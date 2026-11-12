package expo.modules.notiwebhook

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** POSTs JSON to the configured webhook, with retry. */
object WebhookSender {
  private const val TAG = "NotiWebhook"
  private const val MAX_ATTEMPTS = 3
  private val executor = Executors.newSingleThreadExecutor()

  /** Fire-and-forget, for the notification listener. */
  fun send(ctx: Context, payload: JSONObject) {
    val app = ctx.applicationContext
    executor.execute { deliver(app, payload) }
  }

  /** Builds payloads on the sender thread (keeps image encoding off the main thread); one POST each. */
  fun sendBuilt(ctx: Context, build: () -> List<JSONObject>) {
    val app = ctx.applicationContext
    executor.execute { build().forEach { deliver(app, it) } }
  }

  /** Blocking send; returns (ok, result). Call off the main thread. */
  fun deliver(ctx: Context, payload: JSONObject): Pair<Boolean, String> {
    val url = Store.url(ctx)
    if (url.isBlank()) return false to "Chưa cấu hình webhook URL"

    var ok = false
    var result = ""
    for (attempt in 1..MAX_ATTEMPTS) {
      try {
        val code = post(url, payload.toString())
        result = "HTTP $code"
        ok = code in 200..299
        // 4xx (except 408/429) won't succeed on retry.
        if (ok || (code in 400..499 && code != 408 && code != 429)) break
      } catch (e: Exception) {
        result = e.javaClass.simpleName + ": " + (e.message ?: "")
        Log.w(TAG, "Attempt $attempt failed: $result")
      }
      if (attempt < MAX_ATTEMPTS) Thread.sleep(1000L * attempt * attempt)
    }
    Store.addLog(ctx, JSONObject()
        .put("time", System.currentTimeMillis())
        .put("app", payload.optString("appName", payload.optString("packageName")))
        .put("title", payload.optString("title"))
        .put("text", payload.optString("text"))
        .put("ok", ok)
        .put("result", result))
    return ok to result
  }

  private fun post(url: String, body: String): Int {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
      conn.requestMethod = "POST"
      conn.connectTimeout = 10_000
      conn.readTimeout = 15_000
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
      conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
      val code = conn.responseCode
      (if (code >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes() }
      return code
    } finally {
      conn.disconnect()
    }
  }
}
