package com.armilla.neckcare.scene.environment

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Paints the pre-dawn mountain lake of the design as an equirectangular panorama.
 *
 * The artboards are a 90° horizontal-FOV rectilinear projection, 1600 px wide, so a pixel offset d
 * from the horizon line is an elevation of atan(d / 800). Every stop below is the artboard value
 * converted that way; colours are the artboard colours unchanged.
 *
 * Azimuth 0 is the direction the user faces after calibration, in the middle of the bitmap;
 * positive azimuth is to the user's right.
 */
object SkyPanorama {
    const val WIDTH = 4096
    const val HEIGHT = 2048

    /** Dawn glow sits 200 px right of centre on the lobby board: atan(200 / 800). */
    const val GLOW_AZIMUTH_DEG = 14.04f
    private const val GLOW_RADIUS_DEG = 26.57f

    private const val PX_PER_DEG = WIDTH / 360f

    fun render(): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawSky(canvas)
        drawStars(canvas)
        drawGlow(canvas, mirrored = false)
        drawMountains(canvas)
        drawWater(canvas)
        return bitmap
    }

    private fun x(azimuthDeg: Float) = WIDTH / 2f + azimuthDeg * PX_PER_DEG

    private fun y(elevationDeg: Float) = HEIGHT / 2f - elevationDeg * PX_PER_DEG

    private fun drawSky(canvas: Canvas) {
        // #587878 at the horizon, #2d4b55 at 7.06°, #16303b at 18.07°, #0b161d from 29.36° up.
        val top = y(90f)
        val horizon = y(0f)
        val span = horizon - top
        fun stop(elevation: Float) = (y(elevation) - top) / span
        val paint =
            Paint().apply {
                shader =
                    LinearGradient(
                        0f, top, 0f, horizon,
                        intArrayOf(
                            Color.parseColor("#0b161d"),
                            Color.parseColor("#0b161d"),
                            Color.parseColor("#16303b"),
                            Color.parseColor("#2d4b55"),
                            Color.parseColor("#587878"),
                        ),
                        floatArrayOf(0f, stop(29.36f), stop(18.07f), stop(7.06f), 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, top, WIDTH.toFloat(), horizon, paint)
    }

    private fun drawStars(canvas: Canvas) {
        // About 48 stars in a 90° × 27° window of the artboard; keep that density over the dome.
        val random = Random(20260921)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pxPerArtboardPx = PX_PER_DEG * 90f / 1600f
        repeat(620) {
            val azimuth = random.nextFloat() * 360f - 180f
            // Uniform over the sphere cap above 9°, so stars do not crowd the zenith rows.
            val sinMin = sin(9.0 * PI / 180).toFloat()
            val elevation =
                Math.toDegrees(kotlin.math.asin((sinMin + random.nextFloat() * (1f - sinMin)).toDouble()))
                    .toFloat()
            val radius = (0.7f + random.nextFloat()) * pxPerArtboardPx * 1.6f
            val alpha = 0.25f + random.nextFloat() * 0.55f
            paint.color = Color.argb((alpha * 255).toInt(), 0xEF, 0xE9, 0xDC)
            // Stretch horizontally with 1 / cos(elevation) so stars stay round on the sphere.
            val stretch = 1f / kotlin.math.cos(elevation * PI.toFloat() / 180f).coerceAtLeast(0.15f)
            canvas.drawOval(
                RectF(
                    x(azimuth) - radius * stretch, y(elevation) - radius,
                    x(azimuth) + radius * stretch, y(elevation) + radius,
                ),
                paint,
            )
        }
    }

    private fun drawGlow(canvas: Canvas, mirrored: Boolean) {
        val radius = GLOW_RADIUS_DEG * PX_PER_DEG
        val cx = x(GLOW_AZIMUTH_DEG)
        val cy = y(0f)
        val strength = if (mirrored) 0.20f else 0.42f
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        cx, cy, radius,
                        Color.argb((strength * 255).toInt(), 0xE2, 0xCF, 0x9F),
                        Color.argb(0, 0xE2, 0xCF, 0x9F),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.save()
        if (mirrored) canvas.clipRect(0f, cy, WIDTH.toFloat(), HEIGHT.toFloat())
        else canvas.clipRect(0f, 0f, WIDTH.toFloat(), cy)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.restore()
    }

    /** Three ridgelines, far to near, with mist bands between them as on the artboards. */
    private fun drawMountains(canvas: Canvas) {
        drawRidge(canvas, "#48646b", minDeg = 4.2f, maxDeg = 8.4f, seed = 11)
        drawMist(canvas, elevationDeg = 1.7f, alpha = 0.22f, seed = 1)
        drawRidge(canvas, "#2c4852", minDeg = 1.4f, maxDeg = 6.0f, seed = 23)
        drawMist(canvas, elevationDeg = 1.0f, alpha = 0.16f, seed = 2)
        drawRidge(canvas, "#162c35", minDeg = 0.5f, maxDeg = 3.4f, seed = 37)
    }

    private fun ridgeHeight(azimuthDeg: Float, minDeg: Float, maxDeg: Float, seed: Int): Float {
        // Whole-number harmonics of the full circle, so the ridge closes seamlessly at ±180°.
        val a = azimuthDeg * PI.toFloat() / 180f
        val s = seed.toFloat()
        var h = 0.5f
        h += 0.26f * sin(3 * a + s)
        h += 0.17f * sin(7 * a + s * 1.7f)
        h += 0.10f * sin(13 * a + s * 2.3f)
        h += 0.06f * sin(29 * a + s * 0.9f)
        h += 0.03f * sin(61 * a + s * 3.1f)
        return minDeg + (maxDeg - minDeg) * h.coerceIn(0f, 1f)
    }

    private fun ridgePath(minDeg: Float, maxDeg: Float, seed: Int, sign: Float): Path {
        val path = Path()
        path.moveTo(0f, y(0f))
        var px = 0
        while (px <= WIDTH) {
            val azimuth = (px - WIDTH / 2f) / PX_PER_DEG
            path.lineTo(px.toFloat(), y(sign * ridgeHeight(azimuth, minDeg, maxDeg, seed)))
            px += 4
        }
        path.lineTo(WIDTH.toFloat(), y(0f))
        path.close()
        return path
    }

    private fun drawRidge(canvas: Canvas, color: String, minDeg: Float, maxDeg: Float, seed: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor(color) }
        canvas.drawPath(ridgePath(minDeg, maxDeg, seed, sign = 1f), paint)
    }

    private fun drawMist(canvas: Canvas, elevationDeg: Float, alpha: Float, seed: Int) {
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((alpha * 255).toInt(), 0xC7, 0xD3, 0xCF)
                // Artboard blur is 16 px of a 1600 px / 90° view.
                maskFilter = BlurMaskFilter(16f * PX_PER_DEG * 90f / 1600f * 2f, BlurMaskFilter.Blur.NORMAL)
            }
        val random = Random(seed)
        val halfHeight = 0.85f * PX_PER_DEG
        var azimuth = -180f
        while (azimuth < 180f) {
            val width = 40f + random.nextFloat() * 50f
            val cx = x(azimuth + width / 2f)
            canvas.drawOval(
                RectF(
                    cx - width / 2f * PX_PER_DEG, y(elevationDeg) - halfHeight,
                    cx + width / 2f * PX_PER_DEG, y(elevationDeg) + halfHeight,
                ),
                paint,
            )
            azimuth += width + 8f + random.nextFloat() * 22f
        }
    }

    private fun drawWater(canvas: Canvas) {
        // #4d6d6d at the horizon, #1b333c at -12.07°, #081217 from -29.36° down.
        val horizon = y(0f)
        val bottom = y(-90f)
        val span = bottom - horizon
        fun stop(elevation: Float) = (y(elevation) - horizon) / span
        val water =
            Paint().apply {
                shader =
                    LinearGradient(
                        0f, horizon, 0f, bottom,
                        intArrayOf(
                            Color.parseColor("#4d6d6d"),
                            Color.parseColor("#1b333c"),
                            Color.parseColor("#081217"),
                            Color.parseColor("#081217"),
                        ),
                        floatArrayOf(0f, stop(-12.07f), stop(-29.36f), 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, horizon, WIDTH.toFloat(), bottom, water)

        // Blurred reflections of the ridges and the mist at 30 % opacity.
        val blur = BlurMaskFilter(3f * PX_PER_DEG * 90f / 1600f * 3f, BlurMaskFilter.Blur.NORMAL)
        val reflection = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = blur }
        canvas.save()
        canvas.clipRect(0f, horizon, WIDTH.toFloat(), bottom)
        listOf(
            Triple("#48646b", 4.2f to 8.4f, 11),
            Triple("#2c4852", 1.4f to 6.0f, 23),
            Triple("#162c35", 0.5f to 3.4f, 37),
        ).forEach { (color, range, seed) ->
            val c = Color.parseColor(color)
            reflection.color = Color.argb(77, Color.red(c), Color.green(c), Color.blue(c))
            canvas.drawPath(ridgePath(range.first, range.second, seed, sign = -1f), reflection)
        }
        canvas.restore()
        drawGlow(canvas, mirrored = true)
    }
}
