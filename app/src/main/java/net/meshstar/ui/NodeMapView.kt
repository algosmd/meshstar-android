package net.meshstar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import net.meshstar.meshcore.*
import net.meshstar.rnslite.hex
import kotlin.math.*

@Composable fun NodeMapView(node:NodeState,networking:Boolean,open:(NodeContact)->Unit){
    val context=LocalContext.current;val density=LocalDensity.current.density;val lifecycle=LocalLifecycleOwner.current.lifecycle
    val tileSize=256*density
    val entries=remember(node.contacts,node.advertisements,node.settings?.key?.toList()){NodeMap.entries(node.contacts,node.advertisements,node.settings?.key)}
    var now by remember{mutableLongStateOf(System.currentTimeMillis()/1000)}
    var types by remember{mutableStateOf(setOf(0,1,2,3,4))};var age by remember{mutableStateOf<Long?>(null)}
    var filters by remember{mutableStateOf(false)};var list by remember{mutableStateOf(false)}
    var viewport by remember{mutableStateOf(MapViewport())};var size by remember{mutableStateOf(IntSize.Zero)}
    var fitted by remember{mutableStateOf(false)};var phone by remember{mutableStateOf<GeoPoint?>(null)}
    var active by remember{mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    val currentOpen by rememberUpdatedState(open)
    DisposableEffect(lifecycle){
        val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START->active=true;Lifecycle.Event.ON_STOP->{active=false;if(phone!=null){viewport=MapViewport();fitted=false};phone=null};else->Unit}}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);phone=null}
    }
    LaunchedEffect(active){while(active){now=System.currentTimeMillis()/1000;delay(60000)}}
    val shown=remember(entries,types,age,now){NodeMap.filter(entries,types,age,now)}
    val own=node.settings?.let{GeoPoint(it.latitude,it.longitude)}?.takeIf{it.valid&&(it.latitude!=0.0||it.longitude!=0.0)}
    fun fit(){viewport=NodeMap.fit(shown.map{it.point}+listOfNotNull(own,phone),size.width,size.height,tileSize);fitted=true}
    LaunchedEffect(entries,size,fitted,active){if(active&&!fitted&&size.width>0&&size.height>0)fit()}
    val tiles=remember(viewport,size,tileSize){NodeMap.tiles(viewport,size.width,size.height,tileSize)}
    val images=remember{mutableStateMapOf<TileKey,ImageBitmap>()}
    var failed by remember{mutableStateOf(false)}
    val source=remember(networking){if(networking)net.meshstar.map.OsmTiles(context.applicationContext)else null}
    DisposableEffect(source){onDispose{source?.close()}}
    LaunchedEffect(tiles.map{it.key}.distinct(),active,source){
        if(!active||source==null)return@LaunchedEffect
        delay(120) // Only fetch the settled visible viewport, never prefetch regions.
        val keys=tiles.map{it.key}.toSet();images.keys.toList().filterNot{it in keys}.forEach{images.remove(it)}
        failed=false
        coroutineScope{keys.filterNot{it in images}.forEach{key->launch{
            try{source.load(key)?.let{images[key]=it.asImageBitmap()}?:run{failed=true}}
            catch(e:CancellationException){throw e}catch(_:Exception){failed=true}
        }}}
    }
    Column(Modifier.fillMaxSize()){
        Row(Modifier.horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically){
            MeshButton(tr(T.map_filters)){filters=!filters};MeshButton(tr(T.map_fit)){fit()}
            PhoneCoordinates(requestPermission=true,label=T.map_locate,clear={phone=null}){lat,lon->phone=GeoPoint(lat,lon);viewport=MapViewport(phone!!,15);fitted=true}
        }
        if(filters){
            Row(Modifier.horizontalScroll(rememberScrollState())){listOf(1 to T.map_companion,2 to T.map_repeater,3 to T.map_room,4 to T.map_sensor,0 to T.map_unknown).forEach{(kind,title)->MeshButton(tr(title)+if(kind in types)" ✓"else ""){types=if(kind in types)types-kind else types+kind}}}
            Row(Modifier.horizontalScroll(rememberScrollState())){listOf(null to T.map_all,3600L to T.map_hour,86400L to T.map_day,604800L to T.map_week).forEach{(seconds,title)->MeshButton(tr(title)+if(age==seconds)" ✓"else ""){age=seconds}}}
            Secondary(tr(T.map_stale),size=10)
        }
        if(shown.isEmpty())Secondary(tr(T.map_empty),size=10)
        if(failed)Secondary(tr(T.map_unavailable),size=10)
        val description=tr(T.map_title)
        val background=MaterialTheme.colorScheme.surfaceVariant
        Box(Modifier.weight(1f).fillMaxWidth()){
            val currentShown by rememberUpdatedState(shown)
            Canvas(Modifier.fillMaxSize().clipToBounds().background(background).onSizeChanged{size=it}.semantics{contentDescription=description}
                .pointerInput(Unit){var zoom=1f;detectTransformGestures{_,pan,scale,_->
                    viewport=NodeMap.pan(viewport,pan.x,pan.y,tileSize);zoom*=scale
                    if(zoom>1.4f){viewport=viewport.copy(zoom=(viewport.zoom+1).coerceAtMost(19));zoom=1f}
                    else if(zoom<0.7f){viewport=viewport.copy(zoom=(viewport.zoom-1).coerceAtLeast(1));zoom=1f};fitted=true
                }}.pointerInput(Unit){detectTapGestures{position->
                    currentShown.minByOrNull{entry->val p=NodeMap.screen(entry.point,viewport,size.width,size.height,tileSize);hypot(p.first-position.x,p.second-position.y)}?.let{entry->
                        val p=NodeMap.screen(entry.point,viewport,size.width,size.height,tileSize)
                        if(hypot(p.first-position.x,p.second-position.y)<=24*density)currentOpen(entry.contact)
                    }
                }}){
                tiles.forEach{tile->images[tile.key]?.let{drawImage(it,dstOffset=IntOffset(tile.left.roundToInt(),tile.top.roundToInt()),dstSize=IntSize(ceil(tileSize).toInt(),ceil(tileSize).toInt()))}}
                // The test renderer is deliberately offline; grid is fixture-only.
                if(!networking){tiles.forEach{drawRect(Color.Gray.copy(alpha=.15f),Offset(it.left,it.top),Size(tileSize,tileSize),style=Stroke(1f))}}
                shown.forEach{entry->
                    val p=NodeMap.screen(entry.point,viewport,size.width,size.height,tileSize);val x=p.first;val y=p.second
                    if(x !in -20f..size.width+20f||y !in -20f..size.height+20f)return@forEach
                    val color=(if(entry.stale(now))Color.Gray else when(entry.contact.type){1->Color(0xff008800);2->Color(0xff007aff);3->Color(0xffa050d0);4->Color(0xffd08000);else->Color.Gray}).copy(alpha=entry.opacity(now))
                    val radius=8*density
                    drawCircle(Color.White, radius+2*density,Offset(x,y))
                    when(entry.contact.type){
                        2->drawPath(Path().apply{moveTo(x,y-radius);lineTo(x-radius,y+radius);lineTo(x+radius,y+radius);close()},color)
                        3->drawRect(color,Offset(x-radius,y-radius),Size(radius*2,radius*2))
                        4->drawPath(Path().apply{moveTo(x,y-radius);lineTo(x+radius,y);lineTo(x,y+radius);lineTo(x-radius,y);close()},color)
                        else->drawCircle(color,radius,Offset(x,y))
                    }
                }
                listOfNotNull(own,phone).forEach{point->val p=NodeMap.screen(point,viewport,size.width,size.height,tileSize);drawCircle(Color.White,9*density,Offset(p.first,p.second));drawCircle(Color(0xff007aff),6*density,Offset(p.first,p.second));drawCircle(Color.White,2*density,Offset(p.first,p.second))}
            }
            Column(Modifier.align(Alignment.TopEnd)){
                MeshButton("+",enabled=viewport.zoom<19){viewport=viewport.copy(zoom=viewport.zoom+1)}
                MeshButton("−",enabled=viewport.zoom>1){viewport=viewport.copy(zoom=viewport.zoom-1)}
            }
            val uri=LocalUriHandler.current
            Text("© OpenStreetMap contributors",Modifier.align(Alignment.BottomStart).background(MaterialTheme.colorScheme.background.copy(alpha=.95f)).clickable{uri.openUri("https://www.openstreetmap.org/copyright")}.padding(4.dp),fontSize=10.sp)
        }
        MeshButton(tr(T.map_nodes)+" · ${shown.size}"){list=!list}
        if(list)androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max=180.dp)){
            items(shown.size){index->val entry=shown[index];MeshButton(entry.contact.name){open(entry.contact)}}
        }
    }
}
