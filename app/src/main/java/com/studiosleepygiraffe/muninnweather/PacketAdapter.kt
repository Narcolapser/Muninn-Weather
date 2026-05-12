package com.studiosleepygiraffe.muninnweather

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.studiosleepygiraffe.muninnweather.data.WeatherPacket
import com.studiosleepygiraffe.muninnweather.data.WeatherSource
import com.studiosleepygiraffe.muninnweather.databinding.ItemPacketBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PacketAdapter : RecyclerView.Adapter<PacketAdapter.PacketViewHolder>() {
    private val packets = mutableListOf<WeatherPacket>()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun submitList(items: List<WeatherPacket>) {
        packets.clear()
        packets.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PacketViewHolder {
        val binding = ItemPacketBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PacketViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PacketViewHolder, position: Int) {
        holder.bind(packets[position])
    }

    override fun getItemCount(): Int = packets.size

    inner class PacketViewHolder(private val binding: ItemPacketBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(packet: WeatherPacket) {
            val date = Date(packet.timestampMillis)
            val formatted = formatter.format(date)
            binding.packetTime.text = binding.root.context.getString(
                R.string.recorded_at,
                formatted
            )
            binding.packetTemp.text = String.format(Locale.US, "%.1f %s", packet.temperature, packet.unit)
            val sourceName = when (packet.source) {
                WeatherSource.HOME_ASSISTANT -> binding.root.context.getString(R.string.source_home_assistant)
                WeatherSource.WEATHER_API -> binding.root.context.getString(R.string.source_weather_api)
            }
            binding.packetSource.text = binding.root.context.getString(
                R.string.packet_source,
                sourceName,
                packet.locationName,
                packet.condition
            )
        }
    }
}
