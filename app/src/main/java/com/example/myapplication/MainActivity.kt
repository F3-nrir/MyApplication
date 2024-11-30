package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var refreshButton: Button
    private lateinit var logoutButton: Button
    private lateinit var odooRepository: OdooRepository
    private lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        refreshButton = findViewById(R.id.refreshButton)
        logoutButton = findViewById(R.id.logoutButton)

        notificationHelper = NotificationHelper(this)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                refreshButton.isEnabled = true
            }
        }

        refreshButton.setOnClickListener {
            refreshButton.isEnabled = false
            checkConnectionAndRefresh()
        }

        logoutButton.setOnClickListener {
            logout()
        }

        if (!isSessionActive()) {
            startLoginActivity()
        } else {
            initializeOdooRepository()
            loadCalendarEvents()
        }

        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Permiso de notificaciones concedido")
                } else {
                    Log.d(TAG, "Permiso de notificaciones denegado")
                    Toast.makeText(this, "No podrás recibir notificaciones de eventos sin este permiso", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun isSessionActive(): Boolean {
        val sharedPref = getSharedPreferences("OdooLogin", Context.MODE_PRIVATE)
        val url = sharedPref.getString("url", "") ?: ""
        val database = sharedPref.getString("database", "") ?: ""
        val username = sharedPref.getString("username", "") ?: ""
        val password = sharedPref.getString("password", "") ?: ""

        return url.isNotEmpty() && database.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()
    }

    private fun initializeOdooRepository() {
        val sharedPref = getSharedPreferences("OdooLogin", Context.MODE_PRIVATE)
        val url = sharedPref.getString("url", "") ?: ""
        val database = sharedPref.getString("database", "") ?: ""
        val username = sharedPref.getString("username", "") ?: ""
        val password = sharedPref.getString("password", "") ?: ""

        odooRepository = OdooRepository(url, database, username, password)
    }

    private fun logout() {
        val sharedPref = getSharedPreferences("OdooLogin", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            clear()
            apply()
        }

        val sharedPrefCalendar = getSharedPreferences("OdooCalendar", Context.MODE_PRIVATE)
        with(sharedPrefCalendar.edit()) {
            clear()
            apply()
        }

        startLoginActivity()
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun loadCalendarEvents() {
        if (isNetworkAvailable()) {
            fetchCalendarEvents()
        } else {
            val savedEvents = getSavedEvents()
            if (savedEvents.isNotEmpty()) {
                displayCalendar(savedEvents)
            } else {
                showError("No hay conexión a internet y no hay eventos guardados.")
            }
        }
    }

    private fun fetchCalendarEvents() {
        lifecycleScope.launch {
            try {
                val uid = odooRepository.authenticate()
                if (uid != null) {
                    Log.d(TAG, "Autenticación exitosa. UID: $uid")
                    val events = odooRepository.getCalendarEvents(uid)
                    Log.d(TAG, "Eventos obtenidos: ${events.size}")
                    saveEvents(events)
                    displayCalendar(events)
                    scheduleNotifications(events, uid)
                } else {
                    showError("La autenticación falló")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener eventos del calendario: ${e.message}")
                showError("Error: ${e.message}")
            } finally {
                refreshButton.isEnabled = true
            }
        }
    }

    private suspend fun scheduleNotifications(events: List<CalendarEvent>, uid: Int) {
        val eventos = events.reversed()
        eventos.forEach { event ->
            Log.d(TAG, "Procesando evento: ${event.name}, alarmIds: ${event.alarmIds}")
            if (event.alarmIds.isEmpty()) {
                Log.d(TAG, "El evento ${event.name} no tiene alarmas asociadas")
            }
            event.alarmIds.forEach { alarmId ->
                Log.d(TAG, "Procesando alarma ID: $alarmId para el evento: ${event.name}")
                try {
                    val minutesBefore = odooRepository.getAlarmDetails(uid, alarmId)
                    if (minutesBefore != null) {
                        Log.d(TAG, "Programando notificación para el evento: ${event.name}, ${minutesBefore} minutos antes")
                        notificationHelper.scheduleNotification(event, minutesBefore)
                    } else {
                        Log.e(TAG, "No se pudieron obtener los detalles de la alarma para alarmId: $alarmId del evento: ${event.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al procesar la alarma $alarmId para el evento ${event.name}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun saveEvents(events: List<CalendarEvent>) {
        val sharedPref = getSharedPreferences("OdooCalendar", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("saved_events", JSONArray(events.map { event ->
                JSONObject().apply {
                    put("id", event.id)
                    put("name", event.name)
                    put("start", event.start)
                    put("stop", event.stop)
                    put("allday", event.allday)
                    put("description", event.description ?: "")
                    put("location", event.location ?: "")
                    put("alarmIds", JSONArray(event.alarmIds ?: emptyList<Int>()))
                }
            }).toString())
            apply()
        }
    }

    private fun getSavedEvents(): List<CalendarEvent> {
        val sharedPref = getSharedPreferences("OdooCalendar", Context.MODE_PRIVATE)
        val savedEventsJson = sharedPref.getString("saved_events", null)
        return if (savedEventsJson != null) {
            val jsonArray = JSONArray(savedEventsJson)
            List(jsonArray.length()) { i ->
                val obj = jsonArray.getJSONObject(i)
                CalendarEvent(
                    obj.getInt("id"),
                    obj.getString("name"),
                    obj.getString("start"),
                    obj.getString("stop"),
                    obj.getBoolean("allday"),
                    obj.getString("description"),
                    obj.getString("location"),
                    (obj.optJSONArray("alarmIds")?.let { 0.until(it.length()).map { j -> it.getInt(j) } } ?: emptyList())
                )
            }
        } else {
            emptyList()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private fun checkConnectionAndRefresh() {
        if (isNetworkAvailable()) {
            fetchCalendarEvents()
        } else {
            showError("No hay conexión a internet")
            refreshButton.isEnabled = true
            loadCalendarEvents()
        }
    }

    private fun displayCalendar(events: List<CalendarEvent>) {
        val jsonEvents = JSONArray(events.map { event ->
            JSONObject().apply {
                put("id", event.id)
                put("title", event.name)
                put("start", event.start)
                put("end", event.stop)
                put("allDay", event.allday)
                put("description", event.description ?: "")
                put("location", event.location ?: "")
            }
        })

        val htmlContent = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Calendario Interactivo</title>
                <link href="https://cdn.jsdelivr.net/npm/fullcalendar@5.10.2/main.min.css" rel="stylesheet">
                <script src="https://cdn.jsdelivr.net/npm/fullcalendar@5.10.2/main.min.js"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/moment.js/2.29.1/moment.min.js"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/moment.js/2.29.1/locale/es.js"></script>
                <style>
                    body { 
                        font-family: Arial, sans-serif; 
                        margin: 0;
                        padding: 0;
                    }
                    #calendar { 
                        width: 100%; 
                        height: 100vh; 
                    }
                    .modal {
                        display: none;
                        position: fixed;
                        z-index: 1000;
                        left: 0;
                        top: 0;
                        width: 100%;
                        height: 100%;
                        overflow: auto;
                        background-color: rgba(0,0,0,0.4);
                    }
                    .modal-content {
                        margin: 15% auto;
                        padding: 20px;
                        border: 1px solid #888;
                        width: 80%;
                        max-width: 500px;
                        border-radius: 5px;
                        color: white;
                    }
                    .close {
                        color: #aaa;
                        float: right;
                        font-size: 28px;
                        font-weight: bold;
                        cursor: pointer;
                    }
                    .close:hover,
                    .close:focus {
                        color: black;
                        text-decoration: none;
                        cursor: pointer;
                    }
                    .fc-event-past {
                        background-color: #8F8F8F !important;
                        border-color: #8F8F8F !important;
                        color: white !important;
                    }
                    .fc-event-past .fc-event-dot {
                        background-color: #E46E78 !important;
                    }
                    .fc-event-future {
                        background-color: #017E84 !important;
                        border-color: #017E84 !important;
                        color: white !important;
                    }
                    .fc-event-future .fc-event-dot {
                        background-color: #E4A900 !important;
                    }
                    .fc-event-current {
                        background-color: #714B67 !important;
                        border-color: #714B67 !important;
                        color: white !important;
                    }
                    .fc-event-current .fc-event-dot {
                        background-color: #21B799 !important;
                    }
                    .event-past {
                        background-color: #8F8F8F;
                    }
                    .event-future {
                        background-color: #017E84;
                    }
                    .event-current {
                        background-color: #714B67;
                    }
                </style>
            </head>
            <body>
                <div id="calendar"></div>
                <div id="eventModal" class="modal">
                    <div class="modal-content">
                        <span class="close">&times;</span>
                        <h2 id="eventTitle"></h2>
                        <p id="eventDate"></p>
                        <p id="eventTime"></p>
                        <p id="eventDescription"></p>
                        <p id="eventLocation"></p>
                        <p id="eventStatus"></p>
                    </div>
                </div>
                <script>
                    document.addEventListener('DOMContentLoaded', function() {
                        moment.locale('es');
                        var calendarEl = document.getElementById('calendar');
                        var modal = document.getElementById('eventModal');
                        var span = document.getElementsByClassName("close")[0];
                        
                        var calendar = new FullCalendar.Calendar(calendarEl, {
                            initialView: 'dayGridMonth',
                            headerToolbar: {
                                left: 'prev,next today',
                                center: 'title',
                                right: 'dayGridMonth,timeGridWeek,timeGridDay'
                            },
                            allDayText: 'Todo el día',
                            locale: 'es',
                            buttonText: {
                                today: 'Hoy',
                                month: 'Mes',
                                week: 'Semana',
                                day: 'Día'
                            },
                            events: ${jsonEvents},
                            eventClassNames: function(arg) {
                                var now = new Date();
                                var start = new Date(arg.event.start);
                                var end = new Date(arg.event.end);
                                if (now < start) {
                                    return ['fc-event-future'];
                                } else if (now > end) {
                                    return ['fc-event-past'];
                                } else {
                                    return ['fc-event-current'];
                                }
                            },
                            eventClick: function(info) {
                                var event = info.event;
                                var now = new Date();
                                var start = new Date(event.start);
                                var end = new Date(event.end);
                                
                                document.getElementById('eventTitle').textContent = event.title;
                                document.getElementById('eventDate').textContent = 'Fecha: ' + event.start.toLocaleDateString('es-ES');
                                document.getElementById('eventTime').textContent = event.allDay ? 'Todo el día' : 'Hora: ' + event.start.toLocaleTimeString('es-ES') + ' - ' + event.end.toLocaleTimeString('es-ES');
                                
                                var statusText = '';
                                var eventClass = '';
                                if (now < start) {
                                    statusText = 'Comienza en: ' + moment(start).fromNow();
                                    eventClass = 'event-future';
                                } else if (now > end) {
                                    statusText = 'Terminó hace: ' + moment(end).fromNow();
                                    eventClass = 'event-past';
                                } else {
                                    statusText = 'Termina en: ' + moment(end).fromNow();
                                    eventClass = 'event-current';
                                }
                                document.getElementById('eventStatus').textContent = statusText;
                                
                                if (event.extendedProps.description != "false"){
                                    document.getElementById('eventDescription').textContent = 'Descripción: ' + event.extendedProps.description.substring(3, event.extendedProps.description.length-8);
                                } else {
                                    document.getElementById('eventDescription').textContent = 'Descripción: No se especificó una descripción';
                                }
                                if (event.extendedProps.location == "false"){
                                    document.getElementById('eventLocation').textContent = 'Ubicación: No se especificó una ubicación';
                                } else {
                                    document.getElementById('eventLocation').textContent = 'Ubicación: ' + event.extendedProps.location;
                                }
                                
                                modal.style.display = "block";
                                modal.querySelector('.modal-content').className = 'modal-content ' + eventClass;
                                
                                var statusInterval = setInterval(function() {
                                    var now = new Date();
                                    if (now < start) {
                                        statusText = 'Comienza en: ' + moment(start).fromNow();
                                    } else if (now > end) {
                                        statusText = 'Terminó hace: ' + moment(end).fromNow();
                                    } else {
                                        statusText = 'Termina en: ' + moment(end).fromNow();
                                    }
                                    document.getElementById('eventStatus').textContent = statusText;
                                }, 1000);
                                
                                span.onclick = function() {
                                    clearInterval(statusInterval);
                                    modal.style.display = "none";
                                }
                                
                                window.onclick = function(event) {
                                    if (event.target == modal) {
                                        clearInterval(statusInterval);
                                        modal.style.display = "none";
                                    }
                                }
                            }
                        });
                        
                        calendar.render();
                    });
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        webView.loadData("<html><body><h1>Error</h1><p>$message</p></body></html>", "text/html", "UTF-8")
    }
}