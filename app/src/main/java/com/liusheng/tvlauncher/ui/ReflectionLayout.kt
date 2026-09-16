package com.liusheng.tvlauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.FrameLayout

/** 把第一个子 View 缓存为位图，并在下方绘制渐隐倒影。 */
class ReflectionLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var reflectionRatio: Float = DEFAULT_RATIO
    var reflectionGap: Int = dp(DEFAULT_GAP_DP)
    var reflectionAlpha: Int = DEFAULT_ALPHA
    var reflectionEnabled: Boolean = true

    private var contentWidth = 0
    private var contentHeight = 0

    private var bmpWidth = 0
    private var bmpHeight = 0
    private var marginX = 0
    private var marginY = 0

    private var contentBitmap: Bitmap? = null
    private var reflectionBitmap: Bitmap? = null
    private var fadeShader: Shader? = null

    private val reflectPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()

    private var recording = false
    private var dirty = true
    private var lastScale = 1f

    init {
        setWillNotDraw(false)
        drawPaint.alpha = reflectionAlpha
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val child = getChildAt(0)
        val cw = child?.measuredWidth ?: measuredWidth
        val ch = child?.measuredHeight ?: measuredHeight
        if (cw != contentWidth || ch != contentHeight) {
            contentWidth = cw
            contentHeight = ch
            dirty = true
        }

        val extra = if (reflectionEnabled) reflectionGap + reflectionHeight() else 0
        setMeasuredDimension(measuredWidth, ch + extra)
    }

    private fun reflectionHeight(): Int =
        (contentHeight * reflectionRatio).toInt().coerceAtLeast(1)

    /** 子 View 的图标、文字或描边改变时重建倒影。 */
    fun invalidateReflection() {
        dirty = true
        postInvalidateOnAnimation()
    }

    private fun ensureBitmaps() {
        if (contentWidth <= 0 || contentHeight <= 0) return

        // 给聚焦缩放留出位图边距。
        val w = (contentWidth * (1f + MARGIN_RATIO * 2)).toInt()
        val h = (contentHeight * (1f + MARGIN_RATIO * 2)).toInt()
        val rh = reflectionHeight()

        if (contentBitmap?.width != w || contentBitmap?.height != h) {
            contentBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmpWidth = w
            bmpHeight = h
            marginX = (w - contentWidth) / 2
            marginY = (h - contentHeight) / 2
        }
        if (reflectionBitmap?.width != w || reflectionBitmap?.height != rh) {
            reflectionBitmap = Bitmap.createBitmap(w, rh, Bitmap.Config.ARGB_8888)
            fadeShader = LinearGradient(
                0f, 0f, 0f, rh.toFloat(),
                (reflectionAlpha shl 24) or 0x00FFFFFF,
                0x00FFFFFF,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        if (!reflectionEnabled || recording || contentHeight <= 0 || contentWidth <= 0) return

        val child = getChildAt(0)
        // 缩放期间重新录制子 View。
        val scale = child?.scaleX ?: 1f
        if (scale != lastScale) {
            lastScale = scale
            dirty = true
        }

        ensureBitmaps()
        val cb = contentBitmap ?: return
        val rb = reflectionBitmap ?: return

        if (dirty) {
            recordAndFade(cb, rb)
            dirty = false
        }

        canvas.save()
        // 补偿位图边距，让倒影接在卡片下方。
        canvas.translate(-marginX.toFloat(), (contentHeight + reflectionGap - marginY).toFloat())
        canvas.drawBitmap(rb, 0f, 0f, drawPaint)
        canvas.restore()
    }

    /** 缓存失效时才录制和渐隐。 */
    private fun recordAndFade(cb: Bitmap, rb: Bitmap) {
        recording = true
        val contentCanvas = Canvas(cb)
        contentCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        contentCanvas.translate(-marginX.toFloat(), -marginY.toFloat())
        super.dispatchDraw(contentCanvas)
        recording = false

        val mirrorCanvas = Canvas(rb)
        mirrorCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val scaleY = rb.height.toFloat() / bmpHeight.toFloat()
        matrix.reset()
        matrix.setScale(1f, -scaleY)
        matrix.postTranslate(0f, rb.height.toFloat())

        reflectPaint.reset()
        reflectPaint.isFilterBitmap = true
        reflectPaint.alpha = 255
        mirrorCanvas.drawBitmap(cb, matrix, reflectPaint)

        reflectPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        reflectPaint.shader = fadeShader
        mirrorCanvas.drawRect(0f, 0f, rb.width.toFloat(), rb.height.toFloat(), reflectPaint)
        reflectPaint.shader = null
        reflectPaint.xfermode = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        /** 倒影高度与卡片高度的比例。 */
        const val DEFAULT_RATIO = 0.42f
        private const val DEFAULT_GAP_DP = 6
        private const val DEFAULT_ALPHA = 0x62

        /** 位图边距用于容纳缩放动画。 */
        private const val MARGIN_RATIO = 0.12f
    }
}
