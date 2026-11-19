package com.lyricsync.app.data.local

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import com.lyricsync.app.domain.LyricDisplayMode
import com.lyricsync.app.domain.OverlayConfig
import com.lyricsync.app.domain.ThemeMode
import com.lyricsync.app.domain.TranslationEngine
import java.util.Locale

class AppPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("lyricsync_prefs", Context.MODE_PRIVATE)

    private val systemDefaultLang: String
        get() = TranslateLanguage.fromLanguageTag(Locale.getDefault().language)
            ?: TranslateLanguage.TURKISH

    var targetLang: String
        get() = prefs.getString(KEY_TARGET_LANG, systemDefaultLang) ?: systemDefaultLang
        set(value) = prefs.edit().putString(KEY_TARGET_LANG, value).apply()

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name)
        }.getOrDefault(ThemeMode.DARK)
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var displayMode: LyricDisplayMode
        get() = runCatching {
            LyricDisplayMode.valueOf(
                prefs.getString(KEY_DISPLAY_MODE, LyricDisplayMode.DUAL.name) ?: LyricDisplayMode.DUAL.name
            )
        }.getOrDefault(LyricDisplayMode.DUAL)
        set(value) = prefs.edit().putString(KEY_DISPLAY_MODE, value.name).apply()

    var wifiOnlyDownload: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var autoHideOverlayOnPause: Boolean
        get() = prefs.getBoolean(KEY_AUTO_HIDE_OVERLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_HIDE_OVERLAY, value).apply()

    var translationEngine: TranslationEngine
        get() = runCatching {
            TranslationEngine.valueOf(
                prefs.getString(KEY_TRANSLATION_ENGINE, TranslationEngine.CLOUD_CONTEXT.name)
                    ?: TranslationEngine.CLOUD_CONTEXT.name
            )
        }.getOrDefault(TranslationEngine.CLOUD_CONTEXT)
        set(value) = prefs.edit().putString(KEY_TRANSLATION_ENGINE, value.name).apply()

    fun getOverlayConfig(): OverlayConfig = OverlayConfig(
        fontSizeSp = prefs.getFloat(KEY_OVERLAY_FONT_SIZE, 15f).coerceIn(12f, 24f),
        opacity = prefs.getFloat(KEY_OVERLAY_OPACITY, 0.90f).coerceIn(0.35f, 1.0f),
        isTouchLocked = false,
        showNextLine = prefs.getBoolean(KEY_OVERLAY_NEXT_LINE, true),
        x = prefs.getInt(KEY_OVERLAY_X, 0),
        y = prefs.getInt(KEY_OVERLAY_Y, 180).coerceAtLeast(0)
    )

    fun saveOverlayConfig(config: OverlayConfig) {
        prefs.edit()
            .putFloat(KEY_OVERLAY_FONT_SIZE, config.fontSizeSp.coerceIn(12f, 24f))
            .putFloat(KEY_OVERLAY_OPACITY, config.opacity.coerceIn(0.35f, 1.0f))
            .putBoolean(KEY_OVERLAY_NEXT_LINE, config.showNextLine)
            .putInt(KEY_OVERLAY_X, config.x)
            .putInt(KEY_OVERLAY_Y, config.y.coerceAtLeast(0))
            .apply()
    }

    private companion object {
        const val KEY_TARGET_LANG = "target_lang"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val KEY_WIFI_ONLY = "wifi_only_download"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_AUTO_HIDE_OVERLAY = "auto_hide_overlay_on_pause"
        const val KEY_TRANSLATION_ENGINE = "translation_engine"
        const val KEY_OVERLAY_FONT_SIZE = "overlay_font_size"
        const val KEY_OVERLAY_OPACITY = "overlay_opacity"
        const val KEY_OVERLAY_NEXT_LINE = "overlay_show_next_line"
        const val KEY_OVERLAY_X = "overlay_x"
        const val KEY_OVERLAY_Y = "overlay_y"
    }
}
