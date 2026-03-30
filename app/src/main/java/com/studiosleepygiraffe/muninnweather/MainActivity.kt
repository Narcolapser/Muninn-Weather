package com.studiosleepygiraffe.muninnweather

import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.net.Uri
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.studiosleepygiraffe.muninnweather.data.WeatherStorage
import com.studiosleepygiraffe.muninnweather.databinding.ActivityMainBinding
import com.studiosleepygiraffe.muninnweather.network.HaClient
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
        refreshPackets()
    }

    override fun onResume() {
        super.onResume()
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
}
