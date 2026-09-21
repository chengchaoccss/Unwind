package com.armilla.neckcare.data.repository

import com.armilla.neckcare.domain.model.ExerciseResult
import com.armilla.neckcare.domain.model.TestResult

/** Local-only store of finished tests and exercises (PRD §12). Nothing leaves the headset. */
interface SessionRepository {
    /** All finished tests, oldest first. */
    suspend fun history(): List<TestResult>

    /** Saves one test and returns its session id. Throws if the write fails. */
    suspend fun save(result: TestResult): Long

    suspend fun saveExercise(result: ExerciseResult)

    suspend fun exercises(sessionId: Long): List<ExerciseResult>

    suspend fun deleteAll()
}

class InMemorySessionRepository(initial: List<TestResult> = emptyList()) : SessionRepository {
    private val results = initial.toMutableList()
    private val exerciseResults = mutableListOf<ExerciseResult>()
    private var nextId = (initial.maxOfOrNull { it.sessionId } ?: 0L) + 1
    var failNextSave = false

    override suspend fun history(): List<TestResult> = results.sortedBy { it.finishedAtMillis }

    override suspend fun save(result: TestResult): Long {
        if (failNextSave) {
            failNextSave = false
            throw IllegalStateException("storage unavailable")
        }
        val id = nextId++
        results += result.copy(sessionId = id)
        return id
    }

    override suspend fun saveExercise(result: ExerciseResult) {
        exerciseResults += result
    }

    override suspend fun exercises(sessionId: Long) = exerciseResults.filter { it.sessionId == sessionId }

    override suspend fun deleteAll() {
        results.clear()
        exerciseResults.clear()
    }
}
