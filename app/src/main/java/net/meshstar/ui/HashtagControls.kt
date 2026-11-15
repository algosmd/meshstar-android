package net.meshstar.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import net.meshstar.meshcore.*

@Composable fun HashtagControls(node:NodeState,name:String,change:(String)->Unit,action:suspend(UiAction)->Boolean,opened:(String)->Unit) {
    val scope=rememberCoroutineScope()
    var slots by remember { mutableStateOf(false) }
    var replacement by remember(name) { mutableStateOf<Channel?>(null) }
    val valid=runCatching{Hashtag.name(name)}.isSuccess
    Text(tr(T.channel_open))
    CompactField(name, {change(it);replacement=null}, tr(T.channel_name),tag="m10.channel.name")
    Secondary(tr(T.channel_public),size=10)
    MeshButton(tr(T.channel_open),enabled=node.companion&&valid){scope.launch{if(action(UiAction.OpenHashtag(name)))opened(Hashtag.name(name))}}
    MeshButton(tr(T.channel_slots)){slots=!slots}
    if(slots)MeshCard {
        node.channels.forEach { channel ->
            MeshButton("${channel.index} · "+if(channel.empty)tr(T.channel_free) else channel.displayName,enabled=!channel.protected&&valid){
                if(channel.empty)scope.launch{if(action(UiAction.OpenHashtag(name,channel.index)))opened(Hashtag.name(name))}else replacement=channel
            }
        }
    }
    replacement?.let { channel -> MeshCard {
        Secondary(tr(T.channel_replace)+" ${channel.index} · ${channel.displayName}",size=12)
        MeshButton(tr(T.channel_confirm)){scope.launch{if(action(UiAction.OpenHashtag(name,channel.index,channel.fingerprint))){replacement=null;opened(Hashtag.name(name))}}}
        MeshButton(tr(T.back)){replacement=null}
    } }
    node.channels.filterNot{it.transport||it.name.isBlank()}.forEach { channel->MeshButton(channel.displayName){opened(channel.name)} }
}
