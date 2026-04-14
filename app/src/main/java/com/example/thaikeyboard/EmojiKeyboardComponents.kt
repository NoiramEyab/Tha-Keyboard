/*
 * Copyright (c) 2026 Marion Bayé. Tous droits réservés.
 * Ce code source et ses ressources graphiques sont la propriété exclusive de Marion Bayé.
 * Toute reproduction, modification ou distribution non autorisée est strictement interdite.
 */
package com.example.thaikeyboard

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmojiAdapter(
    private val onEmojiClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    private var emojis: List<EmojiItem> = emptyList()

    fun updateEmojis(newEmojis: List<EmojiItem>) {
        emojis = newEmojis
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val size = parent.context.resources.displayMetrics.widthPixels / 8
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Color.BLACK)
        }
        return EmojiViewHolder(tv)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val item = emojis[position]
        (holder.itemView as TextView).text = item.emoji
        holder.itemView.setOnClickListener { onEmojiClick(item.emoji) }
    }

    override fun getItemCount(): Int = emojis.size

    class EmojiViewHolder(view: View) : RecyclerView.ViewHolder(view)
}

class CategoryAdapter(
    private val categories: List<EmojiProvider.EmojiCategory>,
    private val onCategoryClick: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var selectedCategory: String = "Smileys"

    fun setSelected(category: String) {
        selectedCategory = category
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(30, 0, 30, 0)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }
        return CategoryViewHolder(tv)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val cat = categories[position]
        (holder.itemView as TextView).apply {
            text = cat.icon
            alpha = if (selectedCategory == cat.name) 1.0f else 0.5f
            setOnClickListener { 
                selectedCategory = cat.name
                onCategoryClick(cat.name)
                notifyDataSetChanged()
            }
        }
    }

    override fun getItemCount(): Int = categories.size

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
