package com.example.chargelimes

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.flow.MutableStateFlow
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.os.Build
import android.util.Log

class BatteryService : Service() {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var lastAction: String? = null
    private var previousBatteryData = BatteryData(-1, -1f)

    companion object {
        @Volatile
        var isRunning = false
        private const val CHANNEL_ID = "batteryServiceChannel"
    }

    private val batteryStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val batteryLevel = level * 100 / scale
            val batteryTemperature = temperature / 10.0f

            val currentBatteryData = BatteryData(batteryLevel, batteryTemperature)
            if (currentBatteryData != previousBatteryData) {
                handleBatteryChange(currentBatteryData)
                previousBatteryData = currentBatteryData
            }

        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Background Service Running")
            .setContentText("Battery monitoring is active.")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()

        startForeground(1, notification)

        registerReceiver(batteryStatusReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun handleBatteryChange(currentBatteryData: BatteryData) {
        coroutineScope.launch {
            val sharedPreferences = getSharedPreferences("com.example.chargelimes.PREFERENCES", Context.MODE_PRIVATE)
            val chargeOffTemperature = sharedPreferences.getFloat("chargeOffTemperature", 30f)
            val chargeOffBatteryLevel = sharedPreferences.getInt("chargeOffBatteryLevel", 20)
            val chargeOnTemperature = sharedPreferences.getFloat("chargeOnTemperature", 15f)
            val chargeOnBatteryLevel = sharedPreferences.getInt("chargeOnBatteryLevel", 80)

            val httpOnUrl = sharedPreferences.getString("httpOnUrl", "http://192.168.1.242/cm?cmnd=Power%20ON")
            val httpOffUrl = sharedPreferences.getString("httpOffUrl", "http://192.168.1.242/cm?cmnd=Power%20OFF")

            when {
                currentBatteryData.temperature >= chargeOffTemperature || currentBatteryData.level >= chargeOffBatteryLevel -> {
                    if (lastAction != "OFF") {
                        sendHttpGetRequest(httpOffUrl)
                        lastAction = "OFF"
                    }
                }
                currentBatteryData.temperature <= chargeOnTemperature && currentBatteryData.level <= chargeOnBatteryLevel -> {
                    if (lastAction != "ON") {
                        sendHttpGetRequest(httpOnUrl)
                        lastAction = "ON"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        unregisterReceiver(batteryStatusReceiver)
        coroutineScope.cancel()
        super.onDestroy()
    }

    private fun sendHttpGetRequest(url: String?) {
        url?.let {
            coroutineScope.launch {
                try {
                    val request = Request.Builder().url(it).build()
                    val client = OkHttpClient()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.e("HttpError", "Failed to execute request: ${response.message}")
                        } else {
//                             Process the response if needed
//                           val responseBody = response.body?.string()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HttpError", "Error during HTTP request: ${e.message}")
                }
            }
        }
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Battery Monitoring Service", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

