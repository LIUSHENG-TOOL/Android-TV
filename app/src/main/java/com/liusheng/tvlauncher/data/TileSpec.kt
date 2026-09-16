package com.liusheng.tvlauncher.data

import android.content.Intent
import android.provider.Settings
import com.liusheng.tvlauncher.R

/** 卡片的默认应用，候选包名按启动优先级排列。 */
data class TileSpec(
    val id: String,
    val titleRes: Int,
    val logoRes: Int,
    val colorRes: Int,
    val packages: List<String>,
    val storeId: String?
)

object Tiles {

    val NETFLIX = TileSpec(
        id = "netflix",
        titleRes = R.string.tile_netflix,
        logoRes = R.drawable.ic_logo_netflix,
        colorRes = R.color.tile_netflix,
        packages = listOf(
            "com.netflix.ninja",            // Android TV 版
            "com.netflix.mediaclient"       // 手机 / 平板版
        ),
        storeId = "com.netflix.mediaclient"
    )

    val YOUTUBE = TileSpec(
        id = "youtube",
        titleRes = R.string.tile_youtube,
        logoRes = R.drawable.ic_logo_youtube,
        colorRes = R.color.tile_youtube,
        packages = listOf(
            "com.google.android.youtube.tv",  // Android TV 版
            "com.google.android.youtube",     // 手机版
            "com.google.android.apps.youtube.tv",
            "com.google.android.apps.youtube.mango"
        ),
        storeId = "com.google.android.youtube"
    )

    val GOOGLE_PLAY = TileSpec(
        id = "google_play",
        titleRes = R.string.tile_play,
        logoRes = R.drawable.ic_logo_google_play,
        colorRes = R.color.tile_play,
        packages = listOf(
            "com.android.vending",       // Google Play 商店
            "com.google.android.apps.play.tv"
        ),
        storeId = null
    )

    val CHROME = TileSpec(
        id = "chrome",
        titleRes = R.string.tile_chrome,
        logoRes = R.drawable.ic_logo_chrome,
        colorRes = R.color.tile_chrome,
        packages = listOf(
            "com.android.chrome",        // Chrome 稳定版
            "com.chrome.beta",
            "com.android.browser"
        ),
        storeId = "com.android.chrome"
    )

    val ALL = listOf(NETFLIX, YOUTUBE, GOOGLE_PLAY, CHROME)
}

/** 底栏按钮的行为。 */
enum class DockAction {
    MY_APPS,

    CANDIDATE_INTENTS,

    SYSTEM_SETTINGS
}

/** 底栏入口；厂商特有的 Intent 集中保存在这里。 */
data class DockSpec(
    val id: String,
    val titleRes: Int,
    val iconRes: Int,
    val action: DockAction,
    val candidates: List<Intent> = emptyList()
)

object DockItems {

    /** 梯形校正最后退到系统显示设置。 */
    private fun keystoneIntents() = listOf(
        Intent("com.android.settings.KEYSTONE"),
        Intent("com.ktc.settings.action.KEYSTONE"),
        Intent("com.hisense.action.KEYSTONE_CORRECTION"),
        Intent("com.android.settings.action.KEYSTONE"),
        Intent(Settings.ACTION_DISPLAY_SETTINGS)
    )

    /** 投屏入口不可用时打开 Wi-Fi 设置。 */
    private fun miracastIntents() = listOf(
        Intent("android.settings.CAST_SETTINGS"),
        Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
        Intent("com.android.settings.WIFI_DISPLAY_SETTINGS"),
        Intent(Settings.ACTION_WIFI_SETTINGS)
    )

    /** 信号源切换入口。 */
    private fun signalSourceIntents() = listOf(
        Intent("android.settings.TV_INPUT_SETTINGS"),
        Intent("com.android.tv.ACTION_SELECT_INPUT"),
        Intent("android.media.tv.action.VIEW_INPUT"),
        Intent("com.android.tv.settings.TV_INPUT"),
        Intent("android.intent.action.TV_INPUT")
    )

    fun all(): List<DockSpec> = listOf(
        DockSpec("keystone", R.string.dock_keystone, R.drawable.ic_dock_keystone,
            DockAction.CANDIDATE_INTENTS, keystoneIntents()),
        DockSpec("miracast", R.string.dock_miracast, R.drawable.ic_dock_miracast,
            DockAction.CANDIDATE_INTENTS, miracastIntents()),
        DockSpec("signal_source", R.string.dock_signal, R.drawable.ic_dock_signal,
            DockAction.CANDIDATE_INTENTS, signalSourceIntents()),
        DockSpec("my_apps", R.string.dock_myapps, R.drawable.ic_dock_my_apps,
            DockAction.MY_APPS),
        DockSpec("settings", R.string.dock_settings, R.drawable.ic_dock_settings,
            DockAction.SYSTEM_SETTINGS, listOf(Intent(Settings.ACTION_SETTINGS)))
    )
}
