package expo.modules.notiwebhook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject

class NotiWebhookModule : Module() {
  private val ctx: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  override fun definition() = ModuleDefinition {
    Name("NotiWebhook")

    AsyncFunction("getStatus") {
      val c = ctx
      val granted = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
      if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        // Ask the system to (re)bind in case the listener was killed.
        NotificationListenerService.requestRebind(ComponentName(c, NotiListener::class.java))
      }
      val pm = c.getSystemService(PowerManager::class.java)
      mapOf(
          "url" to Store.url(c),
          "enabled" to Store.enabled(c),
          "permissionGranted" to granted,
          "batteryUnrestricted" to (pm?.isIgnoringBatteryOptimizations(c.packageName) ?: true),
      )
    }

    AsyncFunction("saveConfig") { url: String, enabled: Boolean ->
      Store.save(ctx, url, enabled)
      true
    }

    Function("openPermissionSettings") {
      ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Function("requestIgnoreBatteryOptimization") {
      val c = ctx
      val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
          Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      try {
        c.startActivity(direct)
      } catch (e: Exception) {
        c.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
    }

    // AsyncFunction runs on a background queue, so blocking network I/O is fine here.
    AsyncFunction("sendTest") {
      val payload = JSONObject()
          .put("packageName", ctx.packageName)
          .put("appName", "Noti Webhook (test)")
          .put("title", "Test webhook")
          .put("text", "Nếu thấy cái này là webhook chạy OK")
          .put("bigText", "")
          .put("subText", "")
          .put("category", "test")
          .put("postTime", System.currentTimeMillis())
          .put("key", "test")
          .put("isOngoing", false)
          .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
      val (ok, result) = WebhookSender.deliver(ctx, payload)
      mapOf("ok" to ok, "result" to result)
    }

    AsyncFunction("getLogs") {
      Store.logs(ctx).toString()
    }

    Function("clearLogs") {
      Store.clearLogs(ctx)
    }
  }
}
