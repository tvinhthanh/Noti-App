package expo.modules.notiwebhook

import android.content.Context
import android.os.Build

/** This app's installed version, read once from the package manager. */
object AppInfo {
  @Volatile private var cached: Pair<String, Long>? = null

  private fun load(ctx: Context): Pair<String, Long> = cached ?: run {
    val info = try {
      ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    } catch (e: Exception) {
      null
    }
    val name = info?.versionName ?: "?"
    val code = when {
      info == null -> 0L
      Build.VERSION.SDK_INT >= 28 -> info.longVersionCode
      else -> @Suppress("DEPRECATION") info.versionCode.toLong()
    }
    (name to code).also { cached = it }
  }

  fun versionName(ctx: Context): String = load(ctx).first
  fun versionCode(ctx: Context): Long = load(ctx).second
}
