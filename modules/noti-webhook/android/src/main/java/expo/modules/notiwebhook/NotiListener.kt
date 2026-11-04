package expo.modules.notiwebhook

import android.app.Notification
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/** The system calls this for every notification posted on the device. */
class NotiListener : NotificationListenerService() {

  // Apps re-post the same notification on every update; skip exact repeats within 10s.
  private val recent = LinkedHashMap<String, Long>()

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    if (!Store.enabled(this)) return
    if (sbn.packageName == packageName) return
    val n = sbn.notification ?: return
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

    val extras = n.extras
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
    val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
    val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
    if (title.isEmpty() && text.isEmpty() && bigText.isEmpty()) return

    if (isDuplicate("${sbn.key}|$title|$text|$bigText")) return

    val payload = JSONObject()
        .put("packageName", sbn.packageName)
        .put("appName", appLabel(sbn.packageName))
        .put("title", title)
        .put("text", text)
        .put("bigText", bigText)
        .put("subText", subText)
        .put("category", n.category ?: "")
        .put("postTime", sbn.postTime)
        .put("key", sbn.key)
        .put("isOngoing", sbn.isOngoing)
        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")

    WebhookSender.send(this, payload)
  }

  @Synchronized
  private fun isDuplicate(sig: String): Boolean {
    val now = System.currentTimeMillis()
    recent.entries.removeAll { now - it.value > 10_000 }
    if (recent.containsKey(sig)) return true
    recent[sig] = now
    return false
  }

  private fun appLabel(pkg: String): String = try {
    val pm = packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
  } catch (e: PackageManager.NameNotFoundException) {
    pkg
  }
}
