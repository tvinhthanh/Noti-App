package expo.modules.notiwebhook

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

  @Volatile private var lastRebindAttempt = 0L

  /**
   * After a force-stop (swipe-kill on Xiaomi) the permission stays granted but the system never
   * binds the listener again, and requestRebind alone is ignored. Toggling our own component off/on
   * makes the notification manager re-scan and bind it. Runs on the AsyncFunction background queue.
   */
  private fun ensureListenerBound(c: Context) {
    // On a cold start the system binds the listener a moment after the UI process comes up.
    repeat(15) {
      if (NotiListener.connected) return
      Thread.sleep(100)
    }
    // The UI polls status every few seconds; don't churn the component on every poll.
    val now = System.currentTimeMillis()
    if (now - lastRebindAttempt < 30_000) return
    lastRebindAttempt = now

    val cn = ComponentName(c, NotiListener::class.java)
    try {
      val pm = c.packageManager
      pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
          PackageManager.DONT_KILL_APP)
      pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
          PackageManager.DONT_KILL_APP)
    } catch (e: Exception) {
      // Fall through to requestRebind.
    }
    try {
      NotificationListenerService.requestRebind(cn)
    } catch (e: Exception) {
    }
    repeat(30) {
      if (NotiListener.connected) return
      Thread.sleep(100)
    }
  }

  override fun definition() = ModuleDefinition {
    Name("NotiWebhook")

    AsyncFunction("getStatus") {
      val c = ctx
      val granted = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
      if (granted) ensureListenerBound(c)
      val pm = c.getSystemService(PowerManager::class.java)
      mapOf(
          "url" to Store.url(c),
          "enabled" to Store.enabled(c),
          "permissionGranted" to granted,
          "batteryUnrestricted" to (pm?.isIgnoringBatteryOptimizations(c.packageName) ?: true),
          "isXiaomi" to listOf("xiaomi", "redmi", "poco").any {
            Build.MANUFACTURER.equals(it, true) || Build.BRAND.equals(it, true)
          },
          "listenerConnected" to NotiListener.connected,
          "autostartAck" to Store.autostartAck(c),
          "appVersion" to AppInfo.versionName(c),
          "appVersionCode" to AppInfo.versionCode(c),
      )
    }

    // The user's own confirmation that Autostart is on, since the OS gives no way to read it.
    Function("setAutostartAck") { value: Boolean ->
      Store.setAutostartAck(ctx, value)
    }

    // Xiaomi/HyperOS force-stops swiped-away apps unless Autostart is on; there is no API to
    // read that toggle, so we just open the screen. Falls back to the app's system info page.
    Function("openAutostartSettings") {
      val c = ctx
      val miui = Intent().setComponent(ComponentName("com.miui.securitycenter",
          "com.miui.permcenter.autostart.AutoStartManagementActivity"))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      try {
        c.startActivity(miui)
      } catch (e: Exception) {
        c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
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
          .put("appVersion", AppInfo.versionName(ctx))
          .put("appVersionCode", AppInfo.versionCode(ctx))
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
