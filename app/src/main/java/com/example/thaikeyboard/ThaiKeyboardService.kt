/*
 * Copyright (c) 2026 Marion Bayé. Tous droits réservés.
 * Ce code source et ses ressources graphiques sont la propriété exclusive de Marion Bayé.
 * Toute reproduction, modification ou distribution non autorisée est strictement interdite.
 */
package com.example.thaikeyboard

import android.content.Context
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.google.android.gms.ads.MobileAds

class ThaiKeyboardService : InputMethodService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var keyboardView: KeyboardView? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var suggestionEngine: ThaiWordSuggestionEngine

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        prefs = getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        suggestionEngine = ThaiWordSuggestionEngine(this)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        return keyboardView!!
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyNavBarColor()
        keyboardView?.setEditorInfo(info)
        if (!restarting) {
            keyboardView?.resetState()
        }
        keyboardView?.refreshKeyboard(currentInputConnection)
        triggerSuggestions()
    }

    private fun applyNavBarColor() {
        val prefs = getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        val isPremium = KeyboardSettings.isPremium(this)
        val navColor = when (prefs.getString("theme", "light")) {
            "dark"       -> android.graphics.Color.parseColor("#1F1F1F")
            "night_blue" -> if (isPremium) android.graphics.Color.parseColor("#0D1B2A") else android.graphics.Color.parseColor("#F1F3F4")
            "rose"       -> if (isPremium) android.graphics.Color.parseColor("#FDF0F3") else android.graphics.Color.parseColor("#F1F3F4")
            "forest"     -> if (isPremium) android.graphics.Color.parseColor("#1A2B1F") else android.graphics.Color.parseColor("#F1F3F4")
            "violet"     -> if (isPremium) android.graphics.Color.parseColor("#1E1428") else android.graphics.Color.parseColor("#F1F3F4")
            else         -> android.graphics.Color.parseColor("#F1F3F4")
        }
        window?.window?.navigationBarColor = navColor
    }

    private fun triggerSuggestions() {
        if (!KeyboardSettings.isWordSuggestionsEnabled(this)) {
            keyboardView?.updateSuggestions(emptyList())
            return
        }
        val langId = keyboardView?.getCurrentLanguageId() ?: 0
        val textBefore = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString() ?: ""
        keyboardView?.updateSuggestions(suggestionEngine.getSuggestions(textBefore, langId))
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        keyboardView?.updateShiftStateByContext()

        // Détecter un espace inséré après un mot (curseur avance de 1)
        if (newSelStart == oldSelStart + 1) {
            val ic = currentInputConnection
            val textBefore = ic?.getTextBeforeCursor(30, 0)?.toString() ?: ""
            if (textBefore.endsWith(" ") && textBefore.length >= 2) {
                val withoutSpace = textBefore.dropLast(1)
                val langId = keyboardView?.getCurrentLanguageId() ?: 0

                val lastWord = when (langId) {
                    0 -> { // Thai
                        var wordLen = 0
                        for (i in withoutSpace.indices.reversed()) {
                            if (withoutSpace[i].code in 0x0E00..0x0E7F) wordLen++
                            else break
                        }
                        if (wordLen > 0) withoutSpace.takeLast(wordLen) else null
                    }
                    1, 2 -> { // French + English
                        var wordLen = 0
                        for (i in withoutSpace.indices.reversed()) {
                            val c = withoutSpace[i]
                            if (c.isLetter() || c == '\'') wordLen++
                            else break
                        }
                        if (wordLen > 0) withoutSpace.takeLast(wordLen) else null
                    }
                    else -> null
                }

                if (lastWord != null) {
                    // Apprentissage
                    suggestionEngine.recordWord(lastWord, null, langId)

                    // Autocorrection automatique (FR et EN uniquement)
                    if (langId == 1 || langId == 2) {
                        val correction = suggestionEngine.getAutoCorrection(lastWord, langId)
                        if (correction != null) {
                            // Remplacer : effacer le mot + espace, réécrire la correction + espace
                            ic?.deleteSurroundingText(lastWord.length + 1, 0)
                            ic?.commitText("$correction ", 1)
                        }
                    }
                }
            }
        }

        triggerSuggestions()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "font_style" || key == "theme" || key == "pref_show_suggestions" || key == "keyboard_height_percent") {
            applyNavBarColor()
            setInputView(onCreateInputView())
        }
    }
}
