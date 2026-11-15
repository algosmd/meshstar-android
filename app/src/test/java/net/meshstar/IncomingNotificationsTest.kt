package net.meshstar

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import net.meshstar.data.*
import net.meshstar.notifications.IncomingNotifications
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.Shadows.shadowOf

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28],application=Application::class,manifest=org.robolectric.annotation.Config.NONE)
class IncomingNotificationsTest {
    @Test fun allRnsRoutesAndNativeChannelsNotifyOnceAndClearWhenRead() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val manager=context.getSystemService(NotificationManager::class.java)
        val shadow=shadowOf(manager);manager.cancelAll()
        val notifier=IncomingNotifications(context,"Notifications","New message");val state=SatState()
        notifier.update(state,null)
        listOf(Route.SATELLITE,Route.INTERNET,Route.LORA).forEachIndexed { index,route->
            state.messages.add(ChatMessage("hash$index","synthetic$index",false,"private text","",route,"synthetic",100))
            notifier.update(state,null)
        }
        assertEquals(3,shadow.size())
        notifier.update(state,null);assertEquals(3,shadow.size())
        assertTrue(shadow.allNotifications.all { !it.extras.getCharSequence("android.text").toString().contains("private text") })
        state.channelTexts.add(ChannelText("channel-id","Public","private text",10,100))
        notifier.update(state,null);assertEquals(4,shadow.size())
        repeat(3){state.readThrough["synthetic$it"]=100}
        state.readThrough["channel:public"]=100
        notifier.update(state,null);assertEquals(0,shadow.size())
    }
    @Test fun startupHistoryIsNotReannounced() {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val manager=context.getSystemService(NotificationManager::class.java);manager.cancelAll()
        val s=SatState(messages=mutableListOf(ChatMessage("old","synthetic",false,"old","",Route.LORA,"synthetic",10)))
        IncomingNotifications(context,"Notifications","New message").update(s,null)
        assertEquals(0,shadowOf(manager).size())
    }
}
