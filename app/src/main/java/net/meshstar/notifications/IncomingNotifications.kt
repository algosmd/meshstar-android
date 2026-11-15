package net.meshstar.notifications

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.bitchat.android.MainActivity
import com.bitchat.android.R
import net.meshstar.data.SatState
import net.meshstar.ui.SatelliteActivity

/** Shares the BLE DM sound channel. Lock-screen text contains no names or message previews. */
class IncomingNotifications(private val context: Context,
    private val channelTitle: String = context.getString(R.string.ms_notifications),
    private val messageTitle: String = context.getString(R.string.ms_notification_new)) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var seen: Set<String>? = null
    private val active = mutableSetOf<String>()
    companion object { const val CHANNEL = "bitchat_dm_notifications" }
    fun update(state: SatState, visiblePeer: String?) {
        val rows = state.messages.filter { !it.outgoing }.map { Triple(it.hash, "rns:"+it.peer, it.at) } +
            state.channelTexts.filter { !it.outgoing }.map {
                Triple(it.id, if(it.channel.startsWith("direct:")) "native:"+it.channel.removePrefix("direct:") else "channel:"+it.channel.lowercase(),it.receivedAt)
            }
        val fresh = rows.filter { it.first !in (seen ?: rows.map { row -> row.first }.toSet()) }
        seen = rows.map { it.first }.toSet()
        fun unread(peer: String) = rows.count { it.second==peer && it.third > (state.readThrough[peer.removePrefix("rns:")] ?: 0) }
        for(peer in active.toList()) if(unread(peer)==0) { manager.cancel("meshstar:"+peer,0);active.remove(peer) }
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        manager.createNotificationChannel(NotificationChannel(CHANNEL,channelTitle,NotificationManager.IMPORTANCE_HIGH).apply{setShowBadge(true)})
        for ((peer, entries) in fresh.groupBy { it.second }) {
            if(unread(peer)==0)continue
            if(peer==visiblePeer && ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))continue
            val intent=Intent(context,MainActivity::class.java).putExtra(SatelliteActivity.EXTRA_PEER,peer)
                .setAction("net.meshstar.OPEN."+peer).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val note=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("MeshStar").setContentText(messageTitle)
                .setContentIntent(pending).setAutoCancel(true).setNumber(unread(peer))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            try { manager.notify("meshstar:"+peer,0,note);active.add(peer) } catch(_:SecurityException) { }
        }
    }
    fun clear() { active.forEach { manager.cancel("meshstar:"+it,0) }; active.clear(); seen=null }
}
