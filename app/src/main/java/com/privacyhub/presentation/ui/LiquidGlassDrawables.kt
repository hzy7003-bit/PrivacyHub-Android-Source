package com.privacyhub.presentation.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

internal class LiquidGlassBackdropDrawable(private val dark: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(b)

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            if (dark) Color.rgb(17, 27, 45) else Color.rgb(250, 252, 253),
            if (dark) Color.rgb(7, 14, 27) else Color.rgb(232, 239, 243),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)

        paint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.top,
            intArrayOf(
                Color.TRANSPARENT,
                if (dark) Color.argb(20, 94, 151, 246) else Color.argb(70, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)

        drawGlassCurrent(canvas, rect.height() * 0.18f, rect.width() * 0.10f, if (dark) 0.18f else 0.12f)
        drawGlassCurrent(canvas, rect.height() * 0.64f, rect.width() * -0.08f, if (dark) 0.12f else 0.08f)
        drawVerticalDepth(canvas)
        paint.shader = null
    }

    private fun drawGlassCurrent(canvas: Canvas, y: Float, xOffset: Float, alphaScale: Float) {
        path.reset()
        path.moveTo(rect.left + xOffset, y)
        path.cubicTo(
            rect.left + rect.width() * 0.18f + xOffset,
            y - rect.height() * 0.12f,
            rect.left + rect.width() * 0.34f + xOffset,
            y + rect.height() * 0.06f,
            rect.left + rect.width() * 0.54f + xOffset,
            y - rect.height() * 0.02f
        )
        path.cubicTo(
            rect.left + rect.width() * 0.76f + xOffset,
            y - rect.height() * 0.12f,
            rect.left + rect.width() * 0.88f + xOffset,
            y + rect.height() * 0.05f,
            rect.right + xOffset,
            y - rect.height() * 0.06f
        )

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = rect.width() * 0.018f
        paint.shader = LinearGradient(
            rect.left + xOffset,
            y,
            rect.right + xOffset,
            y,
            intArrayOf(
                Color.TRANSPARENT,
                if (dark) Color.argb((90 * alphaScale).toInt(), 125, 211, 252) else Color.argb((85 * alphaScale).toInt(), 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, paint)
    }

    private fun drawVerticalDepth(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        val stripeWidth = rect.width() / 6f
        for (index in 0..5) {
            val left = rect.left + stripeWidth * index
            paint.shader = LinearGradient(
                left,
                rect.top,
                left + stripeWidth,
                rect.bottom,
                if (index % 2 == 0) Color.TRANSPARENT else Color.argb(if (dark) 10 else 14, 255, 255, 255),
                if (index % 2 == 0) Color.argb(if (dark) 12 else 8, 15, 23, 42) else Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(left, rect.top, left + stripeWidth, rect.bottom, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated by the Android Drawable API")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
internal class LiquidGlassDrawable(
    private val fillTop: Int,
    private val fillBottom: Int,
    private val strokeColor: Int,
    private val innerStrokeColor: Int,
    private val highlightColor: Int,
    private val radiusPx: Float,
    private val strokePx: Float,
    private val sheenPx: Float,
    private val insetPx: Float,
    private val depthColor: Int,
    private val glintColor: Int
) : Drawable() {
    private val rect = RectF()
    private val innerRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(
            b.left + insetPx,
            b.top + insetPx,
            b.right - insetPx,
            b.bottom - insetPx
        )
        val rx = radiusPx.coerceAtMost(rect.width() / 2f).coerceAtMost(rect.height() / 2f)

        if (Color.alpha(depthColor) > 0) {
            val shadowRect = RectF(rect)
            shadowRect.offset(0f, rect.height() * 0.035f)
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = depthColor
            canvas.drawRoundRect(shadowRect, rx, rx, paint)
        }

        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            fillTop,
            fillBottom,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, rx, rx, paint)

        paint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(
                Color.argb(if (Color.alpha(fillTop) > 170) 62 else 42, 255, 255, 255),
                Color.TRANSPARENT,
                Color.argb(if (Color.alpha(fillTop) > 170) 54 else 36, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, rx, rx, paint)

        paint.shader = null
        if (strokePx > 0f && Color.alpha(strokeColor) > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokePx
            paint.color = strokeColor
            canvas.drawRoundRect(rect, rx, rx, paint)
        }

        if (Color.alpha(innerStrokeColor) > 0) {
            innerRect.set(rect)
            innerRect.inset(strokePx * 2f, strokePx * 2f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokePx
            paint.color = innerStrokeColor
            canvas.drawRoundRect(innerRect, (rx - strokePx).coerceAtLeast(0f), (rx - strokePx).coerceAtLeast(0f), paint)
        }

        if (sheenPx > 0f && Color.alpha(highlightColor) > 0) {
            path.reset()
            val y = rect.top + rect.height() * 0.24f
            path.moveTo(rect.left + rect.width() * 0.05f, y)
            path.cubicTo(
                rect.left + rect.width() * 0.18f,
                rect.top + rect.height() * 0.04f,
                rect.left + rect.width() * 0.35f,
                rect.top + rect.height() * 0.18f,
                rect.left + rect.width() * 0.54f,
                rect.top + rect.height() * 0.09f
            )
            sheen.strokeWidth = sheenPx
            sheen.shader = LinearGradient(
                rect.left,
                y,
                rect.left + rect.width() * 0.62f,
                rect.top,
                Color.TRANSPARENT,
                highlightColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, sheen)
            sheen.shader = null

            path.reset()
            path.moveTo(rect.right - rect.width() * 0.18f, rect.top + rect.height() * 0.18f)
            path.cubicTo(
                rect.right - rect.width() * 0.08f,
                rect.top + rect.height() * 0.24f,
                rect.right - rect.width() * 0.05f,
                rect.bottom - rect.height() * 0.18f,
                rect.right - rect.width() * 0.18f,
                rect.bottom - rect.height() * 0.10f
            )
            sheen.strokeWidth = (sheenPx * 0.45f).coerceAtLeast(1f)
            sheen.color = glintColor
            canvas.drawPath(path, sheen)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        sheen.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        sheen.colorFilter = colorFilter
    }

    @Deprecated("Deprecated by the Android Drawable API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
