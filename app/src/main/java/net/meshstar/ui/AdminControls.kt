package net.meshstar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.meshstar.meshcore.*

@Composable fun AdminControls(state:AdminState,action:suspend(UiAction)->Boolean,paths:()->Unit){
    val scope=rememberCoroutineScope()
    fun act(value:UiAction){scope.launch{action(value)}}
    var password by remember{mutableStateOf("")}
    var command by remember{mutableStateOf("")}
    var name by remember{mutableStateOf("")}
    var wait by remember{mutableIntStateOf(0)}
    var waitMenu by remember{mutableStateOf(false)}
    var pending by remember{mutableStateOf<String?>(null)}
    var radio by remember{mutableStateOf(RadioPreset.all.first().radio)}
    var radioVisible by remember{mutableStateOf(false)}
    LaunchedEffect(state.contact?.key?.toList()){action(UiAction.AdminPassword{password=it})}
    Text(tr(T.admin_title)+" · "+state.contact?.name.orEmpty())
    MeshButton(tr(T.admin_wait)+" · "+if(wait==0)tr(T.auto)else "$wait s"){waitMenu=!waitMenu}
    if(waitMenu)listOf(0,60,120,300).forEach{seconds->MeshButton(if(seconds==0)tr(T.auto)else "$seconds s"){wait=seconds;waitMenu=false}}
    state.error?.let{Secondary(tr(when(it){NodeError.Code.RADIO_TIMEOUT->T.admin_timeout;NodeError.Code.LOGIN_FAILED->T.admin_failed;NodeError.Code.READ_ONLY->T.admin_read_only;NodeError.Code.AMBIGUOUS->T.admin_ambiguous;else->T.node_failed}),size=12)}
    if(state.error==NodeError.Code.RADIO_TIMEOUT)MeshButton(tr(T.path_title)){paths()}
    if(!state.loggedIn){
        CompactField(password,{password=it},tr(T.admin_password),password=true,tag="m10.admin.password")
        MeshButton(tr(T.admin_login),enabled=!state.busy){act(UiAction.AdminLogin(password,wait))}
    }
    MeshButton(tr(T.admin_forget)){password="";act(UiAction.AdminForget)}
    if(state.busy)Secondary(tr(T.admin_waiting),size=10)
    if(state.loggedIn){
        MeshCard {
            listOf("clock" to T.admin_time,"ver" to T.admin_version,"neighbors" to T.admin_neighbors,"get radio" to T.admin_read_radio,"get name" to T.admin_read_name).forEach{(text,title)->MeshButton(tr(title),enabled=!state.busy){act(UiAction.AdminRun(text,wait))}}
            MeshButton(tr(T.admin_status),enabled=!state.busy){act(UiAction.AdminStatus(wait))}
            MeshButton(tr(T.admin_sync),enabled=!state.busy){pending="clock sync"}
            MeshButton(tr(T.radio_flood),enabled=!state.busy){pending="advert"}
            MeshButton(tr(T.admin_reboot),enabled=!state.busy){pending="reboot"}
        }
        state.status?.let{s->MeshCard{
            Text("${s.batteryMV/1000.0} V · ${s.uptime} s",fontSize=12.sp)
            Secondary("tx ${s.sent} · rx ${s.received}",size=12)
            Secondary("tx ${s.tx} s · rx ${s.rx} s",size=12)
            Secondary("RSSI ${s.rssi} dBm · noise ${s.noise} dBm",size=12)
        }}
        MeshButton(tr(T.radio_settings)){radioVisible=!radioVisible}
        if(radioVisible)MeshCard{RadioParametersEditor(radio){radio=it};MeshButton(tr(T.radio_apply),enabled=radio.valid&&!state.busy){pending=AdminProtocol.radio(radio)}}
        MeshCard{CompactField(name,{name=it},tr(T.nickname));MeshButton(tr(T.save),enabled=name.isNotBlank()&&!state.busy){runCatching{AdminProtocol.name(name)}.getOrNull()?.let{pending=it}}}
        CompactField(command,{command=it},tr(T.admin_command))
        MeshButton(tr(T.send),enabled=command.isNotBlank()&&!state.busy){if(AdminProtocol.confirm(command))pending=command else{act(UiAction.AdminRun(command,wait));command=""}}
    }
    pending?.let{raw->MeshCard{
        Secondary(tr(T.admin_confirm),size=12)
        if(!AdminProtocol.sensitive(raw))Text(raw,fontSize=12.sp)
        MeshButton(tr(T.channel_confirm),enabled=!state.busy){act(UiAction.AdminRun(raw,wait));pending=null;command=""}
        MeshButton(tr(T.back)){pending=null}
    }}
    Secondary(tr(T.admin_console),size=12)
    state.lines.forEach{line->Text(when(line){"[redacted]"->tr(T.admin_redacted);"[reboot submitted]"->tr(T.admin_reboot_sent);else->line},fontSize=12.sp)}
}
