package net.meshstar.ui

import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import net.meshstar.meshcore.*
import net.meshstar.rnslite.hex
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable fun PathControls(key:String,state:PathState,known:List<NodeContact>,snr:Double?,action:suspend(UiAction)->Boolean){
    val scope=rememberCoroutineScope()
    fun act(a:UiAction){scope.launch{action(a)}}
    LaunchedEffect(key){if(key.isNotEmpty())action(UiAction.PathOpen(key))}
    var text by remember(key){mutableStateOf("")}
    var width by remember(key){mutableIntStateOf(1)}
    var picking by remember{mutableStateOf(false)}
    var query by remember{mutableStateOf("")}
    var confirm by remember{mutableStateOf(false)}
    LaunchedEffect(state.contact?.path?.toList(),state.contact?.pathLength){state.contact?.let{runCatching{ContactPath.from(it)}.getOrNull()?.let{p->width=p.width;text=p.text}}}
    Text(tr(T.path_title))
    state.error?.let{Secondary(tr(when(it){NodeError.Code.RADIO_TIMEOUT->T.admin_timeout;NodeError.Code.PATH_TRACE->T.path_trace_error;NodeError.Code.AMBIGUOUS->T.admin_ambiguous;else->T.path_error}),size=12)}
    if(state.busy)Secondary(tr(T.admin_waiting),size=10)
    state.contact?.let{contact->
        Secondary(tr(T.path_advert)+" · "+if(contact.advertisedAt>0)DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(contact.advertisedAt*1000))else "—",size=10)
        if(snr!=null)Secondary("SNR $snr dB",size=10)
        Secondary(tr(T.path_rssi),size=10)
        if(contact.pathLength==255)Text("flood",fontSize=12.sp)
        else runCatching{ContactPath.from(contact)}.getOrNull()?.let{path->
            if(path.hashes.isEmpty())Secondary(tr(T.path_direct),size=12)
            path.hashes.forEachIndexed{i,hash->Text("${i+1}. "+(ContactPath.name(hash,known)?.let{"$it · "}?:"")+hash.hex(),fontSize=12.sp)}
        }
    }
    MeshButton(tr(T.radio_refresh),enabled=!state.busy){act(UiAction.PathRefresh)}
    MeshButton(tr(T.path_discover),enabled=!state.busy){act(UiAction.PathDiscover)}
    MeshButton(tr(T.path_trace),enabled=!state.busy){act(UiAction.PathTrace)}
    MeshCard{
        CompactField(text,{text=it},tr(T.path_manual),tag="m10.path.input")
        Text(tr(T.path_width),fontSize=12.sp)
        androidx.compose.foundation.layout.Row{(1..3).forEach{n->MeshButton("$n"+if(width==n)" ✓"else ""){width=n}}}
        MeshButton(tr(T.path_pick)){picking=!picking}
        if(picking){
            CompactField(query,{query=it},tr(T.search))
            known.filter{it.type==2&&(query.isBlank()||it.name.contains(query,true))}.take(30).forEach{c->MeshButton(c.name){text=listOf(text,c.key.copyOf(width).hex()).filter{it.isNotBlank()}.joinToString(":");picking=false}}
        }
        MeshButton(tr(T.save),enabled=!state.busy&&runCatching{ContactPath.parse(text,width)}.isSuccess){act(UiAction.PathSet(text,width))}
        MeshButton(tr(T.reset),enabled=!state.busy){confirm=!confirm}
        if(confirm){Secondary(tr(T.path_reset_confirm),size=10);MeshButton(tr(T.channel_confirm)){act(UiAction.PathSet(null,width));confirm=false}}
    }
    state.trace?.let{trace->MeshCard{
        Text(tr(T.path_trace))
        trace.hashes.forEachIndexed{i,hash->Text("${i+1}. "+(ContactPath.name(hash,known)?.let{"$it · "}?:"")+hash.hex()+" · ${trace.snrs[i]} dB",fontSize=12.sp)}
        Secondary(tr(T.path_final)+" ${trace.finalSnr} dB",size=12)
    }}
}
