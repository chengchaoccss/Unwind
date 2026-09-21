package com.armilla.neckcare.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.Measurement
import com.armilla.neckcare.domain.model.SessionMode
import com.armilla.neckcare.domain.model.TestResult
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Debug builds only: when the marker file `seed_design_data` exists in the app's external files
 * directory and the store is empty, fills it with the sample history drawn on the artboards
 * (9/7 to 9/20, ending at 62/71/46/58/36/41 = 314°) so screens can be compared with the design.
 * Release builds and devices without the marker never see sample data.
 */
object DesignSampleSeeder {
    private val lastDay = mapOf(
        Direction.LEFT_ROTATION to 62, Direction.RIGHT_ROTATION to 71,
        Direction.FLEXION to 46, Direction.EXTENSION to 58,
        Direction.LEFT_BEND to 36, Direction.RIGHT_BEND to 41,
    )
    private val totals = listOf(291, 288, 296, 294, 300, 298, 303, 301, 306, 304, 309, 308, 311, 314)

    suspend fun seedIfRequested(context: Context, sessions: SessionRepository) {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val marker = File(context.getExternalFilesDir(null), "seed_design_data")
        if (!debuggable || !marker.exists() || sessions.history().isNotEmpty()) return
        val zone = ZoneId.systemDefault()
        val end = LocalDate.of(2026, 9, 20)
        totals.forEachIndexed { index, total ->
            val date = end.minusDays((totals.size - 1 - index).toLong())
            val scale = total / 314f
            var measurements = Direction.testOrder.map { d -> Measurement(d, (lastDay.getValue(d) * scale).roundToInt()) }
            // Put the rounding remainder on extension so each day sums to the drawn total.
            val drift = total - measurements.sumOf { it.angleDeg }
            measurements = measurements.map { if (it.direction == Direction.EXTENSION) it.copy(angleDeg = it.angleDeg + drift) else it }
            if (index == totals.lastIndex) measurements = Direction.testOrder.map { Measurement(it, lastDay.getValue(it)) }
            val millis = date.atTime(LocalTime.of(7, 40)).atZone(zone).toInstant().toEpochMilli()
            sessions.save(TestResult(0, millis, SessionMode.FULL, measurements))
        }
    }
}
