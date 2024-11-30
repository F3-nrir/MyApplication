package com.example.myapplication

import retrofit2.http.Body
import retrofit2.http.POST

interface OdooApiService {
    @POST("/jsonrpc")
    suspend fun authenticate(@Body request: AuthRequest): AuthResponse

    @POST("/jsonrpc")
    suspend fun getCalendarEvents(@Body request: CalendarEventRequest): CalendarEventResponse

    @POST("/jsonrpc")
    suspend fun readUsers(@Body request: UserReadRequest): UserReadResponse

    @POST("/jsonrpc")
    suspend fun searchRelatedEvents(@Body request: SearchRelatedEventsRequest): SearchRelatedEventsResponse

    @POST("/jsonrpc")
    suspend fun getAlarmDetails(@Body request: AlarmDetailsRequest): AlarmDetailsResponse
}

data class AlarmDetailsResponse(
    val jsonrpc: String,
    val id: Int,
    val result: List<Map<String, Any>>?,
    val error: ErrorResponse?
)

data class ErrorResponse(
    val code: Int,
    val message: String,
    val data: ErrorData?
)

data class ErrorData(
    val name: String,
    val debug: String,
    val message: String,
    val arguments: List<Any>?,
    val exception_type: String?
)

data class AlarmDetailsRequest(
    val jsonrpc: String = "2.0",
    val method: String = "call",
    val params: AlarmDetailsParams,
    val id: Int = 1
)

data class AlarmDetailsParams(
    val service: String = "object",
    val method: String = "execute_kw",
    val args: List<Any>
)

data class SearchRelatedEventsRequest(
    val jsonrpc: String = "2.0",
    val method: String = "call",
    val params: SearchRelatedEventsParams,
    val id: Int = 1
)

data class SearchRelatedEventsParams(
    val service: String = "object",
    val method: String = "execute_kw",
    val args: List<Any>
)

data class SearchRelatedEventsResponse(
    val jsonrpc: String,
    val id: Int,
    val result: List<Int>?
)

data class UserReadRequest(
    val jsonrpc: String = "2.0",
    val method: String = "call",
    val params: UserReadParams,
    val id: Int = 1
)

data class UserReadParams(
    val service: String = "object",
    val method: String = "execute_kw",
    val args: List<Any>
)

data class UserReadResponse(
    val jsonrpc: String,
    val id: Int,
    val result: List<Map<String, Any>>?
)

data class AuthRequest(
    val jsonrpc: String = "2.0",
    val method: String = "call",
    val params: AuthParams,
    val id: Int = 1
)

data class AuthParams(
    val service: String = "common",
    val method: String = "authenticate",
    val args: List<Any>
)

data class AuthResponse(
    val jsonrpc: String,
    val id: Int,
    val result: Int?
)

data class CalendarEventRequest(
    val jsonrpc: String = "2.0",
    val method: String = "call",
    val params: CalendarEventParams,
    val id: Int = 2
)

data class CalendarEventParams(
    val service: String = "object",
    val method: String = "execute_kw",
    val args: List<Any>
)

data class CalendarEventResponse(
    val jsonrpc: String,
    val id: Int,
    val result: List<CalendarEventData>?
)

data class CalendarEventData(
    val id: Int,
    val name: String,
    val start: String,
    val stop: String,
    val allday: Boolean,
    val description: String?,
    val location: String?,
    val alarm_ids: List<Double>?
)

data class CalendarEvent(
    val id: Int,
    val name: String,
    val start: String,
    val stop: String,
    val allday: Boolean,
    val description: String?,
    val location: String?,
    val alarmIds: List<Int>
)