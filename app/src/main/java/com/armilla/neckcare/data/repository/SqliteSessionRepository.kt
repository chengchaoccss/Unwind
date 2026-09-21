package com.armilla.neckcare.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.ExerciseResult
import com.armilla.neckcare.domain.model.ExerciseType
import com.armilla.neckcare.domain.model.Measurement
import com.armilla.neckcare.domain.model.MeasurementStatus
import com.armilla.neckcare.domain.model.SessionMode
import com.armilla.neckcare.domain.model.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SQLite store with the Session / Measurement / ExerciseResult entities of PRD §12. */
class SqliteSessionRepository(context: Context) : SessionRepository {
    private val helper =
        object : SQLiteOpenHelper(context.applicationContext, "armilla.db", null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE session(id INTEGER PRIMARY KEY AUTOINCREMENT, ended_at INTEGER NOT NULL, mode TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE measurement(session_id INTEGER NOT NULL, direction TEXT NOT NULL, angle_deg INTEGER NOT NULL, " +
                        "peak_speed_dps REAL NOT NULL, dwell_ms INTEGER NOT NULL, retries INTEGER NOT NULL, status TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE exercise(session_id INTEGER NOT NULL, type TEXT NOT NULL, duration_s INTEGER NOT NULL, " +
                        "orbs_caught INTEGER NOT NULL, orbs_total INTEGER NOT NULL, laps_back INTEGER NOT NULL, " +
                        "laps_forward INTEGER NOT NULL, pause_count INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX measurement_session ON measurement(session_id)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

    override suspend fun history(): List<TestResult> =
        withContext(Dispatchers.IO) {
            val db = helper.readableDatabase
            val measurements = HashMap<Long, MutableList<Measurement>>()
            db.rawQuery(
                "SELECT session_id, direction, angle_deg, peak_speed_dps, dwell_ms, retries, status FROM measurement",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    val direction = Direction.fromKey(c.getString(1)) ?: continue
                    measurements.getOrPut(c.getLong(0)) { mutableListOf() } +=
                        Measurement(
                            direction = direction,
                            angleDeg = c.getInt(2),
                            peakSpeedDps = c.getFloat(3),
                            dwellMs = c.getLong(4),
                            retries = c.getInt(5),
                            status = MeasurementStatus.valueOf(c.getString(6)),
                        )
                }
            }
            val out = ArrayList<TestResult>()
            db.rawQuery("SELECT id, ended_at, mode FROM session ORDER BY ended_at", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out += TestResult(id, c.getLong(1), SessionMode.valueOf(c.getString(2)), measurements[id].orEmpty())
                }
            }
            out
        }

    override suspend fun save(result: TestResult): Long =
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val id =
                    db.insertOrThrow(
                        "session",
                        null,
                        ContentValues().apply {
                            put("ended_at", result.finishedAtMillis)
                            put("mode", result.mode.name)
                        },
                    )
                result.measurements.forEach { m ->
                    db.insertOrThrow(
                        "measurement",
                        null,
                        ContentValues().apply {
                            put("session_id", id)
                            put("direction", m.direction.key)
                            put("angle_deg", m.angleDeg)
                            put("peak_speed_dps", m.peakSpeedDps)
                            put("dwell_ms", m.dwellMs)
                            put("retries", m.retries)
                            put("status", m.status.name)
                        },
                    )
                }
                db.setTransactionSuccessful()
                id
            } finally {
                db.endTransaction()
            }
        }

    override suspend fun saveExercise(result: ExerciseResult) =
        withContext(Dispatchers.IO) {
            helper.writableDatabase.insertOrThrow(
                "exercise",
                null,
                ContentValues().apply {
                    put("session_id", result.sessionId)
                    put("type", result.type.key)
                    put("duration_s", result.durationSeconds)
                    put("orbs_caught", result.orbsCaught)
                    put("orbs_total", result.orbsTotal)
                    put("laps_back", result.lapsBack)
                    put("laps_forward", result.lapsForward)
                    put("pause_count", result.pauseCount)
                },
            )
            Unit
        }

    override suspend fun exercises(sessionId: Long): List<ExerciseResult> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<ExerciseResult>()
            helper.readableDatabase.rawQuery(
                "SELECT type, duration_s, orbs_caught, orbs_total, laps_back, laps_forward, pause_count FROM exercise WHERE session_id = ?",
                arrayOf(sessionId.toString()),
            ).use { c ->
                while (c.moveToNext()) {
                    val type = ExerciseType.entries.firstOrNull { it.key == c.getString(0) } ?: continue
                    out += ExerciseResult(sessionId, type, c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6))
                }
            }
            out
        }

    override suspend fun deleteAll() =
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.delete("measurement", null, null)
            db.delete("exercise", null, null)
            db.delete("session", null, null)
            Unit
        }
}
