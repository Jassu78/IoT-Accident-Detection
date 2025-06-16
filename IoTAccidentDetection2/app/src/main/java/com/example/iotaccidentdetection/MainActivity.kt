package com.example.iotaccidentdetection

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.iotaccidentdetection.ui.theme.IoTAccidentDetectionTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isAccidentDetected = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var emergencyContact1: String? = null
    private var emergencyContact2: String? = null

    private val LOCATION_PERMISSION_CODE = 1000
    private val SMS_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SensorManager and Accelerometer
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Check for necessary permissions
        checkPermissions()

        setContent {
            IoTAccidentDetectionTheme {
                AppContent(
                    onSaveContacts = { contact1, contact2 ->
                        emergencyContact1 = contact1
                        emergencyContact2 = contact2
                    },
                    onStartMonitoring = {
                        startAccidentDetection()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Register accelerometer listener
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister listener to save battery
        sensorManager.unregisterListener(this)
    }

    @SuppressLint("MissingPermission")
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            // Calculate the magnitude of acceleration
            val acceleration = sqrt(x * x + y * y + z * z)
            Log.d("Accelerometer", "Acceleration: $acceleration")

            // Detect sudden change in acceleration (threshold is adjustable)
            if (acceleration > 20 && !isAccidentDetected) { // Example threshold: 20 m/s²
                isAccidentDetected = true
                onAccidentDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used in this implementation
    }

    private fun checkPermissions() {
        // Check if location permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_CODE)
        }

        // Check if SMS permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun onAccidentDetected() {
        // Check if location permissions are granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission is not granted", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                sendAccidentAlert(location)
            } else {
                // Handle case where location is unavailable
                Toast.makeText(this, "Failed to retrieve location. Trying again...", Toast.LENGTH_SHORT).show()
                requestFreshLocation()
            }
        }.addOnFailureListener { exception ->
            Log.e("LocationError", "Failed to get last known location", exception)
            Toast.makeText(this, "Failed to retrieve location. Error: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation() {
        val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 500
            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
            numUpdates = 1 // Request a single update
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    val freshLocation = locationResult.lastLocation
                    if (freshLocation != null) {
                        sendAccidentAlert(freshLocation)
                    } else {
                        Toast.makeText(applicationContext, "Still unable to retrieve location.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            null
        )
    }


    private fun sendAccidentAlert(location: Location) {
        val message =
            "Accident detected! \nLocation: https://maps.google.com/?q=${location.latitude}%2C${location.longitude}"

        emergencyContact1?.let {
            SmsManager.getDefault().sendTextMessage(it, null, message, null, null)
            Log.d("SMS", "Sent to $it")
        }
        emergencyContact2?.let {
            SmsManager.getDefault().sendTextMessage(it, null, message, null, null)
            Log.d("SMS", "Sent to $it")
        }

        Toast.makeText(this, "Accident alert sent!", Toast.LENGTH_SHORT).show()

        // Reset accident detection flag after sending alert
        isAccidentDetected = false
    }

    private fun startAccidentDetection() {
        // Register accelerometer listener when user starts monitoring
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        Log.d("AccidentDetection", "Accident monitoring started.")
        Toast.makeText(this, "Accident monitoring started.", Toast.LENGTH_SHORT).show()
    }

    // Handle the permissions request result
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Location Permission Granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
            SMS_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "SMS Permission Granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "SMS Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(
    onSaveContacts: (String, String) -> Unit,
    onStartMonitoring: () -> Unit
) {
    var emergencyContact1 by remember { mutableStateOf("") }
    var emergencyContact2 by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("IoT Accident Detection") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text("Enter Emergency Contacts", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = emergencyContact1,
                onValueChange = { emergencyContact1 = it },
                label = { Text("Emergency Contact 1") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = emergencyContact2,
                onValueChange = { emergencyContact2 = it },
                label = { Text("Emergency Contact 2") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { onSaveContacts(emergencyContact1, emergencyContact2) }) {
                Text("Save Contacts")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { onStartMonitoring() }) {
                Text("Start Monitoring")
            }
        }
    }
}
