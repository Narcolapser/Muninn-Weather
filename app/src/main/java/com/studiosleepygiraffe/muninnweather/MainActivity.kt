package com.studiosleepygiraffe.muninnweather

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.studiosleepygiraffe.muninnweather.data.WeatherStorage
import com.studiosleepygiraffe.muninnweather.databinding.ActivityMainBinding
import com.studiosleepygiraffe.muninnweather.worker.WorkerScheduler

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: WeatherStorage
    private lateinit var adapter: PacketAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = WeatherStorage(this)
        adapter = PacketAdapter()

        binding.packetList.layoutManager = LinearLayoutManager(this)
        binding.packetList.adapter = adapter

        binding.saveButton.setOnClickListener {
            val rawUrl = binding.haUrlInput.text?.toString()?.trim().orEmpty()
            val token = binding.haKeyInput.text?.toString()?.trim().orEmpty()

            if (rawUrl.isBlank() || token.isBlank()) {
                Toast.makeText(this, getString(R.string.missing_config), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (!rawUrl.startsWith("https://")) {
                Toast.makeText(this, getString(R.string.https_required), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val url = rawUrl.trimEnd('/')
            storage.saveConfig(url, token)
            WorkerScheduler.schedulePeriodic(this)
            WorkerScheduler.enqueueOneTime(this)
            showList()
            refreshPackets()
        }

        binding.refreshButton.setOnClickListener {
            WorkerScheduler.enqueueOneTime(this)
            refreshPackets()
        }

        if (storage.hasConfig()) {
            showList()
            refreshPackets()
        } else {
            showConfig()
        }
    }

    override fun onResume() {
        super.onResume()
        if (storage.hasConfig()) {
            refreshPackets()
        }
    }

    private fun refreshPackets() {
        adapter.submitList(storage.getPackets())
    }

    private fun showList() {
        binding.configContainer.visibility = View.GONE
        binding.listContainer.visibility = View.VISIBLE
    }

    private fun showConfig() {
        binding.configContainer.visibility = View.VISIBLE
        binding.listContainer.visibility = View.GONE
    }
}
