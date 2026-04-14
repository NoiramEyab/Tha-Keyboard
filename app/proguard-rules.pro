# =============================================================================
# proguard-rules.pro — Thai Keyboard
# Copyright (c) 2026 Marion Bayé. Tous droits réservés.
# =============================================================================

# ── OPTIMISATION ─────────────────────────────────────────────────────────────
-optimizationpasses 7
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively

# ── OBFUSCATION MAXIMALE ──────────────────────────────────────────────────────
# Obfusquer les noms de packages internes (sauf le package racine pour Android)
-repackageclasses 'a'
-flattenpackagehierarchy 'a'

# Supprimer les attributs de debug (rend la décompilation très difficile)
# ATTENTION : commenter si vous voulez des crash reports lisibles en prod
-renamesourcefileattribute 'x'
-keepattributes SourceFile,LineNumberTable

# ── CONSERVER — ANDROID OBLIGATOIRE ──────────────────────────────────────────
# Le service IME est instancié par le système via AndroidManifest — ne jamais obfusquer
-keep class com.example.thaikeyboard.ThaiKeyboardService { *; }
-keep class com.example.thaikeyboard.LauncherActivity { *; }
-keep class com.example.thaikeyboard.MainActivity { *; }
-keep class com.example.thaikeyboard.SettingsActivity { *; }

# Conserver les Fragments pour le PreferenceFragmentCompat
-keep class com.example.thaikeyboard.*Fragment { *; }
-keep class com.example.thaikeyboard.*Fragment$* { *; }

# ── CONSERVER — COMPOSE ───────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# ── CONSERVER — ENUM (Screen, etc.) ──────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final java.lang.String name();
    public final int ordinal();
}

# ── CONSERVER — RESSOURCES RÉFÉRENCÉES PAR getIdentifier() ───────────────────
# KeyboardView utilise getIdentifier() pour charger ic_keyboard_enter, ic_shift, etc.
-keepclassmembers class **.R$drawable {
    public static int ic_keyboard_enter;
    public static int ic_shift;
    public static int ic_globe;
    public static int ic_grid;
    public static int ic_preferences;
    public static int ic_languages;
    public static int ic_themes;
    public static int ic_correction;
    public static int ic_arrow_back;
}
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ── CONSERVER — GSON (sérialisation/désérialisation des fréquences) ───────────
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Préserver les types génériques utilisés avec TypeToken
-keepattributes Signature

# ── CONSERVER — ADMOB / GOOGLE PLAY SERVICES ─────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**

# ── CONSERVER — PREFERENCES ──────────────────────────────────────────────────
-keep class androidx.preference.** { *; }
-keep class * extends androidx.preference.Preference { *; }
-keep class * extends androidx.preference.PreferenceFragmentCompat { *; }

# ── SUPPRIMER LES LOGS EN PRODUCTION ─────────────────────────────────────────
# Supprime tous les logs de debug et verbose → ne laisse aucun indice à un attaquant
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# ── AVERTISSEMENTS À IGNORER ─────────────────────────────────────────────────
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn org.jetbrains.**
-dontwarn javax.annotation.**
