package com.statussaver.vault.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.statussaver.vault.R
import com.statussaver.vault.databinding.ItemStatusBinding
import com.statussaver.vault.model.StatusItem

class StatusAdapter(
    private val onItemClick: (StatusItem) -> Unit,
    private val onSaveClick: (StatusItem) -> Unit,
    private val onShareClick: (StatusItem) -> Unit
) : ListAdapter<StatusItem, StatusAdapter.StatusVH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusVH {
        val b = ItemStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StatusVH(b)
    }

    override fun onBindViewHolder(holder: StatusVH, position: Int) =
        holder.bind(getItem(position))

    inner class StatusVH(private val b: ItemStatusBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: StatusItem) {
            val ctx = b.root.context

            // Thumbnail
            Glide.with(ctx)
                .load(item.uri)
                .centerCrop()
                .placeholder(R.color.placeholder_color)
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(b.ivThumbnail)

            // Video elements
            if (item.isVideo) {
                b.ivPlayOverlay.visibility = View.VISIBLE
                b.tvDuration.visibility   = View.VISIBLE
                b.tvDuration.text         = item.duration.ifEmpty { "0:00" }
            } else {
                b.ivPlayOverlay.visibility = View.GONE
                b.tvDuration.visibility   = View.GONE
            }

            // Already-saved badge
            if (item.isSaved) {
                b.ivSavedBadge.visibility = View.VISIBLE
                b.btnSave.visibility      = View.GONE
            } else {
                b.ivSavedBadge.visibility = View.GONE
                b.btnSave.visibility      = View.VISIBLE
            }

            // Clicks
            b.root.setOnClickListener    { onItemClick(item) }
            b.btnSave.setOnClickListener { onSaveClick(item) }
            b.btnShare.setOnClickListener{ onShareClick(item) }
        }
    }

    private class Diff : DiffUtil.ItemCallback<StatusItem>() {
        override fun areItemsTheSame(old: StatusItem, new: StatusItem) = old.uri == new.uri
        override fun areContentsTheSame(old: StatusItem, new: StatusItem) = old == new
    }
}