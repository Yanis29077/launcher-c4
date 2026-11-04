package com.launcherc4.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

// Dessine la voiture vue du dessus (voiture tournée vers la droite).
class CarView(context: Context) : View(context) {

    var frontLeftOpen: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val bodyPaint = fillPaint("#2B323C")
    private val wheelPaint = fillPaint("#1A1F26")
    private val glassPaint = fillPaint("#14181D")
    private val roofPaint = fillPaint("#1A1F26")

    private val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0E1013")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val doorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private fun fillPaint(hex: String): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.parseColor(hex)
        p.style = Paint.Style.FILL
        return p
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val availW = (width - paddingLeft - paddingRight).toFloat()
        val availH = (height - paddingTop - paddingBottom).toFloat()
        val s = minOf(availW / 320f, availH / 170f)
        val dx = paddingLeft + (availW - 320f * s) / 2f
        val dy = paddingTop + (availH - 170f * s) / 2f

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(s, s)

        // carrosserie
        canvas.drawRoundRect(RectF(40f, 50f, 280f, 126f), 30f, 30f, bodyPaint)

        // roues
        canvas.drawRoundRect(RectF(62f, 40f, 102f, 54f), 4f, 4f, wheelPaint)
        canvas.drawRoundRect(RectF(62f, 122f, 102f, 136f), 4f, 4f, wheelPaint)
        canvas.drawRoundRect(RectF(214f, 40f, 254f, 54f), 4f, 4f, wheelPaint)
        canvas.drawRoundRect(RectF(214f, 122f, 254f, 136f), 4f, 4f, wheelPaint)

        // pare-brise, toit, lunette arrière
        canvas.drawRoundRect(RectF(196f, 58f, 236f, 118f), 8f, 8f, glassPaint)
        canvas.drawRoundRect(RectF(92f, 60f, 188f, 116f), 10f, 10f, roofPaint)
        canvas.drawRoundRect(RectF(70f, 60f, 88f, 116f), 6f, 6f, glassPaint)

        // séparation des portes
        canvas.drawLine(140f, 52f, 140f, 124f, seamPaint)

        // porte avant gauche ouverte
        if (frontLeftOpen) {
            canvas.drawLine(150f, 52f, 176f, 22f, doorPaint)
        }

        canvas.restore()
    }
}
