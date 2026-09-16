package com.liusheng.tvlauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

/** 从应用图标提取卡片背景色，并限制明度以保留文字对比度。 */
object ColorUtils {

    private const val SAMPLE = 32

    /** 无法提取颜色时返回 null。 */
    fun dominantColor(icon: Drawable?): Int? {
        if (icon == null) return null

        val bitmap = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            icon.setBounds(0, 0, SAMPLE, SAMPLE)
            icon.draw(canvas)

            val histogram = HashMap<Int, Int>()
            for (y in 0 until SAMPLE) {
                for (x in 0 until SAMPLE) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) < 200) continue
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    if (min > 235) continue      // 近白（图标留白/描边）
                    if (max < 26) continue       // 近黑（背景）
                    val key = ((r shr 5) shl 10) or ((g shr 5) shl 5) or (b shr 5)
                    histogram[key] = (histogram[key] ?: 0) + 1
                }
            }
            if (histogram.isEmpty()) return null

            val key = histogram.maxByOrNull { it.value }!!.key
            val r = (((key shr 10) and 0x1F) shl 3) or 0x04
            val g = (((key shr 5) and 0x1F) shl 3) or 0x04
            val b = ((key and 0x1F) shl 3) or 0x04
            return normalize(Color.rgb(r, g, b))
        } catch (t: Throwable) {
            return null
        } finally {
            bitmap.recycle()
        }
    }

    /** 限制饱和度和明度，避免白色文字看不清。 */
    private fun normalize(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1].coerceIn(0.35f, 1f)
        hsv[2] = hsv[2].coerceIn(0.32f, 0.72f)
        return Color.HSVToColor(hsv)
    }
}
