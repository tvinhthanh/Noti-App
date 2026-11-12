package expo.modules.notiwebhook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Turns one posted notification into zero or more webhook events, each new and with its own id.
 *
 * - Messaging notifications re-post the whole conversation each time. Every message gets a stable
 *   `messageId` (sha1 of app|sender|time|text); unseen messages become ONE event EACH, already-sent
 *   ones are dropped. Seen ids are persisted, so a restart doesn't resend history.
 * - Other notifications: one event, `eventId` = sha1 of app + text fields; the same id within
 *   CONTENT_TTL_MS is dropped (progress ticks, silent updates, group summaries repeating a child).
 *
 * Called from WebhookSender's worker pool; synchronized so concurrent posts can't both claim an id.
 */
object Dedupe {
  private const val PREFS = "noti_webhook_seen"
  private const val KEY_MSGS = "msgs"
  private const val KEY_CONTENT = "content"
  private const val MAX_MSGS = 3000
  private const val CONTENT_TTL_MS = 5 * 60_000L

  @Synchronized
  fun split(ctx: Context, p: JSONObject): List<JSONObject> {
    val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val pkg = p.optString("packageName")
    val msgs = p.optJSONArray("messages") ?: JSONArray()
    p.remove("messages")

    if (msgs.length() > 0) {
      val seen = LinkedHashSet<String>(readList(prefs.getString(KEY_MSGS, null)))
      val base = p.toString()
      val out = ArrayList<JSONObject>()
      for (i in 0 until msgs.length()) {
        val m = msgs.optJSONObject(i) ?: continue
        val sender = m.opt("sender").takeIf { it != JSONObject.NULL }?.toString() ?: ""
        val time = m.optLong("time")
        val text = m.optString("text")
        val id = sha1("$pkg|$sender|$time|$text")
        if (!seen.add(id)) continue
        out.add(JSONObject(base)
            .put("eventId", id)
            .put("messageId", id)
            .put("isMessage", true)
            .put("sender", sender)
            .put("messageTime", time)
            .put("text", text)
            .put("historyCount", msgs.length()))
      }
      if (out.isNotEmpty()) {
        prefs.edit().putString(KEY_MSGS, JSONArray(seen.toList().takeLast(MAX_MSGS)).toString()).apply()
      }
      return out
    }

    val contentId = sha1(listOf(
        pkg,
        p.optString("title"),
        p.optString("text"),
        p.optString("bigText"),
        p.optString("subText"),
        p.optString("conversationTitle"),
        p.optJSONArray("textLines")?.toString() ?: "",
    ).joinToString("\u0001"))

    val now = System.currentTimeMillis()
    val recent = try { JSONObject(prefs.getString(KEY_CONTENT, "{}")!!) } catch (e: Exception) { JSONObject() }
    val pruned = JSONObject()
    recent.keys().forEach { k ->
      val t = recent.optLong(k)
      if (now - t < CONTENT_TTL_MS) pruned.put(k, t)
    }
    if (pruned.has(contentId)) return emptyList()
    pruned.put(contentId, now)
    prefs.edit().putString(KEY_CONTENT, pruned.toString()).apply()

    return listOf(p
        .put("eventId", contentId)
        .put("messageId", JSONObject.NULL)
        .put("isMessage", false)
        .put("sender", JSONObject.NULL)
        .put("messageTime", JSONObject.NULL)
        .put("historyCount", 0))
  }

  private fun readList(json: String?): List<String> = try {
    val a = JSONArray(json ?: "[]")
    List(a.length()) { a.getString(it) }
  } catch (e: Exception) {
    emptyList()
  }

  private fun sha1(s: String): String =
      MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
          .joinToString("") { "%02x".format(it) }
}
