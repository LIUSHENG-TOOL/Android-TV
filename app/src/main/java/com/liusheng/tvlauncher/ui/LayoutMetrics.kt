package com.liusheng.tvlauncher.ui

import android.content.Context

/** 按屏幕宽高计算卡片、底栏和文字尺寸。 */
class LayoutMetrics private constructor(
    val tileWidth: Int,
    val tileHeight: Int,
    val tileLogoSize: Int,
    val tileLogoTop: Int,
    val tileTitlePx: Float,
    val tileTitleBottom: Int,
    val tileCorner: Float,
    val tileGap: Int,

    val dockWidth: Int,
    val dockHeight: Int,
    val dockIconSize: Int,
    val dockLabelPx: Float,
    val dockPadTop: Int,
    val dockLabelTop: Int,
    val dockGap: Int,

    val statusTextPx: Float,
    val statusBarHeight: Int,
    val rootPadTop: Int,
    val rootPadBottom: Int,
    val horizontalPadding: Int
) {

    companion object {

        /** 卡片宽高比。 */
        private const val TILE_ASPECT = 300f / 230f

        /** 卡片连同倒影占用的高度系数。 */
        private const val TILE_BLOCK = 1f + ReflectionLayout.DEFAULT_RATIO

        /** 底栏按钮宽高比。 */
        private const val DOCK_ASPECT = 104f / 150f

        fun of(context: Context): LayoutMetrics {
            val dm = context.resources.displayMetrics
            val w = dm.widthPixels.toFloat()
            val h = dm.heightPixels.toFloat()
            val density = dm.density

            val hPad = w * 0.03f
            val gap = w * 0.014f
            val statusH = h * 0.09f
            val bottomPad = h * 0.058f
            val vGap = h * 0.028f

            // 底栏五个按钮等分可用宽度。
            val dockWidth = (w - 2 * hPad - 4 * gap) / 5f
            val dockHeight = dockWidth * DOCK_ASPECT

            // 卡片高度还要给状态栏、倒影和底栏留空间。
            val maxTileHeightByWidth = (w - 2 * hPad - 3 * gap) / 4f * TILE_ASPECT
            val maxTileHeightByHeight =
                (h - statusH - vGap - bottomPad - dockHeight) / TILE_BLOCK

            val tileHeight = minOf(maxTileHeightByWidth, maxTileHeightByHeight)
            val tileWidth = tileHeight / TILE_ASPECT

            val labelPx = dockWidth * 0.105f / density
            // setTextSize 使用 sp，这里先换算为对应数值。
            return LayoutMetrics(
                tileWidth = tileWidth.toInt(),
                tileHeight = tileHeight.toInt(),
                tileLogoSize = (tileWidth * 0.80f).toInt(),
                tileLogoTop = (tileHeight * 0.07f).toInt(),
                tileTitlePx = tileWidth * 0.135f / density,
                tileTitleBottom = (tileHeight * 0.087f).toInt(),
                tileCorner = tileWidth * 0.078f,
                tileGap = gap.toInt(),

                dockWidth = dockWidth.toInt(),
                dockHeight = dockHeight.toInt(),
                dockIconSize = (dockHeight * 0.385f).toInt(),
                dockLabelPx = labelPx,
                dockPadTop = (dockHeight * 0.155f).toInt(),
                dockLabelTop = (dockHeight * 0.077f).toInt(),
                dockGap = gap.toInt(),

                statusTextPx = h * 0.030f / density,
                statusBarHeight = statusH.toInt(),
                rootPadTop = (h * 0.017f).toInt(),
                rootPadBottom = (h * 0.028f).toInt(),
                horizontalPadding = hPad.toInt()
            )
        }
    }
}
