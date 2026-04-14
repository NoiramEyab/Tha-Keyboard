/*
 * Copyright (c) 2026 Marion Bayé. Tous droits réservés.
 * Ce code source et ses ressources graphiques sont la propriété exclusive de Marion Bayé.
 * Toute reproduction, modification ou distribution non autorisée est strictement interdite.
 */
package com.example.thaikeyboard

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsNavigation(onExit = { finish() })
                }
            }
        }
    }
}

enum class Screen { MAIN, LANGUAGES, PREFERENCES, THEME, CORRECTION, PREMIUM, LEGAL }

@Composable
fun BannerAdView() {
    val context = LocalContext.current
    if (KeyboardSettings.isPremium(context)) return

    val adView = remember {
        AdView(context).apply {
            adUnitId = "ca-app-pub-1813285379775825/8994366413"

            val displayMetrics = context.resources.displayMetrics
            val adWidthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, adWidthDp))
            
            loadAd(AdRequest.Builder().build())
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { adView }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNavigation(onExit: () -> Unit) {
    val context = LocalContext.current
    val intent = (context as? ComponentActivity)?.intent
    val startScreen = remember {
        val screenName = intent?.getStringExtra("screen")
        if (screenName != null) {
            try { Screen.valueOf(screenName) } catch (e: Exception) { Screen.MAIN }
        } else {
            Screen.MAIN
        }
    }
    var currentScreen by remember { mutableStateOf(startScreen) }
    val prefs = remember { context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }

    // Utilisation d'un Int pour l'intensité (0 = désactivé)
    var vibration by remember { mutableIntStateOf(prefs.getInt("vibration_intensity", 20)) }
    var heightPercent by remember { mutableIntStateOf(prefs.getInt("keyboard_height_percent", 50)) }
    var longPressDelay by remember { mutableIntStateOf(prefs.getInt("long_press_timeout", 300)) }
    var doubleSpaceToPeriod by remember { mutableStateOf(prefs.getBoolean("double_space_to_period", true)) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "light") ?: "light") }

    BackHandler {
        if (currentScreen == Screen.MAIN) {
            onExit()
        } else {
            currentScreen = Screen.MAIN
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentScreen) {
                            Screen.MAIN -> "Paramètres"
                            Screen.LANGUAGES -> "Langues"
                            Screen.PREFERENCES -> "Préférences"
                            Screen.THEME -> "Thème"
                            Screen.CORRECTION -> "Correction du texte"
                            Screen.PREMIUM -> "✨ Premium"
                            Screen.LEGAL -> "Mentions Légales"
                        },
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentScreen == Screen.MAIN) onExit() else currentScreen = Screen.MAIN
                    }) {
                        Text("←", fontSize = 24.sp)
                    }
                }
            )
        },
        bottomBar = {
            BannerAdView()
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (currentScreen) {
                Screen.MAIN -> MainMenu { currentScreen = it }
                Screen.LANGUAGES -> LanguagesScreen()
                Screen.PREFERENCES -> PreferencesScreen(
                    vibrationStrength = vibration,
                    heightPercent = heightPercent,
                    longPressDelay = longPressDelay,
                    doubleSpaceToPeriod = doubleSpaceToPeriod,
                    onVibrationChange = {
                        vibration = it
                        prefs.edit { putInt("vibration_intensity", it) }
                    },
                    onHeightChange = {
                        heightPercent = it
                        prefs.edit { putInt("keyboard_height_percent", it) }
                    },
                    onLongPressDelayChange = {
                        longPressDelay = it
                        prefs.edit { putInt("long_press_timeout", it) }
                    },
                    onDoubleSpaceChange = {
                        doubleSpaceToPeriod = it
                        prefs.edit { putBoolean("double_space_to_period", it) }
                    }
                )
                Screen.THEME -> ThemeScreen(theme) {
                    theme = it
                    prefs.edit { putString("theme", it) }
                }
                Screen.CORRECTION -> CorrectionScreen()
                Screen.PREMIUM -> PremiumScreen { currentScreen = Screen.MAIN }
                Screen.LEGAL -> LegalScreen()
            }
        }
    }
}

@Composable
fun MainMenu(onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    val isPremium = prefs.getBoolean("is_premium", false)
    var consonantColorEnabled by remember { mutableStateOf(prefs.getBoolean("pref_color_consonants", true)) }

    val menuItems = listOf(
        Triple("🌐", "Langues", Screen.LANGUAGES),
        Triple("⚙️", "Préférences", Screen.PREFERENCES),
        Triple("🎨", "Thèmes", Screen.THEME),
        Triple("🪄", "Corrections et suggestions", Screen.CORRECTION),
        Triple("✨", "Premium", Screen.PREMIUM),
        Triple("📜", "Mentions Légales", Screen.LEGAL)
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Paramètres du clavier",
                modifier = Modifier.padding(16.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Affichage des premiers éléments jusqu'à Correction
        items(menuItems.take(4)) { (icon, title, screen) ->
            ListItem(
                leadingContent = { Text(icon, fontSize = 22.sp) },
                headlineContent = { Text(title, fontSize = 16.sp) },
                modifier = Modifier.clickable { onNavigate(screen) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = Color.LightGray.copy(alpha = 0.5f)
            )
        }

        // Nouvelle option : Couleur des consonnes
        item {
            val alphaValue = if (isPremium) 1f else 0.5f
            ListItem(
                headlineContent = { Text("Couleur des consonnes", fontSize = 16.sp) },
                supportingContent = if (!isPremium) {
                    { Text("(Option Premium)", fontSize = 12.sp, color = Color.Gray) }
                } else null,
                trailingContent = {
                    Switch(
                        checked = consonantColorEnabled,
                        onCheckedChange = {
                            if (isPremium) {
                                consonantColorEnabled = it
                                prefs.edit().putBoolean("pref_color_consonants", it).apply()
                            }
                        },
                        enabled = isPremium
                    )
                },
                modifier = Modifier
                    .alpha(alphaValue)
                    .clickable(enabled = isPremium) {
                        consonantColorEnabled = !consonantColorEnabled
                        prefs.edit().putBoolean("pref_color_consonants", consonantColorEnabled).apply()
                    }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = Color.LightGray.copy(alpha = 0.5f)
            )
        }

        // Affichage des éléments restants (Premium et Mentions Légales)
        items(menuItems.drop(4)) { (icon, title, screen) ->
            ListItem(
                leadingContent = { Text(icon, fontSize = 22.sp) },
                headlineContent = { Text(title, fontSize = 16.sp) },
                modifier = Modifier.clickable { onNavigate(screen) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = Color.LightGray.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun PreferencesScreen(
    vibrationStrength: Int,
    heightPercent: Int,
    longPressDelay: Int,
    doubleSpaceToPeriod: Boolean,
    onVibrationChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onLongPressDelayChange: (Int) -> Unit,
    onDoubleSpaceChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Touches", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Vibration au toucher", fontSize = 16.sp)
            Text(
                if (vibrationStrength == 0) "Désactivée" else "$vibrationStrength ms",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Slider(
                value = vibrationStrength.toFloat(),
                onValueChange = { onVibrationChange(it.toInt()) },
                valueRange = 0f..50f,
                steps = 10
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Délai de l'appui long", fontSize = 16.sp)
            Text("$longPressDelay ms", fontSize = 14.sp, color = Color.Gray)
            Slider(
                value = longPressDelay.toFloat(),
                onValueChange = { onLongPressDelayChange(it.toInt()) },
                valueRange = 100f..500f,
                steps = 10
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Hauteur du clavier", fontSize = 16.sp)
            Text("${heightPercent}%", fontSize = 14.sp, color = Color.Gray)
            Slider(
                value = heightPercent.toFloat(),
                onValueChange = { onHeightChange(it.toInt()) },
                valueRange = 20f..100f
            )
        }

        ListItem(
            headlineContent = { Text("Point et espace", fontSize = 16.sp) },
            supportingContent = { Text("Appuyez deux fois sur la barre d'espace pour ajouter un point suivi d'un espace", fontSize = 14.sp) },
            trailingContent = {
                Switch(checked = doubleSpaceToPeriod, onCheckedChange = onDoubleSpaceChange)
            },
            modifier = Modifier.clickable { onDoubleSpaceChange(!doubleSpaceToPeriod) }.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))


    }
}

@Composable
fun ThemeScreen(currentTheme: String, onThemeChange: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("keyboard_settings", android.content.Context.MODE_PRIVATE) }
    var isPremium by remember { mutableStateOf(prefs.getBoolean("is_premium", false)) }

    val freeThemes = listOf(
        "light"  to "☀️  Clair",
        "dark"   to "🌙  Sombre"
    )
    val premiumThemes = listOf(
        "night_blue" to "🌊  Bleu Nuit",
        "rose"       to "🌸  Rose Pâle",
        "forest"     to "🌿  Vert Forêt",
        "violet"     to "🔮  Violet"
    )

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Thèmes gratuits", fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp))

        freeThemes.forEach { (id, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onThemeChange(id) }.padding(vertical = 8.dp)
            ) {
                RadioButton(selected = (currentTheme == id), onClick = { onThemeChange(id) })
                Text(label, modifier = Modifier.padding(start = 12.dp), fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("Thèmes Premium", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            if (!isPremium) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("🔒", fontSize = 16.sp)
            }
        }

        premiumThemes.forEach { (id, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .clickable(enabled = isPremium) { if (isPremium) onThemeChange(id) }
                    .padding(vertical = 8.dp)
            ) {
                RadioButton(
                    selected = (currentTheme == id),
                    onClick = { if (isPremium) onThemeChange(id) },
                    enabled = isPremium
                )
                Text(
                    label,
                    modifier = Modifier.padding(start = 12.dp),
                    fontSize = 16.sp,
                    color = if (isPremium) Color.Unspecified else Color.Gray
                )
                if (!isPremium) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Premium", fontSize = 12.sp,
                        color = Color(0xFFFFAA00),
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!isPremium) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Débloquer les thèmes Premium dans ✨ Premium",
                fontSize = 13.sp, color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun PremiumScreen(onPremiumActivated: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("keyboard_settings", android.content.Context.MODE_PRIVATE) }
    var isPremium by remember { mutableStateOf(prefs.getBoolean("is_premium", false)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✨", fontSize = 56.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Text("Thai Keyboard Premium", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp))
        Text("Débloquez toutes les fonctionnalités", fontSize = 14.sp, color = Color.Gray,
            modifier = Modifier.padding(bottom = 32.dp))

        // Features list
        val features = listOf(
            "🎨" to "4 thèmes exclusifs (Bleu Nuit, Rose, Forêt, Violet)",
            "🇹🇭" to "Codes couleurs des consonnes thaïes\n(haute = rouge, moyenne = vert, basse = bleu)",
            "🚫" to "Sans publicité",
            "⭐" to "Accès prioritaire aux nouvelles fonctionnalités"
        )
        features.forEach { (icon, desc) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(icon, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
                Text(desc, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!isPremium) {
            Button(
                onClick = {
                    // TODO: Configurer Google Play Billing pour un abonnement récurrent mensuel (sku_premium_subs)
                    // Pour l'instant : activation directe (démo)
                    prefs.edit().putBoolean("is_premium", true).apply()
                    isPremium = true
                    onPremiumActivated()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Activer Premium", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Accès Premium pour 0€99/mois", fontSize = 12.sp, color = Color.Gray)
        } else {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("✅  Premium activé", fontSize = 17.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    prefs.edit().putBoolean("is_premium", false).apply()
                    isPremium = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Désactiver (test)", fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun LanguagesScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }

    var thEnabled by remember { mutableStateOf(prefs.getBoolean("pref_lang_th", true)) }
    var frEnabled by remember { mutableStateOf(prefs.getBoolean("pref_lang_fr", true)) }
    var enEnabled by remember { mutableStateOf(prefs.getBoolean("pref_lang_en", true)) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Claviers installés", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))

        LanguageItem(
            name = "Thaï",
            desc = "Romanisation & Codes Couleurs",
            flag = "🇹🇭",
            checked = thEnabled,
            onCheckedChange = {
                thEnabled = it
                prefs.edit().putBoolean("pref_lang_th", it).apply()
            }
        )
        LanguageItem(
            name = "Français",
            desc = "AZERTY",
            flag = "🇫🇷",
            checked = frEnabled,
            onCheckedChange = {
                frEnabled = it
                prefs.edit().putBoolean("pref_lang_fr", it).apply()
            }
        )
        LanguageItem(
            name = "Anglais",
            desc = "QWERTY",
            flag = "🇺🇸",
            checked = enEnabled,
            onCheckedChange = {
                enEnabled = it
                prefs.edit().putBoolean("pref_lang_en", it).apply()
            }
        )
    }
}

@Composable
fun LanguageItem(name: String, desc: String, flag: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(desc) },
        leadingContent = { Text(flag, fontSize = 24.sp) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun CorrectionScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE) }
    var showSuggestions by remember { mutableStateOf(prefs.getBoolean("pref_show_suggestions", true)) }
    var autoCaps by remember { mutableStateOf(prefs.getBoolean("pref_auto_caps", true)) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Suggestions", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))

        ListItem(
            headlineContent = { Text("Afficher les suggestions et corrections", fontSize = 16.sp) },
            supportingContent = { Text("Affiche des prédictions de mots au-dessus du clavier pendant la saisie", fontSize = 14.sp) },
            trailingContent = {
                Switch(
                    checked = showSuggestions,
                    onCheckedChange = {
                        showSuggestions = it
                        prefs.edit().putBoolean("pref_show_suggestions", it).apply()
                    }
                )
            },
            modifier = Modifier.clickable {
                showSuggestions = !showSuggestions
                prefs.edit().putBoolean("pref_show_suggestions", showSuggestions).apply()
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))

        ListItem(
            headlineContent = { Text("Majuscules automatique", fontSize = 16.sp) },
            supportingContent = { Text("Majuscule au premier mot de chaque phrase ou après une ponctuation", fontSize = 14.sp) },
            trailingContent = {
                Switch(
                    checked = autoCaps,
                    onCheckedChange = {
                        autoCaps = it
                        prefs.edit().putBoolean("pref_auto_caps", it).apply()
                    }
                )
            },
            modifier = Modifier.clickable {
                autoCaps = !autoCaps
                prefs.edit().putBoolean("pref_auto_caps", autoCaps).apply()
            }
        )
    }
}

@Composable
fun LegalScreen() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // ── Confidentialité ──────────────────────────────────────────
        Text(
            "Confidentialité & Données",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "🔒  Respect total de votre vie privée",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    "Thai Keyboard ne collecte, ne stocke et ne transmet AUCUNE donnée " +
                    "saisie via le clavier. Vos textes, mots de passe et informations " +
                    "personnelles restent exclusivement sur votre appareil. " +
                    "Aucune connexion réseau n'est établie pour la saisie.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "📚  Données de correction",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    "Le clavier mémorise localement les mots que vous tapez fréquemment " +
                    "pour améliorer les suggestions. Ces données restent sur votre " +
                    "appareil et peuvent être supprimées à tout moment depuis les " +
                    "paramètres de l'application.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }

        // ── Bibliothèques tierces ─────────────────────────────────────
        Text(
            "Bibliothèques tierces",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                listOf(
                    Triple("Google AdMob", "com.google.android.gms.ads", "Affichage de publicités — Politique de confidentialité Google : policies.google.com/privacy"),
                    Triple("Google Gson", "com.google.gson", "Sérialisation JSON pour le stockage local des préférences utilisateur"),
                    Triple("Jetpack Compose", "androidx.compose", "Interface utilisateur Android — Apache License 2.0"),
                    Triple("AndroidX Preferences", "androidx.preference", "Gestion des paramètres — Apache License 2.0")
                ).forEach { (name, pkg, desc) ->
                    Text(name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(pkg, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 2.dp))
                    Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
        }

        // ── Propriété intellectuelle ──────────────────────────────────
        Text(
            "Propriété intellectuelle",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "Thai Keyboard est un logiciel propriétaire. Le code source, les " +
                    "ressources graphiques et les algorithmes sont la propriété exclusive " +
                    "de l'auteur. Toute reproduction, modification ou distribution non " +
                    "autorisée est strictement interdite.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Le Logiciel est fourni « en l'état » (AS IS), sans garantie d'aucune " +
                    "sorte. L'auteur ne saurait être tenu responsable de tout dommage " +
                    "découlant de son utilisation.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }

        // ── À propos ──────────────────────────────────────────────────
        Text(
            "À propos",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Développeur : Marion Bayé", fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                Text("© 2026 Marion Bayé. Tous droits réservés.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                Text("Clavier thaï — Version 1.0", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
