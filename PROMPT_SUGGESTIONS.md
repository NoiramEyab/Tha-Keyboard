# Prompt — Suggestions de mots thaïs + option dans les préférences de correction

## Contexte du projet

Clavier Android personnalisé (`com.example.thaikeyboard`), Kotlin, sans bibliothèque tierce.
Le clavier supporte 3 langues : Thaï (`currentLanguage == 0`), Français (`1`), Anglais (`2`).

---

## Architecture actuelle — à bien lire avant de coder

### `ThaiKeyboardService.kt` (Service IME)
- Étend `InputMethodService`
- `onUpdateSelection()` est déjà overridé et appelle uniquement `keyboardView?.updateShiftStateByContext()`
- `currentInputConnection` est disponible dans tout le service
- `keyboardView` est de type `KeyboardView?`

### `KeyboardView.kt` (Vue principale)
- Variable d'état : `private var currentLanguage = 0` (0=Thaï, 1=FR, 2=EN)
- Variable d'état : `private var inputConnection: InputConnection?`
- Couleurs déjà disponibles : `bgColor`, `keyWhite`, `keySpecial`, `textPrimary`, `textSecondary`
- La **candidate bar** contient un `LinearLayout` avec `tag = "suggestionContainer"` dans un `HorizontalScrollView`. C'est là que doivent s'afficher les suggestions.
- La méthode `showLanguageFeedback()` utilise déjà ce `suggestionContainer` pour afficher un message temporaire — utiliser ce même pattern.
- `updateShiftStateByContext()` est une méthode publique — ne pas la modifier.
- `vibrate(d: Long)` et `vibrationIntensity` sont disponibles.

### `KeyboardSettings.kt`
- Object Kotlin avec accès aux SharedPreferences `"keyboard_settings"`
- Méthode helper privée : `fun prefs(context: Context)` déjà présente

### `pref_correction.xml`
- Contient une `PreferenceCategory` avec `app:title="CORRECTION"` qui a déjà un switch `auto_correction`
- Contient une `PreferenceCategory` avec `app:title="IA"` (suggestions IA bientôt disponible)

---

## Ce qu'il faut implémenter — 4 modifications uniquement

---

### 1. Nouveau fichier : `ThaiWordSuggestionEngine.kt`

Package : `com.example.thaikeyboard`

Ce fichier gère toute la logique de suggestion. Voici ce qu'il doit faire :

**Dictionnaire statique** : inclure en dur au moins 80 mots thaïs très courants couvrant différentes catégories grammaticales. Exemples : ไป, มา, ที่, ของ, และ, แต่, ว่า, ใน, จาก, ถึง, กับ, เป็น, ได้, มี, ไม่, จะ, ให้, เขา, เธอ, เรา, คุณ, นี้, นั้น, อยู่, ทำ, รู้, คิด, เห็น, บอก, ถาม, ฟัง, พูด, อ่าน, เขียน, กิน, ดื่ม, นอน, ตื่น, กลับ, ซื้อ, ขาย, ชอบ, รัก, เข้าใจ, ต้องการ, สวย, ดี, ใหญ่, เล็ก, ใหม่, เก่า, ร้อน, เย็น, วัน, เดือน, ปี, คน, บ้าน, งาน, เงิน, เวลา, ที่นี่, ข้างนอก, อาหาร, น้ำ, รถ, โทรศัพท์, คอมพิวเตอร์, เพื่อน, ครอบครัว, พ่อ, แม่, ลูก, ตอนนี้, วันนี้, พรุ่งนี้, เมื่อวาน, เร็วๆ, ช้าๆ, มาก, น้อย, ดีมาก.

**Apprentissage utilisateur** : maintenir une `Map<String, Int>` (mot → fréquence) persistée en JSON dans les SharedPreferences sous la clé `"thai_word_freq"`.

**Méthode `getSuggestions(textBeforeCursor: String): List<String>`** :
1. Extraire le fragment thaï continu juste avant le curseur : prendre les caractères depuis la fin de `textBeforeCursor`, s'arrêter dès qu'on rencontre un espace, un caractère non-thaï (hors range Unicode U+0E00–U+0E7F), ou qu'on dépasse 20 chars.
2. Si le fragment fait 0 char : retourner les 3 mots les plus fréquents de la map utilisateur, ou les 3 premiers du dictionnaire statique si la map est vide.
3. Si le fragment fait ≥ 1 char : retourner jusqu'à 3 mots (map utilisateur en priorité, puis dictionnaire statique) dont le début (`startsWith`) correspond au fragment, triés par fréquence décroissante. Exclure les doublons.

**Méthode `recordWord(word: String)`** : incrémenter la fréquence du mot dans la map et la persister. Limiter la map à 500 entrées max (supprimer les moins fréquentes si dépassé).

---

### 2. Modification de `KeyboardView.kt`

Ajouter cette méthode publique (visible depuis `ThaiKeyboardService`) :

```kotlin
fun updateSuggestions(suggestions: List<String>) {
    // Ne afficher les suggestions que pour le clavier thaï
    if (currentLanguage != 0 || isNumber || isSymbol || isEmoji || isToolsPanel) {
        findViewWithTag<LinearLayout>("suggestionContainer")?.removeAllViews()
        return
    }
    val container = findViewWithTag<LinearLayout>("suggestionContainer") ?: return
    container.removeAllViews()
    val chipRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)
    val chipPadH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
    val chipPadV = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
    val chipMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()

    suggestions.forEach { word ->
        val chip = TextView(context).apply {
            text = word
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(chipPadH, chipPadV, chipPadH, chipPadV)
            val bg = GradientDrawable().apply {
                cornerRadius = chipRadius
                setColor(keySpecial)
            }
            background = bg
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(chipMargin, 0, chipMargin, 0) }
            layoutParams = params
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener {
                inputConnection?.commitText(word, 1)
                vibrate(vibrationIntensity.toLong())
            }
        }
        container.addView(chip)
    }
}
```

---

### 3. Modification de `KeyboardSettings.kt`

Ajouter dans l'objet `KeyboardSettings`, après les méthodes existantes :

```kotlin
// ===== SUGGESTIONS DE MOTS =====
fun isWordSuggestionsEnabled(context: Context): Boolean {
    return prefs(context).getBoolean("word_suggestions_enabled", true)
}

fun setWordSuggestions(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean("word_suggestions_enabled", enabled).apply()
}
```

---

### 4. Modification de `pref_correction.xml`

Dans la `PreferenceCategory` existante `app:title="CORRECTION"`, ajouter ce switch **après** le switch `auto_correction` existant :

```xml
<SwitchPreferenceCompat
    app:key="word_suggestions_enabled"
    app:title="Suggestions de mots"
    app:summary="Affiche des propositions de mots thaïs pendant la saisie"
    app:defaultValue="true" />
```

---

### 5. Modification de `ThaiKeyboardService.kt`

**a)** Déclarer le moteur au niveau de la classe :
```kotlin
private lateinit var suggestionEngine: ThaiWordSuggestionEngine
```

**b)** Dans `onCreate()`, ajouter après l'init des prefs :
```kotlin
suggestionEngine = ThaiWordSuggestionEngine(this)
```

**c)** Ajouter une méthode privée helper :
```kotlin
private fun triggerSuggestions() {
    if (!KeyboardSettings.isWordSuggestionsEnabled(this)) {
        keyboardView?.updateSuggestions(emptyList())
        return
    }
    val ic = currentInputConnection ?: return
    val textBefore = ic.getTextBeforeCursor(30, 0)?.toString() ?: ""
    val suggestions = suggestionEngine.getSuggestions(textBefore)
    keyboardView?.updateSuggestions(suggestions)
}
```

**d)** Dans `onUpdateSelection()`, **après** l'appel existant `keyboardView?.updateShiftStateByContext()`, ajouter :
```kotlin
triggerSuggestions()
```

**e)** Dans `onStartInputView()`, **après** l'appel existant `keyboardView?.refreshKeyboard(currentInputConnection)`, ajouter :
```kotlin
triggerSuggestions()
```

**f)** Pour l'apprentissage des mots : override `onUpdateSelection` pour détecter quand un espace est inséré (newSelStart == oldSelStart + 1) et le caractère avant est un caractère thaï. À ce moment, lire le mot thaï juste avant l'espace et appeler `suggestionEngine.recordWord(word)`. Implémenter cette logique dans `triggerSuggestions()` ou dans un appel séparé dans `onUpdateSelection`.

---

## Contraintes à respecter absolument

- Ne **pas** modifier `updateShiftStateByContext()` — elle est déjà utilisée par la logique FR/EN.
- Ne **pas** modifier `showLanguageFeedback()` — elle utilise aussi le `suggestionContainer` mais de façon temporaire (1.5s), ce qui est compatible.
- Ne **pas** changer la hauteur de la candidate bar (48dp).
- Utiliser uniquement les couleurs déjà disponibles dans `KeyboardView` (`keySpecial`, `textPrimary`, etc.) — ne pas hardcoder de couleurs.
- Aucune nouvelle dépendance dans `build.gradle`.
- Aucune nouvelle permission dans `AndroidManifest.xml`.
- La clé SharedPreferences doit être exactement `"word_suggestions_enabled"` (utilisée à la fois dans `KeyboardSettings` et dans `pref_correction.xml`).
