/*
 * Copyright (c) 2026 Marion Bayé. Tous droits réservés.
 * Ce code source et ses ressources graphiques sont la propriété exclusive de Marion Bayé.
 * Toute reproduction, modification ou distribution non autorisée est strictement interdite.
 */
package com.example.thaikeyboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            val target = intent.getStringExtra("target_fragment")
            val fragment = when (target) {
                "languages" -> LanguagesFragment()
                else -> MainSettingsFragment()
            }
            
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, fragment)
                .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            finish()
        }
        return true
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
        return true
    }
}

class MainSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "keyboard_settings"
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Setup navigation for each item
        findPreference<Preference>("languages")?.fragment = LanguagesFragment::class.java.name
        findPreference<Preference>("preferences")?.fragment = PreferencesFragment::class.java.name
        findPreference<Preference>("themes")?.fragment = ThemesFragment::class.java.name
        findPreference<Preference>("correction")?.fragment = CorrectionFragment::class.java.name
    }
}

class LanguagesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "keyboard_settings"
        setPreferencesFromResource(R.xml.pref_languages, rootKey)
    }
}

class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "keyboard_settings"
        setPreferencesFromResource(R.xml.pref_preferences, rootKey)
    }
}

class ThemesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "keyboard_settings"
        setPreferencesFromResource(R.xml.pref_themes, rootKey)
    }
}

class CorrectionFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "keyboard_settings"
        setPreferencesFromResource(R.xml.pref_correction, rootKey)
    }
}