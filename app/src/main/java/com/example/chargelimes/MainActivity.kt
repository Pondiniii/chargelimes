package com.example.chargelimes

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chargelimes.ui.theme.ChargelimesTheme
import kotlinx.coroutines.flow.MutableStateFlow
import android.os.BatteryManager
import android.os.Build
import android.content.BroadcastReceiver
import android.content.IntentFilter

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val serviceIntent = Intent(context, BatteryService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val batteryInfo = MutableStateFlow(BatteryData(0, -1f))

    private val batteryStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
            batteryInfo.value = BatteryData(0, temperature.toFloat()) // Update battery info

        }
    }

    private lateinit var sharedPreferences: SharedPreferences

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver to prevent memory leaks
        unregisterReceiver(batteryStatusReceiver)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("com.example.chargelimes.PREFERENCES", Context.MODE_PRIVATE)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryStatusReceiver, filter)

        setContent {
            ChargelimesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BatteryControlPanel(BatteryService.isRunning)
                }
            }
        }
    }

    @Composable
    fun BatteryControlPanel(initialServiceEnabled: Boolean) {
        var serviceEnabled by remember { mutableStateOf(initialServiceEnabled) }
        var autoStartEnabled by remember {
            mutableStateOf(sharedPreferences.getBoolean("autoStartEnabled", false))
        }

        var chargeOffTemperature by remember {
            mutableStateOf(sharedPreferences.getFloat("chargeOffTemperature", 30f).toInt())
        }
        var chargeOffBatteryLevel by remember {
            mutableStateOf(sharedPreferences.getInt("chargeOffBatteryLevel", 20))
        }
        var chargeOnTemperature by remember {
            mutableStateOf(sharedPreferences.getFloat("chargeOnTemperature", 15f).toInt())
        }
        var chargeOnBatteryLevel by remember {
            mutableStateOf(sharedPreferences.getInt("chargeOnBatteryLevel", 85))
        }
        var httpOnUrl by remember { mutableStateOf(sharedPreferences.getString("httpOnUrl", "http://192.168.1.242/cm?cmnd=Power%20ON") ?: "") }
        var httpOffUrl by remember { mutableStateOf(sharedPreferences.getString("httpOffUrl", "http://192.168.1.242/cm?cmnd=Power%20OFF") ?: "") }


        LazyColumn {
            item {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Auto-Start at Boot:", modifier = Modifier.weight(1f))
                    Switch(
                        checked = autoStartEnabled,
                        onCheckedChange = { enabled ->
                            autoStartEnabled = enabled
                            sharedPreferences.edit().putBoolean("autoStartEnabled", enabled).apply()
                        }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Service Status:", modifier = Modifier.weight(1f))
                    Switch(
                        checked = serviceEnabled,
                        onCheckedChange = {
                            serviceEnabled = it
                            val intent = Intent(this@MainActivity, BatteryService::class.java)
                            if (it) {
                                startService(intent)
                            } else {
                                stopService(intent)
                            }
                        }
                    )
                }

                val data by batteryInfo.collectAsState()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Text(
                        text = "Battery Temperature:",
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${data.temperature}°C"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))


                Text("Turn off charging when temperature is >= ${chargeOffTemperature}°C or battery level is >= ${chargeOffBatteryLevel}%.")
                SliderWithValue(
                    value = chargeOffTemperature.toFloat(),
                    onValueChange = { value ->
                        chargeOffTemperature = value.toInt()
                        sharedPreferences.edit().putFloat("chargeOffTemperature", value).apply()
                    },
                    valueRange = 15f..40f,
                    steps = 25 // 1 step per degree
                )

                // Charge Off Battery Level
                SliderWithValue(
                    value = chargeOffBatteryLevel.toFloat(),
                    onValueChange = { value ->
                        chargeOffBatteryLevel = value.toInt()
                        sharedPreferences.edit()
                            .putInt("chargeOffBatteryLevel", chargeOffBatteryLevel).apply()
                    },
                    valueRange = 1f..100f,
                    steps = 99 // Integer steps
                )

                // Charge On Temperature
                Text("Turn on charging when temperature is <= ${chargeOnTemperature}°C and battery level is <= ${chargeOnBatteryLevel}%.")
                SliderWithValue(
                    value = chargeOnTemperature.toFloat(),
                    onValueChange = { value ->
                        chargeOnTemperature = value.toInt()
                        sharedPreferences.edit().putFloat("chargeOnTemperature", value).apply()
                    },
                    valueRange = 15f..40f,
                    steps = 25 // 1 step per degree
                )

                // Charge On Battery Level
                SliderWithValue(
                    value = chargeOnBatteryLevel.toFloat(),
                    onValueChange = { value ->
                        chargeOnBatteryLevel = value.toInt()
                        sharedPreferences.edit()
                            .putInt("chargeOnBatteryLevel", chargeOnBatteryLevel).apply()
                    },
                    valueRange = 1f..100f,
                    steps = 99 // Integer steps
                )

                OutlinedTextField(
                    value = httpOnUrl,
                    onValueChange = { value ->
                        httpOnUrl = value
                        sharedPreferences.edit().putString("httpOnUrl", value).apply()
                    },
                    label = { Text("HTTP On URL") }
                )

                OutlinedTextField(
                    value = httpOffUrl,
                    onValueChange = { value ->
                        httpOffUrl = value
                        sharedPreferences.edit().putString("httpOffUrl", value).apply()
                    },
                    label = { Text("HTTP Off URL") }
                )
            }
        }
    }

    @Composable
    fun SliderWithValue(
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int
    ) {
        Column {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}
