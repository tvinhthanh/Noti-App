package expo.modules.notiwebhook

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/** The system calls this for every notification posted on the device; new content goes to the webhook. */
class NotiListener : NotificationListenerService() {

  companion object {
    /** True while the system has this listener bound in the current process. */
    @Volatile var connected = false
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    connected = true
  }

  // The system can unbind us (memory pressure, OEM task killers); ask to be bound again.
  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    connected = false
    try {
      requestRebind(ComponentName(this, NotiListener::class.java))
    } catch (e: Exception) {
      Log.w("NotiWebhook", "requestRebind failed", e)
    }
  }

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    if (!Store.enabled(this)) return
    if (sbn.packageName == packageName) return

    // Ranking is a snapshot tied to this moment; read it now, serialize off the main thread.
    val ranking = try {
      Ranking().takeIf { currentRanking?.getRanking(sbn.key, it) == true }
    } catch (e: Exception) {
      null
    }
    val ctx = applicationContext

    WebhookSender.sendBuilt(ctx) {
      try {
        Dedupe.split(ctx, NotiSerializer.build(ctx, sbn, ranking))
      } catch (e: Throwable) {
        Log.e("NotiWebhook", "Serialize failed", e)
        emptyList()
      }
    }
  }
}
