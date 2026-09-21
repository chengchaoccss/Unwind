package com.armilla.neckcare.ui

import com.armilla.neckcare.data.repository.InMemorySessionRepository
import com.armilla.neckcare.data.repository.InMemorySettingsRepository
import com.armilla.neckcare.data.repository.Posture
import com.armilla.neckcare.data.repository.Settings
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.Measurement
import com.armilla.neckcare.domain.model.SessionMode
import com.armilla.neckcare.domain.model.TestResult
import com.armilla.neckcare.domain.usecase.HeadAngles
import com.armilla.neckcare.ui.lobby.LobbyViewModel
import com.armilla.neckcare.ui.navigation.MainEvent
import com.armilla.neckcare.ui.navigation.MainPage
import com.armilla.neckcare.ui.navigation.MainViewModel
import com.armilla.neckcare.ui.navigation.OnboardingStep
import com.armilla.neckcare.ui.records.RecordsEvent
import com.armilla.neckcare.ui.records.RecordsRange
import com.armilla.neckcare.ui.records.RecordsViewModel
import com.armilla.neckcare.ui.session.SessionEvent
import com.armilla.neckcare.ui.session.SessionStage
import com.armilla.neckcare.ui.session.SessionViewModel
import com.armilla.neckcare.ui.session.StepStatus
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val dt = 1f / 72f

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun result(date: String, vararg angles: Int): TestResult {
        val millis = LocalDate.parse(date).atTime(7, 40).atZone(zone).toInstant().toEpochMilli()
        return TestResult(0, millis, SessionMode.FULL, Direction.testOrder.mapIndexed { i, d -> Measurement(d, angles[i]) })
    }

    // ---- MainViewModel

    @Test
    fun firstRunWalksThroughHealthPostureAndReminders() = runTest {
        val settings = InMemorySettingsRepository()
        val vm = MainViewModel(settings, InMemorySessionRepository())
        assertEquals(OnboardingStep.HEALTH, vm.state.value.onboarding)
        vm.onEvent(MainEvent.Navigate(MainPage.SETTINGS))
        assertEquals("navigation is locked during onboarding", MainPage.LOBBY, vm.state.value.page)
        vm.onEvent(MainEvent.HealthAccepted)
        vm.onEvent(MainEvent.PostureChosen(Posture.STANDING))
        vm.onEvent(MainEvent.RemindersChosen(false))
        assertNull(vm.state.value.onboarding)
        assertTrue(settings.settings.value.onboarded)
        assertEquals(Posture.STANDING, settings.settings.value.posture)
        assertFalse(settings.settings.value.remindersEnabled)
    }

    @Test
    fun startingAgainWithinThirtyMinutesIsMentionedOnceAndCanBeIgnored() = runTest {
        var now = 10_000_000L
        val settings = InMemorySettingsRepository(Settings(onboarded = true, lastSessionEndMillis = now - 5 * 60_000))
        val vm = MainViewModel(settings, InMemorySessionRepository(), now = { now })
        assertFalse(vm.mayStartSession())
        assertEquals("刚练过，隔一会儿再来", vm.state.value.notice)
        assertTrue(vm.mayStartSession())
        now += 40 * 60_000
        vm.onSessionFinished()
        now += 31 * 60_000
        assertTrue(vm.mayStartSession())
    }

    @Test
    fun deletingEverythingClearsDataAndReturnsToOnboarding() = runTest {
        val sessions = InMemorySessionRepository(listOf(result("2026-09-20", 62, 71, 46, 58, 36, 41)))
        val settings = InMemorySettingsRepository(Settings(onboarded = true, autoAdjust = false))
        val vm = MainViewModel(settings, sessions)
        vm.onEvent(MainEvent.AskDelete)
        assertTrue(vm.state.value.confirmingDelete)
        vm.onEvent(MainEvent.ConfirmDelete)
        advanceUntilIdle()
        assertTrue(sessions.history().isEmpty())
        assertEquals(Settings(), settings.settings.value)
        assertEquals(OnboardingStep.HEALTH, vm.state.value.onboarding)
    }

    @Test
    fun reminderTimesShiftByHalfHoursAndLowBatteryIsMentioned() = runTest {
        val settings = InMemorySettingsRepository(Settings(onboarded = true))
        val vm = MainViewModel(settings, InMemorySessionRepository(), batteryPercent = { 7 })
        assertNotNull(vm.state.value.notice)
        vm.onEvent(MainEvent.ShiftReminder(0, -30))
        vm.onEvent(MainEvent.ShiftReminder(1, 30))
        assertEquals(listOf("10:00", "16:00"), settings.settings.value.reminderTimes)
        vm.onEvent(MainEvent.NudgeVolume(-0.3f))
        assertEquals(0f, settings.settings.value.ambientVolume, 0.001f)
    }

    // ---- SessionViewModel

    private fun SessionViewModel.hold(seconds: Float, angles: HeadAngles) = repeat((seconds / dt).toInt()) { onTestFrame(dt, angles) }

    @Test
    fun aTestOnlySessionRecordsSkipsAndSavesThenShowsTheResult() = runTest {
        val sessions = InMemorySessionRepository()
        val vm = SessionViewModel(sessions)
        vm.onEvent(SessionEvent.Start(SessionMode.TEST_ONLY))
        assertEquals(SessionStage.CALIBRATING, vm.state.value.stage)
        var calibrated = false
        repeat((2.2f / dt).toInt()) { calibrated = vm.onCalibrationFrame(dt, 1f, 0f) || calibrated }
        assertTrue(calibrated)
        assertEquals(Direction.LEFT_ROTATION, vm.state.value.current)

        // Turn left to 60°, hold, come back: recorded and on to right rotation.
        var a = 0f
        while (a > -60f) { a -= 20f * dt; vm.onTestFrame(dt, HeadAngles(a, 0f, 0f)) }
        vm.hold(3f, HeadAngles(-60f, 0f, 0f))
        while (a < 0f) { a += 25f * dt; vm.onTestFrame(dt, HeadAngles(a, 0f, 0f)) }
        vm.hold(0.2f, HeadAngles.ZERO)
        assertEquals(Direction.RIGHT_ROTATION, vm.state.value.current)
        assertEquals(60, vm.state.value.steps.first().readingDeg)

        repeat(5) { vm.onEvent(SessionEvent.Next) }
        advanceUntilIdle()
        assertEquals(SessionStage.RESULT, vm.state.value.stage)
        assertEquals(1, sessions.history().single().measuredCount)
        assertEquals(StepStatus.SKIPPED, vm.state.value.steps.last().status)
    }

    @Test
    fun aFullSessionGoesOnToTheExercisesAndPausingStopsTheClock() = runTest {
        val vm = SessionViewModel(InMemorySessionRepository())
        vm.finishWithReadingsForCapture(SessionMode.FULL, Direction.entries.associateWith { 50 }, thenExercise = true)
        advanceUntilIdle()
        assertEquals(SessionStage.ORB, vm.state.value.stage)
        vm.onEvent(SessionEvent.Pause)
        assertTrue(vm.state.value.paused)
        vm.onEvent(SessionEvent.Resume)
        vm.onEvent(SessionEvent.Next)
        assertEquals(SessionStage.SHOULDER, vm.state.value.stage)
        vm.onEvent(SessionEvent.Next)
        assertEquals(SessionStage.PUNCH, vm.state.value.stage)
        vm.onEvent(SessionEvent.Next)
        assertEquals(SessionStage.RESULT, vm.state.value.stage)
    }

    @Test
    fun aFailedSaveIsReportedAndCanBeRetried() = runTest {
        val sessions = InMemorySessionRepository().apply { failNextSave = true }
        val vm = SessionViewModel(sessions)
        vm.finishWithReadingsForCapture(SessionMode.TEST_ONLY, Direction.entries.associateWith { 40 })
        advanceUntilIdle()
        assertTrue(vm.state.value.saveFailed)
        vm.onEvent(SessionEvent.RetrySave)
        advanceUntilIdle()
        assertFalse(vm.state.value.saveFailed)
        assertEquals(1, sessions.history().size)
    }

    @Test
    fun quittingWithoutSavingLeavesNoRecord() = runTest {
        val sessions = InMemorySessionRepository()
        val vm = SessionViewModel(sessions)
        vm.onEvent(SessionEvent.Start(SessionMode.FULL))
        vm.onEvent(SessionEvent.QuitWithoutSaving)
        advanceUntilIdle()
        assertEquals(SessionStage.LOBBY, vm.state.value.stage)
        assertTrue(sessions.history().isEmpty())
    }

    // ---- LobbyViewModel and RecordsViewModel

    private val history = listOf(result("2026-09-07", 58, 66, 42, 54, 33, 38), result("2026-09-20", 62, 71, 46, 58, 36, 41))

    @Test
    fun theLobbyStatesTheFiguresOfTheDesignBoard() = runTest {
        val vm =
            LobbyViewModel(
                InMemorySessionRepository(history), InMemorySettingsRepository(),
                com.armilla.neckcare.domain.usecase.MobilityInsights(zone),
            ) { ZonedDateTime.of(2026, 9, 21, 10, 45, 0, 0, zone) }
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals("早上好", s.greeting)
        assertEquals("上次测量，9月20日", s.armillaryTitle)
        assertEquals("314°", s.trend?.totalText)
        assertEquals("比两周前多 23°", s.trend?.changeText)
        assertEquals("左右旋转相差 9°", s.balanceHeadline)
        assertEquals("右边更灵活，今天会向左多放一组光球", s.balanceDetail)
        assertEquals("15:30", s.nextReminder)
    }

    @Test
    fun anEmptyLobbyInvitesAFirstTest() = runTest {
        val vm = LobbyViewModel(InMemorySessionRepository(), InMemorySettingsRepository()) { ZonedDateTime.of(2026, 9, 21, 20, 0, 0, 0, zone) }
        advanceUntilIdle()
        assertEquals("晚上好", vm.state.value.greeting)
        assertEquals("还没有测量", vm.state.value.armillaryTitle)
        assertNull(vm.state.value.trend)
    }

    @Test
    fun recordsFollowTheChosenRangeAndTheSelectedDay() = runTest {
        val vm = RecordsViewModel(InMemorySessionRepository(history), com.armilla.neckcare.domain.usecase.MobilityInsights(zone), zone) { LocalDate.parse("2026-09-21") }
        advanceUntilIdle()
        assertEquals(listOf(291, 314), vm.state.value.totals)
        assertEquals(62, vm.state.value.series.first().shownDeg)
        vm.onEvent(RecordsEvent.SelectDay(LocalDate.parse("2026-09-07")))
        assertEquals(58, vm.state.value.series.first().shownDeg)
        vm.onEvent(RecordsEvent.Range(RecordsRange.ALL))
        assertEquals(2, vm.state.value.totals.size)
        assertEquals(2, vm.state.value.calendar.count { it.hasRecord })
    }

    @Test
    fun recordsAreEmptyBeforeTheFirstTest() = runTest {
        val vm = RecordsViewModel(InMemorySessionRepository(), zone = zone) { LocalDate.parse("2026-09-21") }
        advanceUntilIdle()
        assertTrue(vm.state.value.empty)
        assertTrue(vm.state.value.calendar.none { it.hasRecord })
    }
}
