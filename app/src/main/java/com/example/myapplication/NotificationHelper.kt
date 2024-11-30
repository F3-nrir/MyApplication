package com.example.myapplication

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val CHANNEL_ID = "CalendarEventChannel"
        private const val TAG = "NotificationHelper"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Eventos del Calendario",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones para eventos del calendario"
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Canal de notificación creado")
        }
    }

    fun scheduleNotification(event: CalendarEvent, minutesBefore: Int) {
        val notificationIntent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("eventId", event.id)
            putExtra("eventTitle", event.name)
            putExtra("eventStart", event.start)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.id,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val eventTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(event.start)
        val notificationTime = eventTime?.time?.minus(minutesBefore * 60 * 1000)

        if (notificationTime != null && notificationTime > System.currentTimeMillis()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    scheduleExactAlarm(notificationTime, pendingIntent)
                    Log.d(TAG, "Alarma exacta programada para el evento: ${event.name} a las ${Date(notificationTime)}")
                } else {
                    Log.w(TAG, "No se pueden programar alarmas exactas. Solicitando permiso...")
                    requestExactAlarmPermission()
                    scheduleInexactAlarm(notificationTime, pendingIntent)
                    Log.d(TAG, "Alarma inexacta programada para el evento: ${event.name} a las ${Date(notificationTime)}")
                }
            } else {
                scheduleExactAlarm(notificationTime, pendingIntent)
                Log.d(TAG, "Alarma exacta programada para el evento: ${event.name} a las ${Date(notificationTime)}")
            }
        } else {
            Log.e(TAG, "No se pudo programar la notificación para el evento: ${event.name}. Tiempo de notificación inválido.")
        }
    }

    private fun scheduleExactAlarm(notificationTime: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                notificationTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                notificationTime,
                pendingIntent
            )
        }
    }

    private fun scheduleInexactAlarm(notificationTime: Long, pendingIntent: PendingIntent) {
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            notificationTime,
            pendingIntent
        )
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
        }
    }

    fun showNotification(eventId: Int, eventTitle: String, eventStart: String) {
        val timeRemaining = getTimeRemaining(eventStart)
        val notificationText = "Comienza en $timeRemaining"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(eventTitle)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(eventId, notification)
        Log.d(TAG, "Notificación mostrada para el evento: $eventTitle")
    }

    private fun getTimeRemaining(eventStart: String): String {
        val eventTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(eventStart)
        val currentTime = System.currentTimeMillis()
        val timeDiff = eventTime?.time?.minus(currentTime) ?: 0

        val minutes = abs(timeDiff / (60 * 1000)) % 60
        val hours = abs(timeDiff / (60 * 60 * 1000)) % 24
        val days = abs(timeDiff / (24 * 60 * 60 * 1000))

        return when {
            days > 0 -> "$days día${if (days > 1) "s" else ""}"
            hours > 0 -> "$hours hora${if (hours > 1) "s" else ""}"
            else -> "$minutes minuto${if (minutes > 1) "s" else ""}"
        }
    }
}