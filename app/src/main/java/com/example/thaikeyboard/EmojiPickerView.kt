/*
 * Copyright (c) 2026 Marion Bayé. Tous droits réservés.
 * Ce code source et ses ressources graphiques sont la propriété exclusive de Marion Bayé.
 * Toute reproduction, modification ou distribution non autorisée est strictement interdite.
 */
package com.example.thaikeyboard

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

class EmojiPickerView(
    context: Context,
    private val onEmojiClick: (String) -> Unit,
    private val themeColors: Map<String, Int>
) : LinearLayout(context) {

    private val adContainer = FrameLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        val dp4 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
        setPadding(0, dp4, 0, dp4)
        visibility = VISIBLE
    }

    private val emojiAdapter = EmojiAdapter { emoji ->
        addToRecent(emoji)
        onEmojiClick(emoji)
    }
    
    private val categoryAdapter = CategoryAdapter(EmojiProvider.categories) { categoryName ->
        filterByCategory(categoryName)
    }

    private val searchBar = EditText(context).apply {
        hint = "Rechercher un emoji..."
        setHintTextColor(themeColors["textSecondary"] ?: Color.GRAY)
        setTextColor(themeColors["textPrimary"] ?: Color.BLACK)
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(40, 20, 40, 20)
        maxLines = 1
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    }

    private val searchContainer: LinearLayout

    init {
        orientation = VERTICAL
        setBackgroundColor(themeColors["bgColor"] ?: Color.WHITE)

        // 0. Add Ad Container at the very top
        addView(adContainer)
        loadBannerAd()

        // 1. Category Bar
        val categoryList = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
            setBackgroundColor(Color.parseColor("#05000000"))
        }
        addView(categoryList)

        // 2. Search Bar (Hidden by default, below categories)
        searchContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            visibility = GONE
            val dp8 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp8, dp8, dp8, dp8)
            }
            setBackgroundColor(Color.parseColor("#10000000"))
        }
        searchContainer.addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(searchContainer)

        // 3. Emoji Grid (Fills remaining space)
        val emojiList = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            layoutManager = GridLayoutManager(context, 8)
            adapter = emojiAdapter
        }
        addView(emojiList)

        setupSearch()
        filterByCategory("Smileys")
    }

    private fun setupSearch() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase()
                if (query.isEmpty()) {
                    filterByCategory("Smileys")
                } else {
                    val filtered = EmojiProvider.allEmojis.filter { item ->
                        item.keywords.any { it.contains(query) } || item.emoji.contains(query)
                    }
                    emojiAdapter.updateEmojis(filtered)
                    categoryAdapter.setSelected("")
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun filterByCategory(categoryName: String) {
        if (categoryName == "Search") {
            searchContainer.visibility = VISIBLE
            searchBar.requestFocus()
            // On reste sur la catégorie précédente visuellement ou on vide
            return
        }
        
        searchContainer.visibility = GONE
        if (categoryName == "Recent") {
            emojiAdapter.updateEmojis(getRecentEmojis())
        } else {
            val filtered = EmojiProvider.allEmojis.filter { it.category == categoryName }
            emojiAdapter.updateEmojis(filtered)
        }
        categoryAdapter.setSelected(categoryName)
    }

    private fun addToRecent(emoji: String) {
        val prefs = context.getSharedPreferences("emoji_prefs", Context.MODE_PRIVATE)
        val recent = prefs.getString("recent_emojis", "")?.split(",")?.toMutableList() ?: mutableListOf()
        recent.remove(emoji)
        recent.add(0, emoji)
        if (recent.size > 20) recent.removeAt(recent.size - 1)
        prefs.edit().putString("recent_emojis", recent.joinToString(",")).apply()
    }

    private fun getRecentEmojis(): List<EmojiItem> {
        val prefs = context.getSharedPreferences("emoji_prefs", Context.MODE_PRIVATE)
        val recentString = prefs.getString("recent_emojis", "") ?: ""
        if (recentString.isEmpty()) return emptyList()
        return recentString.split(",").filter { it.isNotEmpty() }.map { EmojiItem(it, "Recent", emptyList()) }
    }

    private var adView: AdView? = null

    private fun loadBannerAd() {
        if (KeyboardSettings.isPremium(context)) {
            adContainer.visibility = GONE
            return
        }

        // Configuration Adaptive Banner selon les recommandations Google AdMob
        val displayMetrics = resources.displayMetrics
        val adWidthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)

        adView = AdView(context).apply {
            adUnitId = "ca-app-pub-3940256099942544/9214589741"
            setAdSize(adaptiveSize)
            
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    adContainer.visibility = VISIBLE
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    adContainer.visibility = GONE
                }
            }
        }

        adContainer.removeAllViews()
        adView?.let {
            adContainer.addView(it)
            it.loadAd(AdRequest.Builder().build())
        }
    }

    fun destroyAd() {
        adView?.destroy()
        adView = null
    }
}
