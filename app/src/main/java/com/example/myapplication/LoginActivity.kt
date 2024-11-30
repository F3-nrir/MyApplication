package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class LoginActivity : AppCompatActivity() {
    private lateinit var urlEditText: EditText
    private lateinit var databaseEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var loginProgressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        urlEditText = findViewById(R.id.urlEditText)
        databaseEditText = findViewById(R.id.databaseEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        loginProgressBar = findViewById(R.id.loginProgressBar)

        loginButton.setOnClickListener {
            val domain = urlEditText.text.toString()
            val database = databaseEditText.text.toString()
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (domain.isNotEmpty() && database.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                val url = if (domain.startsWith("http://") || domain.startsWith("https://")) domain else "https://$domain"
                login(url, database, username, password)
            } else {
                showError("Por favor, complete todos los campos")
            }
        }
    }

    private fun login(url: String, database: String, username: String, password: String) {
        loginProgressBar.visibility = View.VISIBLE
        loginButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val odooRepository = OdooRepository(url, database, username, password)
                val uid = odooRepository.authenticate()
                if (uid != null) {
                    saveLoginInfo(url, database, username, password)
                    startMainActivity()
                } else {
                    showError("La autenticación falló. Por favor, verifica tus credenciales.")
                }
            } catch (e: Exception) {
                when (e) {
                    is UnknownHostException -> showError("No se pudo conectar al servidor. Verifica la URL y tu conexión a internet.")
                    is SocketTimeoutException -> showError("La conexión al servidor ha expirado. Por favor, inténtalo de nuevo.")
                    is SSLHandshakeException -> showError("Error de seguridad en la conexión. Verifica que estés usando HTTPS para conexiones remotas.")
                    else -> showError("Error de conexión: ${e.message}")
                }
                Log.e("LoginActivity", "Login error", e)
            } finally {
                loginProgressBar.visibility = View.GONE
                loginButton.isEnabled = true
            }
        }
    }

    private fun saveLoginInfo(url: String, database: String, username: String, password: String) {
        val sharedPref = getSharedPreferences("OdooLogin", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("url", url)
            putString("database", database)
            putString("username", username)
            putString("password", password)
            putLong("last_login", System.currentTimeMillis())
            apply()
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}