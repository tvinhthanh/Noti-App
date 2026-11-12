package expo.modules.notiwebhook

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/** After reboot or an app update, make sure the notification listener is bound again. */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent) {
    val granted = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    if (!granted) return
    try {
      NotificationListenerService.requestRebind(ComponentName(ctx, NotiListener::class.java))
    } catch (e: Exception) {
      // Not fatal: the system binds enabled listeners on boot by itself.
    }
  }
}
