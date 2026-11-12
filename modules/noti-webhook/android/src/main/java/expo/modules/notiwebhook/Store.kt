package expo.modules.notiwebhook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Config + recent send log, persisted in SharedPreferences so the service works without JS. */
object Store {
  private const val PREFS = "noti_webhook"
  private const val KEY_URL = "url"
  private const val KEY_ENABLED = "enabled"
  private const val KEY_LOGS = "logs"
  private const val KEY_AUTOSTART_ACK = "autostart_ack"
  private const val MAX_LOGS = 100

  private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun url(ctx: Context): String = prefs(ctx).getString(KEY_URL, "") ?: ""
  fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, true)

  fun save(ctx: Context, url: String, enabled: Boolean) {
    prefs(ctx).edit().putString(KEY_URL, url.trim()).putBoolean(KEY_ENABLED, enabled).apply()
  }

  /** User confirmed they enabled Xiaomi Autostart (the OS offers no way to read it). */
  fun autostartAck(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTOSTART_ACK, false)

  fun setAutostartAck(ctx: Context, value: Boolean) {
    prefs(ctx).edit().putBoolean(KEY_AUTOSTART_ACK, value).apply()
  }

  @Synchronized
  fun addLog(ctx: Context, entry: JSONObject) {
    val old = logs(ctx)
    val out = JSONArray().put(entry)
    for (i in 0 until minOf(old.length(), MAX_LOGS - 1)) out.put(old.get(i))
    prefs(ctx).edit().putString(KEY_LOGS, out.toString()).apply()
  }

  fun logs(ctx: Context): JSONArray =
      try { JSONArray(prefs(ctx).getString(KEY_LOGS, "[]")) } catch (e: Exception) { JSONArray() }

  @Synchronized
  fun clearLogs(ctx: Context) { prefs(ctx).edit().remove(KEY_LOGS).apply() }
}
