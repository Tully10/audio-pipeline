package com.nocturne.audiocapture.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class WaveformProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private var markerFractions = emptyList<Float>()
    private var onMarkerTapped: ((Int) -> Unit)? = null
    private var onSeek: ((Float) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0E0E0"); style = Paint.Style.FILL }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1976D2"); style = Paint.Style.FILL }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6600"); style = Paint.Style.FILL }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1976D2"); style = Paint.Style.FILL }

    fun setProgress(fraction: Float) { progress = fraction.coerceIn(0f, 1f); invalidate() }
    fun setMarkers(fractions: List<Float>) { markerFractions = fractions; invalidate() }
    fun setOnMarkerTapped(cb: (Int) -> Unit) { onMarkerTapped = cb }
    fun setOnSeek(cb: (Float) -> Unit) { onSeek = cb }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val trackH = h * 0.25f; val trackTop = (h - trackH) / 2
        canvas.drawRoundRect(0f, trackTop, w, trackTop + trackH, 4f, 4f, trackPaint)
        canvas.drawRoundRect(0f, trackTop, w * progress, trackTop + trackH, 4f, 4f, progressPaint)
        canvas.drawCircle(w * progress, h / 2, h * 0.35f, thumbPaint)
        markerFractions.forEachIndexed { _, frac ->
            val x = frac * w
            canvas.drawPath(Path().apply {
                moveTo(x, trackTop - 4f); lineTo(x - 5f, trackTop - 14f); lineTo(x + 5f, trackTop - 14f); close()
            }, markerPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_MOVE) {
            val frac = (event.x / width.toFloat()).coerceIn(0f, 1f)
            val markerIdx = markerFractions.indexOfFirst { abs(it - frac) < 0.025f }
            if (event.action == MotionEvent.ACTION_UP && markerIdx >= 0) onMarkerTapped?.invoke(markerIdx)
            else onSeek?.invoke(frac)
            return true
        }
        return super.onTouchEvent(event)
    }
}
