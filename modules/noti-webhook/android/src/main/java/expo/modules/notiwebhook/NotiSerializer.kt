package expo.modules.notiwebhook

import android.app.Notification
import android.app.Person
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Turns a StatusBarNotification into a JSON object carrying everything Android exposes about it. */
object NotiSerializer {
  private const val MAX_DEPTH = 4

  fun build(
      ctx: Context,
      sbn: StatusBarNotification,
      ranking: NotificationListenerService.Ranking?,
  ): JSONObject {
    val n = sbn.notification
    val ex = n.extras ?: Bundle()
    fun cs(key: String) = ex.getCharSequence(key)?.toString() ?: ""

    return JSONObject().apply {
      put("event", "posted")
      put("sentAt", System.currentTimeMillis())

      // App
      put("packageName", sbn.packageName)
      put("appName", appLabel(ctx, sbn.packageName))

      // Readable content
      put("title", cs(Notification.EXTRA_TITLE))
      put("titleBig", cs(Notification.EXTRA_TITLE_BIG))
      put("text", cs(Notification.EXTRA_TEXT))
      put("bigText", cs(Notification.EXTRA_BIG_TEXT))
      put("subText", cs(Notification.EXTRA_SUB_TEXT))
      put("summaryText", cs(Notification.EXTRA_SUMMARY_TEXT))
      put("infoText", cs(Notification.EXTRA_INFO_TEXT))
      put("tickerText", n.tickerText?.toString() ?: "")
      put("conversationTitle", cs(Notification.EXTRA_CONVERSATION_TITLE))
      put("isGroupConversation",
          Build.VERSION.SDK_INT >= 28 && ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION))
      put("textLines", JSONArray(
          ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it?.toString() ?: "" }
              ?: emptyList<String>()))
      put("messages", messages(ex))
      put("template", ex.getString(Notification.EXTRA_TEMPLATE) ?: "")

      // Identity
      put("id", sbn.id)
      put("tag", sbn.tag ?: JSONObject.NULL)
      put("key", sbn.key)
      put("groupKey", sbn.groupKey ?: "")
      put("group", n.group ?: "")
      put("sortKey", n.sortKey ?: "")
      put("isGroupSummary", n.flags and Notification.FLAG_GROUP_SUMMARY != 0)
      put("channelId", if (Build.VERSION.SDK_INT >= 26) n.channelId ?: "" else "")
      put("category", n.category ?: "")
      put("userId", sbn.user.hashCode())

      // Timing
      put("postTime", sbn.postTime)
      put("when", n.`when`)

      // State / appearance
      put("isOngoing", sbn.isOngoing)
      put("isClearable", sbn.isClearable)
      put("flags", n.flags)
      put("priority", @Suppress("DEPRECATION") n.priority)
      put("visibility", n.visibility)
      put("number", n.number)
      put("color", String.format("#%08X", n.color))
      put("progress", JSONObject()
          .put("value", ex.getInt(Notification.EXTRA_PROGRESS))
          .put("max", ex.getInt(Notification.EXTRA_PROGRESS_MAX))
          .put("indeterminate", ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)))
      put("actions", actions(n))
      put("ranking", ranking(ranking))

      // Images as data URIs (null when absent)
      put("largeIcon", image(ctx, largeIcon(n, ex), 128, Bitmap.CompressFormat.PNG))
      put("picture", image(ctx, picture(ex), 720, Bitmap.CompressFormat.JPEG))

      // Every raw extra the app attached, for anything not mapped above
      put("extras", bundleToJson(ex, 0))

      // Device
      put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
      put("androidVersion", Build.VERSION.RELEASE)
    }
  }

  fun appLabel(ctx: Context, pkg: String): String = try {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
  } catch (e: PackageManager.NameNotFoundException) {
    pkg
  }

  @Suppress("DEPRECATION")
  private fun messages(ex: Bundle): JSONArray {
    val out = JSONArray()
    val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return out
    for (p in arr) {
      val b = p as? Bundle ?: continue
      var sender = b.getCharSequence("sender")?.toString()
      if (sender == null && Build.VERSION.SDK_INT >= 28) {
        sender = (b.getParcelable<Parcelable>("sender_person") as? Person)?.name?.toString()
      }
      out.put(JSONObject()
          .put("text", b.getCharSequence("text")?.toString() ?: "")
          .put("time", b.getLong("time"))
          .put("sender", sender ?: JSONObject.NULL))
    }
    return out
  }

  private fun actions(n: Notification): JSONArray {
    val out = JSONArray()
    n.actions?.forEach { a ->
      out.put(JSONObject()
          .put("title", a.title?.toString() ?: "")
          .put("hasReply", a.remoteInputs?.isNotEmpty() == true))
    }
    return out
  }

  private fun ranking(r: NotificationListenerService.Ranking?): Any {
    r ?: return JSONObject.NULL
    return JSONObject().apply {
      put("rank", r.rank)
      put("isAmbient", r.isAmbient)
      put("matchesInterruptionFilter", r.matchesInterruptionFilter())
      put("importance", r.importance)
      if (Build.VERSION.SDK_INT >= 26) {
        put("channelName", r.channel?.name?.toString() ?: "")
        put("canShowBadge", r.canShowBadge())
      }
      if (Build.VERSION.SDK_INT >= 30) put("isConversation", r.isConversation)
    }
  }

  private fun largeIcon(n: Notification, ex: Bundle): Any? =
      n.getLargeIcon() ?: @Suppress("DEPRECATION") ex.get(Notification.EXTRA_LARGE_ICON)

  @Suppress("DEPRECATION")
  private fun picture(ex: Bundle): Any? {
    ex.get(Notification.EXTRA_PICTURE)?.let { return it }
    if (Build.VERSION.SDK_INT >= 31) return ex.get(Notification.EXTRA_PICTURE_ICON)
    return null
  }

  /** Bitmap or Icon -> "data:image/...;base64,..." scaled to fit maxDim. */
  private fun image(ctx: Context, src: Any?, maxDim: Int, fmt: Bitmap.CompressFormat): Any {
    return try {
      val bmp = when (src) {
        is Bitmap -> src
        is Icon -> src.loadDrawable(ctx)?.let { d ->
          if (d is BitmapDrawable && d.bitmap != null) d.bitmap
          else {
            val w = d.intrinsicWidth.coerceIn(1, maxDim)
            val h = d.intrinsicHeight.coerceIn(1, maxDim)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
              d.setBounds(0, 0, w, h)
              d.draw(Canvas(it))
            }
          }
        }
        else -> null
      } ?: return JSONObject.NULL

      val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height))
      val scaled = if (scale < 1f)
        Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1),
            (bmp.height * scale).toInt().coerceAtLeast(1), true)
      else bmp
      val buf = ByteArrayOutputStream()
      scaled.compress(fmt, if (fmt == Bitmap.CompressFormat.JPEG) 80 else 100, buf)
      val mime = if (fmt == Bitmap.CompressFormat.JPEG) "image/jpeg" else "image/png"
      "data:$mime;base64," + Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)
    } catch (e: Throwable) {
      JSONObject.NULL
    }
  }

  @Suppress("DEPRECATION")
  private fun bundleToJson(b: Bundle, depth: Int): JSONObject {
    val o = JSONObject()
    for (key in b.keySet()) {
      val v = try { b.get(key) } catch (e: Throwable) { "[unreadable]" }
      try {
        o.put(key, toJson(v, depth))
      } catch (e: Throwable) {
        o.put(key, v.toString())
      }
    }
    return o
  }

  private fun toJson(v: Any?, depth: Int): Any = when (v) {
    null -> JSONObject.NULL
    is CharSequence -> v.toString()
    is Boolean, is Int, is Long, is Short, is Byte -> v as Any
    is Float -> if (v.isFinite()) v.toDouble() else v.toString()
    is Double -> if (v.isFinite()) v else v.toString()
    is Bitmap -> "[bitmap ${v.width}x${v.height}]"
    is Icon -> "[icon]"
    is Bundle -> if (depth < MAX_DEPTH) bundleToJson(v, depth + 1) else v.toString()
    is Array<*> -> JSONArray(v.map { toJson(it, depth + 1) })
    is IntArray -> JSONArray(v.toList())
    is LongArray -> JSONArray(v.toList())
    is BooleanArray -> JSONArray(v.toList())
    is FloatArray -> JSONArray(v.map { if (it.isFinite()) it.toDouble() else it.toString() })
    is DoubleArray -> JSONArray(v.map { if (it.isFinite()) it else it.toString() })
    is ByteArray -> "[${v.size} bytes]"
    is Collection<*> -> JSONArray(v.map { toJson(it, depth + 1) })
    else -> personOrString(v)
  }

  private fun personOrString(v: Any): Any {
    if (Build.VERSION.SDK_INT >= 28 && v is Person) {
      return JSONObject()
          .put("name", v.name?.toString() ?: JSONObject.NULL)
          .put("key", v.key ?: JSONObject.NULL)
          .put("uri", v.uri ?: JSONObject.NULL)
          .put("isBot", v.isBot)
          .put("isImportant", v.isImportant)
    }
    return v.toString()
  }
}
