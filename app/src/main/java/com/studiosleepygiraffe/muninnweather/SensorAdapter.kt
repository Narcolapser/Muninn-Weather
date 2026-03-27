package com.studiosleepygiraffe.muninnweather

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.studiosleepygiraffe.muninnweather.databinding.ItemSensorBinding
import com.studiosleepygiraffe.muninnweather.network.HaClient

class SensorAdapter(
    private val onSelect: (HaClient.HaSensor) -> Unit
) : RecyclerView.Adapter<SensorAdapter.SensorViewHolder>() {
    private val allSensors = mutableListOf<HaClient.HaSensor>()
    private val visibleSensors = mutableListOf<HaClient.HaSensor>()

    fun submitList(items: List<HaClient.HaSensor>) {
        allSensors.clear()
        allSensors.addAll(items)
        applyFilter("")
    }

    fun applyFilter(query: String) {
        val needle = query.trim().lowercase()
        visibleSensors.clear()
        if (needle.isBlank()) {
            visibleSensors.addAll(allSensors)
        } else {
            visibleSensors.addAll(
                allSensors.filter {
                    it.name.lowercase().contains(needle) || it.entityId.lowercase().contains(needle)
                }
            )
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val binding = ItemSensorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SensorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        holder.bind(visibleSensors[position])
    }

    override fun getItemCount(): Int = visibleSensors.size

    inner class SensorViewHolder(private val binding: ItemSensorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(sensor: HaClient.HaSensor) {
            binding.sensorName.text = sensor.name
            binding.sensorId.text = sensor.entityId
            binding.root.setOnClickListener { onSelect(sensor) }
        }
    }
}
