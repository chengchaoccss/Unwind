package com.armilla.neckcare.scene.environment

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Debug-only equirectangular grid: a line and a label every 10°, to read a sphere's UV mapping. */
object CalibrationGrid {
    fun render(width: Int = 4096, height: Int = 2048): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(14, 26, 32))
        val line = Paint().apply { color = Color.rgb(90, 110, 115); strokeWidth = 2f }
        val bold = Paint().apply { color = Color.rgb(240, 180, 90); strokeWidth = 5f }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f }
        val pxPerDeg = width / 360f
        for (az in -180..180 step 10) {
            val x = width / 2f + az * pxPerDeg
            canvas.drawLine(x, 0f, x, height.toFloat(), if (az % 90 == 0) bold else line)
        }
        for (el in -90..90 step 10) {
            val y = height / 2f - el * pxPerDeg
            canvas.drawLine(0f, y, width.toFloat(), y, if (el == 0) bold else line)
        }
        for (az in -180 until 180 step 10) for (el in -80..80 step 10) {
            canvas.drawText("$az,$el", width / 2f + az * pxPerDeg + 6f, height / 2f - el * pxPerDeg - 8f, text)
        }
        return bitmap
    }
}
