package com.example.myapplication

import android.util.Log
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class OdooRepository(
    private val baseUrl: String,
    private val database: String,
    private val username: String,
    private val password: String
) {
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val odooApiService = retrofit.create(OdooApiService::class.java)

    companion object {
        private const val TAG = "OdooRepository"
    }

    suspend fun authenticate(): Int? {
        val authRequest = AuthRequest(
            params = AuthParams(
                args = listOf(database, username, password, emptyMap<String, Any>())
            )
        )
        return try {
            val response = odooApiService.authenticate(authRequest)
            Log.d(TAG, "Autenticación exitosa. UID: ${response.result}")
            response.result
        } catch (e: Exception) {
            Log.e(TAG, "Error de autenticación: ${e.message}")
            null
        }
    }

    suspend fun getCalendarEvents(uid: Int): List<CalendarEvent> {
        val partnerId = getPartnerId(uid)
        Log.d(TAG, "Partner ID obtenido: $partnerId")
        if (partnerId == null) {
            Log.e(TAG, "No se pudo obtener el Partner ID")
            return emptyList()
        }

        val attendeeIds = getAttendeeIds(uid, partnerId)
        Log.d(TAG, "IDs de asistentes obtenidos: $attendeeIds")
        if (attendeeIds == null || attendeeIds.isEmpty()) {
            Log.e(TAG, "No se encontraron IDs de asistentes")
            return emptyList()
        }

        val userTimezone = getUserTimezone(uid)
        Log.d(TAG, "Zona horaria del usuario: $userTimezone")

        val calendarEventRequest = CalendarEventRequest(
            params = CalendarEventParams(
                args = listOf(
                    database,
                    uid,
                    password,
                    "calendar.event",
                    "search_read",
                    listOf(listOf(listOf("attendee_ids", "in", attendeeIds))),
                    mapOf(
                        "fields" to listOf("name", "start", "stop", "allday", "description", "location", "attendee_ids", "alarm_ids"),
                        "order" to "start asc"
                    )
                )
            )
        )
        return try {
            val response = odooApiService.getCalendarEvents(calendarEventRequest)
            val events = response.result?.map { event ->
                CalendarEvent(
                    id = event.id,
                    name = event.name,
                    start = convertToUserTimezone(event.start, userTimezone),
                    stop = convertToUserTimezone(event.stop, userTimezone),
                    allday = event.allday,
                    description = event.description,
                    location = event.location,
                    alarmIds = event.alarm_ids?.map { it.toInt() } ?: emptyList()
                )
            } ?: emptyList()
            Log.d(TAG, "Eventos obtenidos: ${events.size}")
            events.forEach { event ->
                Log.d(TAG, "Evento: ${event.name}, Alarmas: ${event.alarmIds}")
            }
            events
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener eventos del calendario: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAlarmDetails(uid: Int, alarmId: Int): Int? {
        Log.d(TAG, "Obteniendo detalles de la alarma con ID: $alarmId")
        val alarmRequest = AlarmDetailsRequest(
            params = AlarmDetailsParams(
                args = listOf(
                    database,
                    uid,
                    password,
                    "calendar.alarm",
                    "read",
                    listOf(alarmId),
                    mapOf("fields" to listOf("duration", "interval"))  // Cambiado de lista a mapa
                )
            )
        )
        return try {
            val response = odooApiService.getAlarmDetails(alarmRequest)
            Log.d(TAG, "Respuesta completa de detalles de alarma: $response")

            if (response.result == null) {
                Log.e(TAG, "La respuesta de la alarma es nula")
                if (response.error != null) {
                    Log.e(TAG, "Error en la respuesta: ${response.error}")
                }
                return null
            }

            if (response.result.isEmpty()) {
                Log.e(TAG, "La respuesta de la alarma está vacía")
                return null
            }

            val alarm = response.result.first()
            Log.d(TAG, "Detalles de la alarma: $alarm")

            val duration = (alarm["duration"] as? Number)?.toInt()
            val interval = alarm["interval"] as? String

            Log.d(TAG, "Duración: $duration, Intervalo: $interval")

            if (duration == null || interval == null) {
                Log.e(TAG, "Duración o intervalo son nulos")
                return null
            }

            val minutesBefore = when (interval) {
                "minutes" -> duration
                "hours" -> duration * 60
                "days" -> duration * 60 * 24
                else -> {
                    Log.e(TAG, "Intervalo desconocido: $interval")
                    null
                }
            }

            Log.d(TAG, "Minutos antes: $minutesBefore")
            minutesBefore
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener detalles de la alarma: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun convertToUserTimezone(dateTimeString: String, userTimezone: String): String {
        val odooFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        odooFormat.timeZone = TimeZone.getTimeZone("UTC")

        val date = odooFormat.parse(dateTimeString) ?: return dateTimeString

        val userFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        userFormat.timeZone = TimeZone.getTimeZone(userTimezone)

        return userFormat.format(date)
    }

    private suspend fun getPartnerId(uid: Int): Int? {
        val userRequest = UserReadRequest(
            params = UserReadParams(
                args = listOf(
                    database,
                    uid,
                    password,
                    "res.users",
                    "read",
                    listOf(uid),
                    mapOf("fields" to listOf("partner_id"))
                )
            )
        )

        try {
            val response = odooApiService.readUsers(userRequest)

            if (response.result != null && response.result.isNotEmpty()) {
                val userData = response.result[0]
                val partnerIdValue = userData["partner_id"]

                val firstNumber = (partnerIdValue as? List<*>)?.firstOrNull { it is Number }?.let { (it as Number).toInt() }

                return firstNumber
            }

            Log.e(TAG, "No partner_id found")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Exception occurred while getting partner_id: ${e.message}")
            return null
        }
    }

    private suspend fun getAttendeeIds(userId: Int, partnerId: Int): List<Int>? {
        val request = SearchRelatedEventsRequest(
            params = SearchRelatedEventsParams(
                args = listOf(
                    database,
                    userId,
                    password,
                    "calendar.attendee",
                    "search",
                    listOf(listOf(listOf("partner_id", "=", partnerId)))
                )
            )
        )
        try {
            val response = odooApiService.searchRelatedEvents(request)
            Log.d(TAG, "Attendee IDs Response: ${response.result}")
            return response.result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting attendee IDs: ${e.message}")
            return null
        }
    }

    private suspend fun getUserTimezone(uid: Int): String {
        val userRequest = UserReadRequest(
            params = UserReadParams(
                args = listOf(
                    database,
                    uid,
                    password,
                    "res.users",
                    "read",
                    listOf(uid),
                    mapOf("fields" to listOf("tz"))
                )
            )
        )

        try {
            val response = odooApiService.readUsers(userRequest)
            if (response.result != null && response.result.isNotEmpty()) {
                val userData = response.result[0]
                return userData["tz"] as? String ?: "UTC"
            }
            return "UTC"
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener la zona horaria del usuario: ${e.message}")
            return "UTC"
        }
    }
}