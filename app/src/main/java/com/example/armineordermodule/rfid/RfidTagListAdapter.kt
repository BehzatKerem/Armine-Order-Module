package com.example.armineordermodule.rfid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.armineordermodule.R

/** Ekranda gösterilen, tekillestirilmis (EPC bazli) taranmis etiket bilgisi. */
data class ScannedTagUi(
    val epcId: String,
    val rssi: Int,
    val antennaMask: Int,
    val readCount: Int,
)

class RfidTagListAdapter : RecyclerView.Adapter<RfidTagListAdapter.ViewHolder>() {

    private val items = mutableListOf<ScannedTagUi>()

    fun submitList(tags: List<ScannedTagUi>) {
        items.clear()
        items.addAll(tags.sortedByDescending { it.readCount })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rfid_tag, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEpc: TextView = itemView.findViewById(R.id.tvTagEpc)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvTagDetails)

        fun bind(tag: ScannedTagUi) {
            tvEpc.text = tag.epcId
            tvDetails.text = itemView.context.getString(
                R.string.rfid_tag_details, tag.rssi, tag.antennaMask, tag.readCount
            )
        }
    }
}
