package com.example.jarvis_personalassistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class GridView(context: Context) : View(context) {
    var density = 3 // 3x3 grid
    val currentRect = RectF()
    var isVisibleGrid = false
    var zoomedLevel = 0
    
    private val linePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 48f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#66000000") // Semi-transparent black
        style = Paint.Style.FILL
    }

    fun show() {
        isVisibleGrid = true
        zoomedLevel = 0
        resetRect()
        visibility = VISIBLE
        invalidate()
    }

    fun hide() {
        isVisibleGrid = false
        visibility = GONE
    }

    fun resetRect() {
        currentRect.set(0f, 0f, width.toFloat(), height.toFloat())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (currentRect.isEmpty) {
            resetRect()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isVisibleGrid || currentRect.isEmpty) return
        
        val w = currentRect.width()
        val h = currentRect.height()
        val cw = w / density
        val ch = h / density
        
        // Draw grid lines
        for (i in 1 until density) {
            val x = currentRect.left + i * cw
            canvas.drawLine(x, currentRect.top, x, currentRect.bottom, linePaint)
            val y = currentRect.top + i * ch
            canvas.drawLine(currentRect.left, y, currentRect.right, y, linePaint)
        }
        
        // Draw numbers
        for (row in 0 until density) {
            for (col in 0 until density) {
                val index = row * density + col + 1
                val cx = currentRect.left + col * cw + cw / 2
                val cy = currentRect.top + row * ch + ch / 2
                
                // Draw a small background circle for the number to make it highly legible
                canvas.drawCircle(cx, cy, 35f, bgPaint)
                // Draw text vertically centered
                val textHeight = textPaint.descent() - textPaint.ascent()
                val textOffset = textHeight / 2 - textPaint.descent()
                canvas.drawText(index.toString(), cx, cy + textOffset, textPaint)
            }
        }
    }

    fun getCellCenter(index: Int): Pair<Float, Float>? {
        if (index < 1 || index > density * density) return null
        val row = (index - 1) / density
        val col = (index - 1) % density
        val cw = currentRect.width() / density
        val ch = currentRect.height() / density
        val cx = currentRect.left + col * cw + cw / 2
        val cy = currentRect.top + row * ch + ch / 2
        return Pair(cx, cy)
    }

    fun zoomToCell(index: Int) {
        if (index < 1 || index > density * density) return
        val row = (index - 1) / density
        val col = (index - 1) % density
        val cw = currentRect.width() / density
        val ch = currentRect.height() / density
        
        currentRect.set(
            currentRect.left + col * cw,
            currentRect.top + row * ch,
            currentRect.left + col * cw + cw,
            currentRect.top + row * ch + ch
        )
        zoomedLevel++
        invalidate()
    }
}
