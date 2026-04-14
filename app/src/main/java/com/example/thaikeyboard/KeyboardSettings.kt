/*
 * Copyright (c) 2026 Marion Bayé. Tous droits réservés.
 * Ce code source et ses ressources graphiques sont la propriété exclusive de Marion Bayé.
 * Toute reproduction, modification ou distribution non autorisée est strictement interdite.
 */
package com.example.thaikeyboard

import android.content.Context

object KeyboardSettings {

    private fun prefs(context: Context) =
        context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)

    // ===== VIBRATION =====
    fun isVibrationEnabled(context: Context): Boolean {
        return prefs(context).getBoolean("vibration_enabled", true)
    }

    fun setVibration(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("vibration_enabled", enabled).apply()
    }

    fun getVibrationIntensity(context: Context): Int {
        return prefs(context).getInt("vibration_intensity", 20)
    }

    fun setVibrationIntensity(context: Context, value: Int) {
        prefs(context).edit().putInt("vibration_intensity", value).apply()
    }

    // ===== SON =====
    fun isSoundEnabled(context: Context): Boolean {
        return prefs(context).getBoolean("sound_enabled", true)
    }

    fun setSound(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("sound_enabled", enabled).apply()
    }

    // ===== HAUTEUR (en pourcentage 0-100) =====
    fun getHeight(context: Context): Int {
        return prefs(context).getInt("keyboard_height_percent", 50)
    }

    fun setHeight(context: Context, value: Int) {
        prefs(context).edit().putInt("keyboard_height_percent", value).apply()
    }

    // ===== SUGGESTIONS =====
    fun isWordSuggestionsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean("pref_show_suggestions", true)
    }

    fun setWordSuggestionsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("pref_show_suggestions", enabled).apply()
    }

    // ===== APPUI LONG =====
    fun getLongPressTimeout(context: Context): Int {
        return prefs(context).getInt("long_press_timeout", 300)
    }

    fun setLongPressTimeout(context: Context, value: Int) {
        prefs(context).edit().putInt("long_press_timeout", value).apply()
    }

    // ===== DOUBLE ESPACE = POINT =====
    fun isDoubleSpaceToPeriodEnabled(context: Context): Boolean {
        return prefs(context).getBoolean("double_space_to_period", true)
    }

    fun setDoubleSpaceToPeriodEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("double_space_to_period", enabled).apply()
    }

    // ===== AUTO-CORRECTION =====
    fun isAutoCorrectionEnabled(context: Context): Boolean {
        return prefs(context).getBoolean("auto_correction_enabled", true)
    }

    fun setAutoCorrectionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("auto_correction_enabled", enabled).apply()
    }

    // ===== PREMIUM =====
    fun isPremium(context: Context): Boolean {
        return prefs(context).getBoolean("is_premium", false)
    }

    fun setPremium(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("is_premium", enabled).apply()
    }
}
