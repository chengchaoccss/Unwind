package com.armilla.neckcare.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.armilla.neckcare.domain.model.TestResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** PRD §12 "导出": one row per measurement with the session time, into the public Documents folder. */
class CsvExporter(private val context: Context, private val sessions: SessionRepository, private val posture: () -> String) {
    suspend fun export(): String =
        withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(Instant.now().atZone(zone))
            val name = "armilla_$stamp.csv"
            val csv = render(sessions.history(), zone, posture())
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Armilla")
                }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: error("no document uri")
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) } ?: error("cannot open $uri")
            "Documents/Armilla/$name"
        }

    companion object {
        fun render(history: List<TestResult>, zone: ZoneId, posture: String): String {
            val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            val out = StringBuilder("session_id,finished_at,mode,posture,direction,angle_deg,status,peak_speed_dps,dwell_ms,retries\n")
            history.forEach { r ->
                val at = time.format(Instant.ofEpochMilli(r.finishedAtMillis).atZone(zone))
                r.measurements.forEach { m ->
                    out.append("${r.sessionId},$at,${r.mode.name.lowercase()},$posture,${m.direction.key},${m.angleDeg},${m.status.name.lowercase()},${"%.1f".format(m.peakSpeedDps)},${m.dwellMs},${m.retries}\n")
                }
            }
            return out.toString()
        }
    }
}
