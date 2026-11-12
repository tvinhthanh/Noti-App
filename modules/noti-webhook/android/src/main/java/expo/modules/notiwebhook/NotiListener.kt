package expo.modules.notiwebhook

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/** The system calls this for every notification posted on the device; each one goes to the webhook. */
class NotiListener : NotificationListenerService() {

  // Only drops byte-identical re-posts within 2s (some apps post the same thing twice in a row).
  private val recent = LinkedHashMap<String, Long>()

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
      val payload = try {
        NotiSerializer.build(ctx, sbn, ranking)
      } catch (e: Throwable) {
        Log.e("NotiWebhook", "Serialize failed", e)
        null
      } ?: return@sendBuilt null
      if (isDuplicate(signature(payload.toString()))) null else payload
    }
  }

  private fun signature(json: String): String =
      // sentAt changes on every call; strip it so identical content compares equal.
      json.replace(Regex("\"sentAt\":\\d+,?"), "")

  @Synchronized
  private fun isDuplicate(sig: String): Boolean {
    val now = System.currentTimeMillis()
    recent.entries.removeAll { now - it.value > 2_000 }
    if (recent.containsKey(sig)) return true
    recent[sig] = now
    return false
  }
}
