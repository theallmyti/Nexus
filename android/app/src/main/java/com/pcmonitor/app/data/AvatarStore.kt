package com.pcmonitor.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** The signed-in user's picture: a built-in pixel character, an uploaded photo, or none. */
data class Avatar(val value: String? = null, val photoUrl: String? = null) {
    /** "fox" for "pixel:fox", else null. */
    val pixelId: String? get() = value?.takeIf { it.startsWith("pixel:") }?.removePrefix("pixel:")
    val isPhoto: Boolean get() = value == "photo" && photoUrl != null
}

/**
 * Local copy of the avatar so Home and Settings show it instantly, before the profile loads.
 * The account (Convex) is the source of truth; this is refreshed whenever the profile is fetched.
 * The photo itself is cached on disk so it isn't downloaded on every launch.
 */
object AvatarStore {
    private const val PREFS = "avatar"
    private val http = OkHttpClient()
    private val _avatar = MutableStateFlow(Avatar())
    val avatar: StateFlow<Avatar> = _avatar.asStateFlow()
    private var appContext: Context? = null
    private var memory: Pair<String, Bitmap>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _avatar.value = Avatar(p.getString("value", null), p.getString("url", null))
    }

    fun set(avatar: Avatar) {
        _avatar.value = avatar
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("value", avatar.value)?.putString("url", avatar.photoUrl)?.apply()
    }

    /** Signed out: forget the picture and the cached photo. */
    fun clear() {
        set(Avatar())
        memory = null
        appContext?.let { File(it.cacheDir, "avatar.jpg").delete() }
    }

    /** The photo for [url]: memory, then disk, then network. Null if it can't be loaded. */
    suspend fun loadPhoto(url: String): Bitmap? = withContext(Dispatchers.IO) {
        memory?.let { (u, b) -> if (u == url) return@withContext b }
        val ctx = appContext ?: return@withContext null
        val file = File(ctx.cacheDir, "avatar.jpg")
        val keyFile = File(ctx.cacheDir, "avatar.url")
        val bmp = if (file.exists() && keyFile.exists() && keyFile.readText() == url) {
            BitmapFactory.decodeFile(file.path)
        } else runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: return@use null
                file.writeBytes(bytes); keyFile.writeText(url)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
        bmp?.also { memory = url to it }
    }
}
