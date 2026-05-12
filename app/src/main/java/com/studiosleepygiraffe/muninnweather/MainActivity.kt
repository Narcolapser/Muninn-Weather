package com.studiosleepygiraffe.muninnweather

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.studiosleepygiraffe.muninnweather.data.WeatherStorage
import com.studiosleepygiraffe.muninnweather.databinding.ActivityMainBinding
import com.studiosleepygiraffe.muninnweather.network.HaClient
import com.studiosleepygiraffe.muninnweather.network.OpenMeteoClient
import com.studiosleepygiraffe.muninnweather.worker.WorkerScheduler
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: WeatherStorage
    private lateinit var adapter: PacketAdapter
    private lateinit var sensorAdapter: SensorAdapter
    private var selectedSensor: HaClient.HaSensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = WeatherStorage(this)
        adapter = PacketAdapter()
        sensorAdapter = SensorAdapter { sensor ->
            selectedSensor = sensor
            binding.useSensorButton.isEnabled = true
        }

        if (storage.hasFullConfig()) {
            WorkerScheduler.schedulePeriodic(this, storage.getPollingIntervalMinutes())
        }

        binding.packetList.layoutManager = LinearLayoutManager(this)
        binding.packetList.adapter = adapter

        binding.sensorList.layoutManager = LinearLayoutManager(this)
        binding.sensorList.adapter = sensorAdapter

        binding.configureButton.setOnClickListener {
            prefillConfig()
            showConfig()
        }

        binding.batteryOptButton.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        binding.pollingIntervalButton.setOnClickListener {
            showPollingIntervalDialog()
        }

        binding.homeLocaleButton.setOnClickListener {
            showHomeLocaleDialog()
        }

        binding.continueButton.setOnClickListener {
            val rawUrl = binding.haUrlInput.text?.toString()?.trim().orEmpty()
            val token = binding.haKeyInput.text?.toString()?.trim().orEmpty()

            if (rawUrl.isBlank() || token.isBlank()) {
                Toast.makeText(this, getString(R.string.missing_config), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val url = rawUrl.trimEnd('/')
            storage.saveConfig(url, token)
            loadSensors()
        }

        binding.sensorFilterInput.addTextChangedListener { text ->
            sensorAdapter.applyFilter(text?.toString().orEmpty())
        }

        binding.useSensorButton.setOnClickListener {
            val chosen = selectedSensor
            if (chosen == null) {
                Toast.makeText(this, getString(R.string.sensor_required), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            storage.saveEntityId(chosen.entityId)
            updateConfigStatus()
            WorkerScheduler.schedulePeriodic(this, storage.getPollingIntervalMinutes())
            WorkerScheduler.enqueueOneTime(this)
            showHome()
            refreshPackets()
        }

        binding.refreshButton.setOnClickListener {
            WorkerScheduler.enqueueOneTime(this)
            refreshPackets()
        }

        showHome()
        updateConfigStatus()
        updateHomeLocaleStatus()
        updateCurrentLocaleStatus()
        if (storage.getHomeLocale() != null) {
            requestCoarseLocationPermissionIfNeeded()
        }
        refreshPackets()
    }

    override fun onResume() {
        super.onResume()
        updateCurrentLocaleStatus()
        if (storage.hasFullConfig()) {
            refreshPackets()
        }
    }

    private fun refreshPackets() {
        adapter.submitList(storage.getPackets())
    }

    private fun showHome() {
        binding.homeContainer.visibility = View.VISIBLE
        binding.configContainer.visibility = View.GONE
        binding.sensorContainer.visibility = View.GONE
    }

    private fun showConfig() {
        binding.homeContainer.visibility = View.GONE
        binding.configContainer.visibility = View.VISIBLE
        binding.sensorContainer.visibility = View.GONE
    }

    private fun showSensors() {
        binding.homeContainer.visibility = View.GONE
        binding.configContainer.visibility = View.GONE
        binding.sensorContainer.visibility = View.VISIBLE
    }

    private fun prefillConfig() {
        val config = storage.getConfig()
        binding.haUrlInput.setText(config?.url.orEmpty())
        binding.haKeyInput.setText(config?.token.orEmpty())
    }

    private fun updateConfigStatus() {
        val entity = storage.getEntityId()
        if (entity.isNullOrBlank()) {
            binding.configStatusText.text = getString(R.string.config_missing)
        } else {
            binding.configStatusText.text = getString(R.string.config_status, entity)
        }
    }

    private fun updateHomeLocaleStatus() {
        val homeLocale = storage.getHomeLocale()
        if (homeLocale == null) {
            binding.homeLocaleStatusText.text = getString(R.string.home_locale_missing)
        } else {
            binding.homeLocaleStatusText.text = getString(R.string.home_locale_status, homeLocale.name)
        }
    }

    private fun updateCurrentLocaleStatus() {
        val currentLocale = storage.getCurrentLocale()
        if (currentLocale == null) {
            binding.currentLocaleStatusText.text = getString(R.string.current_locale_unknown)
        } else {
            binding.currentLocaleStatusText.text = getString(R.string.current_locale_status, currentLocale.name)
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, getString(R.string.battery_optimization_already_ignored), Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun showPollingIntervalDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(storage.getPollingIntervalMinutes().toString())
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.polling_interval_title))
            .setMessage(getString(R.string.polling_interval_message))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val raw = input.text?.toString()?.trim().orEmpty()
                val minutes = raw.toIntOrNull()
                if (minutes == null || minutes < 1 || minutes > 60) {
                    Toast.makeText(this, getString(R.string.polling_interval_invalid), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                storage.savePollingIntervalMinutes(minutes)
                if (storage.hasFullConfig()) {
                    WorkerScheduler.schedulePeriodic(this, minutes)
                }
                if (minutes < WorkerScheduler.MIN_PERIODIC_MINUTES) {
                    Toast.makeText(this, getString(R.string.polling_interval_min_applied, WorkerScheduler.MIN_PERIODIC_MINUTES), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, getString(R.string.polling_interval_saved), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showHomeLocaleDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.home_locale_hint)
            setText(storage.getHomeLocale()?.name.orEmpty())
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.home_locale_title))
            .setMessage(getString(R.string.home_locale_message))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val query = input.text?.toString()?.trim().orEmpty()
                if (query.isBlank()) {
                    Toast.makeText(this, getString(R.string.home_locale_failed), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                saveHomeLocale(query)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveHomeLocale(query: String) {
        lifecycleScope.launch {
            val result = OpenMeteoClient().geocode(query)
            if (result == null) {
                Toast.makeText(this@MainActivity, getString(R.string.home_locale_failed), Toast.LENGTH_LONG).show()
                return@launch
            }
            storage.saveHomeLocale(
                WeatherStorage.HomeLocale(
                    name = result.name,
                    latitude = result.latitude,
                    longitude = result.longitude
                )
            )
            updateHomeLocaleStatus()
            requestCoarseLocationPermissionIfNeeded()
            Toast.makeText(this@MainActivity, getString(R.string.home_locale_saved, result.name), Toast.LENGTH_LONG).show()
        }
    }

    private fun requestCoarseLocationPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            requestBackgroundLocationPermissionIfNeeded()
            return
        }
        Toast.makeText(this, getString(R.string.location_permission_needed), Toast.LENGTH_LONG).show()
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_COARSE_LOCATION)
    }

    private fun requestBackgroundLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        Toast.makeText(this, getString(R.string.background_location_permission_needed), Toast.LENGTH_LONG).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            REQUEST_BACKGROUND_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_COARSE_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestBackgroundLocationPermissionIfNeeded()
        }
    }

    private fun loadSensors() {
        selectedSensor = null
        binding.useSensorButton.isEnabled = false
        binding.useSensorButton.text = getString(R.string.loading_sensors)

        val config = storage.getConfig()
        if (config == null) {
            Toast.makeText(this, getString(R.string.missing_config), Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            val sensors = HaClient().fetchSensors(config)
            if (sensors.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.config_failed), Toast.LENGTH_LONG).show()
                binding.useSensorButton.text = getString(R.string.use_sensor)
                return@launch
            }
            sensorAdapter.submitList(sensors)
            binding.useSensorButton.text = getString(R.string.use_sensor)
            showSensors()
        }
    }

    companion object {
        private const val REQUEST_COARSE_LOCATION = 1001
        private const val REQUEST_BACKGROUND_LOCATION = 1002
    }
}
