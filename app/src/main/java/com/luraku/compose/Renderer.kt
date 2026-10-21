package com.luraku.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dessin partagé entre l'aperçu à l'écran et l'export PNG, pour que le résultat
 * enregistré soit identique à ce que l'on voit.
 */
object Renderer {

    const val EXPORT_LONG_SIDE = 2048

    /** Coins de l'image après inclinaison + perspective, relatifs à son centre (non mis à l'échelle). */
    private fun projectedCorners(l: Layer): FloatArray {
        val w = l.bitmap.width.toFloat()
        val h = l.bitmap.height.toFloat()
        val d = 2.5f * max(w, h)
        val ax = Math.toRadians(l.tiltX.toDouble())
        val ay = Math.toRadians(l.tiltY.toDouble())
        val cosX = cos(ax).toFloat()
        val sinX = sin(ax).toFloat()
        val cosY = cos(ay).toFloat()
        val sinY = sin(ay).toFloat()
        val xs = floatArrayOf(-w / 2, w / 2, w / 2, -w / 2)
        val ys = floatArrayOf(-h / 2, -h / 2, h / 2, h / 2)
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val x = if (l.flipped) -xs[i] else xs[i]
            val y1 = ys[i] * cosX
            val z1 = ys[i] * sinX
            val x2 = x * cosY + z1 * sinY
            val z2 = -x * sinY + z1 * cosY
            val f = d / (d - z2)
            out[2 * i] = x2 * f
            out[2 * i + 1] = y1 * f
        }
        return out
    }

    /** Les 4 coins de l'image dans le repère de la page (pour le contour et la sélection au doigt). */
    fun pageCorners(l: Layer): FloatArray {
        val p = projectedCorners(l)
        val r = Math.toRadians(l.rotation.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val px = p[2 * i]
            val py = p[2 * i + 1]
            out[2 * i] = l.cx + l.scale * (px * c - py * s)
            out[2 * i + 1] = l.cy + l.scale * (px * s + py * c)
        }
        return out
    }

    fun contains(l: Layer, x: Float, y: Float): Boolean {
        val q = pageCorners(l)
        var pos = false
        var neg = false
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val cross = (q[2 * j] - q[2 * i]) * (y - q[2 * i + 1]) -
                (q[2 * j + 1] - q[2 * i + 1]) * (x - q[2 * i])
            if (cross > 0) pos = true else if (cross < 0) neg = true
        }
        return !(pos && neg)
    }

    fun drawLayer(canvas: Canvas, l: Layer, paint: Paint) {
        val w = l.bitmap.width.toFloat()
        val h = l.bitmap.height.toFloat()
        val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val m = Matrix()
        m.setPolyToPoly(src, 0, projectedCorners(l), 0, 4)
        paint.alpha = (l.alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(l.cx, l.cy)
        canvas.rotate(l.rotation)
        canvas.scale(l.scale, l.scale)
        canvas.concat(m)
        canvas.drawBitmap(l.bitmap, 0f, 0f, paint)
        canvas.restore()
    }

    fun newPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Rend la page complète en bitmap, calques du fond vers le dessus. */
    fun render(layers: List<Layer>, pageW: Float, pageH: Float, background: Background): Bitmap {
        val k = EXPORT_LONG_SIDE / max(pageW, pageH)
        val bmp = Bitmap.createBitmap(
            (pageW * k).roundToInt().coerceAtLeast(1),
            (pageH * k).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bmp)
        background.color?.let { canvas.drawColor(it) }
        canvas.scale(k, k)
        val paint = newPaint()
        layers.forEach { drawLayer(canvas, it, paint) }
        return bmp
    }
}
