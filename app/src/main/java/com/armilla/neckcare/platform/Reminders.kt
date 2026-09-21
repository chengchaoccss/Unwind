package com.armilla.neckcare.platform

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.armilla.neckcare.R
import com.armilla.neckcare.data.repository.Settings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Weekday reminders (US-05). One inexact daily alarm per reminder time; the receiver stays silent
 * at weekends. Whether PICO OS shows app notifications while the headset is off the head is not
 * something this app can control (PRD §16 risk).
 */
object Reminders {
    private const val CHANNEL = "armilla_reminders"

    fun schedule(context: Context, settings: Settings) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        repeat(4) { alarms.cancel(pending(context, it)) }
        if (!settings.remindersEnabled) return
        settings.reminderTimes.forEachIndexed { index, text ->
            val time = runCatching { LocalTime.parse(if (text.length == 4) "0$text" else text) }.getOrNull() ?: return@forEachIndexed
            var at = LocalDateTime.of(LocalDate.now(), time)
            if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
            val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, millis, AlarmManager.INTERVAL_DAY, pending(context, index))
        }
    }

    private fun pending(context: Context, index: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, index, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun notify(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "活动提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val open =
            PendingIntent.getActivity(
                context, 0, Intent(context, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_spatial_launcher)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("花几分钟活动一下脖子和肩膀")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        runCatching { manager.notify(1, notification) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val day = LocalDate.now().dayOfWeek
        if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) Reminders.notify(context)
    }
}
