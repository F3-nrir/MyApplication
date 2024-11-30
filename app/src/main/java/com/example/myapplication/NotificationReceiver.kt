package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getIntExtra("eventId", 0)
        val eventTitle = intent.getStringExtra("eventTitle") ?: "Evento"
        val eventStart = intent.getStringExtra("eventStart") ?: ""

        Log.d(TAG, "Recibida notificación para el evento: $eventTitle")

        val notificationHelper = NotificationHelper(context)
        notificationHelper.showNotification(eventId, eventTitle, eventStart)
    }
}