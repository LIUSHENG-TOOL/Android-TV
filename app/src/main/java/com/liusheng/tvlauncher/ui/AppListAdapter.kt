package com.liusheng.tvlauncher.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liusheng.tvlauncher.data.AppEntry
import com.liusheng.tvlauncher.databinding.ItemAppListBinding

/** 已安装应用网格的展示和点击回调。 */
class AppListAdapter(
    private val inflater: LayoutInflater,
    private val onActivate: (AppEntry) -> Unit,
    private val onLongActivate: (AppEntry, View) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private val items = ArrayList<AppEntry>()

    fun submit(apps: List<AppEntry>) {
        items.clear()
        items.addAll(apps)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppListBinding.inflate(inflater, parent, false)
        val holder = AppViewHolder(binding)

        FocusEffect.apply(
            view = binding.root,
            onClick = { clickPosition(holder)?.let(onActivate) }
        )
        binding.root.setOnLongClickListener {
            clickPosition(holder)?.let { entry -> onLongActivate(entry, binding.root) }
            true
        }
        return holder
    }

    private fun clickPosition(holder: AppViewHolder): AppEntry? {
        val position = holder.bindingAdapterPosition
        return if (position in items.indices) items[position] else null
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val entry = items[position]
        holder.binding.itemIcon.setImageDrawable(entry.icon)
        holder.binding.itemLabel.text = entry.label
        holder.binding.itemLabel.typeface = Typeface.DEFAULT_BOLD
    }

    override fun getItemCount(): Int = items.size

    class AppViewHolder(val binding: ItemAppListBinding) : RecyclerView.ViewHolder(binding.root)
}
