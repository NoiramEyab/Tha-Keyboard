/*
 * Copyright (c) 2026 Marion Bayé. Tous droits réservés.
 * Ce code source et ses ressources graphiques sont la propriété exclusive de Marion Bayé.
 * Toute reproduction, modification ou distribution non autorisée est strictement interdite.
 */
package com.example.thaikeyboard

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.HorizontalScrollView
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import androidx.core.graphics.toColorInt

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Canvas
import android.graphics.RectF
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.Ink.Stroke
import com.google.mlkit.vision.digitalink.Ink.Point
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.common.model.RemoteModelManager
import com.google.android.gms.tasks.Task
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

/**
 * Main Keyboard View for ThaiKeyboard.
 * Removed GIF functionality and optimized color/layout logic.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var inputConnection: InputConnection? = null
    private var currentEditorInfo: EditorInfo? = null
    private var isShift = false
    private var isCapsLock = false
    private var isNumber = false
    private var isSymbol = false
    private var isEmoji = false
    private var isToolsPanel = false
    private var isHandwritingMode = false
    private var isLatinHandwritingMode = false
    private var handwritingView: HandwritingView? = null
    private var isModernMode = false
    private var isDeleting = false
    private var currentLanguageId = 0 // 0: Thai, 1: French, 2: English
    private lateinit var suggestionEngine: ThaiWordSuggestionEngine

    // Pointer ID map for independent multi-touch
    private val pointerViewMap = mutableMapOf<Int, View>()

    // Cache of key bounds for Voronoi-based detection
    private data class KeyBounds(val view: View, val centerX: Float, val centerY: Float)
    private var keyBoundsCache: List<KeyBounds> = emptyList()

    // Theme Colors
    private var bgColor = "#F1F3F4".toColorInt()
    private var keyWhite = "#FFFFFF".toColorInt()
    private var keySpecial = "#DADCE0".toColorInt()
    private var textPrimary = "#202124".toColorInt()
    private var textSecondary = "#5F6368".toColorInt()

    private var keyHeight = 150
    private var actualKeyboardHeight = 0
    private var keyboardContainer: LinearLayout? = null
    private var vibrationIntensity = 15
    private var initialTouchX = 0f
    private var dp1 = 0
    private var dp2 = 0
    private var dp3 = 0
    private var dp10 = 0
    private var lastSpaceTime = 0L
    private var accentPopup: android.widget.PopupWindow? = null
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var selectedAccent: String? = null

    private val accentMap = mapOf(
        "e" to listOf("é", "è", "ê", "ë"),
        "a" to listOf("à", "â", "æ", "á", "ä"),
        "i" to listOf("î", "ï", "í"),
        "o" to listOf("ô", "œ", "ö", "ó"),
        "u" to listOf("ù", "û", "ü", "ú"),
        "c" to listOf("ç"),
        "y" to listOf("ÿ"),
        "n" to listOf("ñ"),
        "." to listOf("!", "?", ",", ":", ";"),
        "," to listOf("?", "!", ".", ":", ";")
    )

    private var sharedAdView: AdView? = null

    init {
        orientation = VERTICAL
        setPadding(2, 4, 2, 0)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setOnApplyWindowInsetsListener { _, insets ->
                val navBarHeight = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                setPadding(paddingLeft, paddingTop, paddingRight, navBarHeight)
                insets
            }
        } else {
            val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) {
                setPadding(paddingLeft, paddingTop, paddingRight, resources.getDimensionPixelSize(resourceId))
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isHandwritingMode && isToolsPanel) {
            val panel = findViewWithTag<View>("toolsPanel")
            if (panel != null) {
                val offset = IntArray(2)
                panel.getLocationOnScreen(offset)
                val adBar = panel.findViewWithTag<View>("adBar")
                val adHeight = adBar?.height ?: 0
                
                // Si on touche dans le panel d'outils
                if (ev.y > offset[1]) {
                    // Si on touche au-dessus de la barre de pub (barre de pub elle-même)
                    if (ev.y < (offset[1] + adHeight)) {
                        return super.dispatchTouchEvent(ev)
                    }
                    
                    // Si c'est le mode écriture, on laisse le système gérer les clics sur les boutons
                    // via super.dispatchTouchEvent(ev) d'abord. Si rien n'est consommé (pas un bouton),
                    // on envoie à l'écriture manuscrite.
                    val handled = super.dispatchTouchEvent(ev)
                    if (!handled) {
                        handwritingView?.onTouchEvent(ev)
                        return true
                    }
                    return true
                }
            }
        }
        if (isEmoji || isToolsPanel) return super.dispatchTouchEvent(ev)
        val action = ev.actionMasked
        val pointerIndex = ev.actionIndex
        val pointerId = ev.getPointerId(pointerIndex)
        val x = ev.getX(pointerIndex)
        val y = ev.getY(pointerIndex)

        // Suggestion bar has priority (standard touch)
        if (isTouchOnCandidateBar(x, y)) {
            return super.dispatchTouchEvent(ev)
        }

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val nearestKey = findNearestKey(x, y)
                if (nearestKey != null) {
                    pointerViewMap[pointerId] = nearestKey
                    dispatchToTarget(nearestKey, ev, pointerIndex)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val pId = ev.getPointerId(i)
                    pointerViewMap[pId]?.let { target ->
                        dispatchToTarget(target, ev, i)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val targetView = pointerViewMap.remove(pointerId)
                if (targetView != null) {
                    dispatchToTarget(targetView, ev, pointerIndex)
                    return true
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun isTouchOnCandidateBar(x: Float, y: Float): Boolean {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.tag == "candidateBar") {
                val location = IntArray(2)
                child.getLocationInWindow(location)
                val parentLocation = IntArray(2)
                this.getLocationInWindow(parentLocation)
                val top = (location[1] - parentLocation[1]).toFloat()
                val bottom = top + child.height
                if (y >= top && y <= bottom) return true
            }
        }
        return false
    }

    private fun dispatchToTarget(view: View, ev: MotionEvent, pointerIndex: Int) {
        val action = ev.actionMasked
        val simulatedAction = when (action) {
            MotionEvent.ACTION_POINTER_DOWN -> MotionEvent.ACTION_DOWN
            MotionEvent.ACTION_POINTER_UP -> MotionEvent.ACTION_UP
            else -> action
        }
        
        val properties = arrayOf(MotionEvent.PointerProperties())
        ev.getPointerProperties(pointerIndex, properties[0])
        val coords = arrayOf(MotionEvent.PointerCoords())
        ev.getPointerCoords(pointerIndex, coords[0])
        
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val parentLocation = IntArray(2)
        this.getLocationInWindow(parentLocation)
        
        coords[0].x -= (location[0] - parentLocation[0]).toFloat()
        coords[0].y -= (location[1] - parentLocation[1]).toFloat()

        val transformedEvent = MotionEvent.obtain(
            ev.downTime, ev.eventTime, simulatedAction, 1, properties, coords,
            ev.metaState, ev.buttonState, ev.xPrecision, ev.yPrecision, ev.deviceId,
            ev.edgeFlags, ev.source, ev.flags
        )
        view.dispatchTouchEvent(transformedEvent)
        transformedEvent.recycle()
    }

    private fun findNearestKey(x: Float, y: Float): View? {
        val cache = keyBoundsCache
        if (cache.isEmpty()) return null

        var nearest: View? = null
        var minScore = Float.MAX_VALUE

        for (k in cache) {
            val dx = x - k.centerX
            val dy = y - k.centerY
            val score = dx * dx + dy * dy
            if (score < minScore) {
                minScore = score
                nearest = k.view
            }
        }
        return nearest
    }

    fun setEditorInfo(info: EditorInfo?) {
        currentEditorInfo = info
        
        val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        val autoCaps = prefs.getBoolean("pref_auto_caps", true)
        
        if (!autoCaps) {
            // Désactive explicitement les flags de majuscule automatique au niveau de l'éditeur
            info?.let {
                it.inputType = it.inputType and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES.inv()
                it.inputType = it.inputType and EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS.inv()
                it.inputType = it.inputType and EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS.inv()
            }
        }
    }

    fun resetState() {
        isNumber = false
        isSymbol = false
        isEmoji = false
        isToolsPanel = false
        isCapsLock = false
        isShift = false
        lastSpaceTime = 0L
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
        accentPopup?.dismiss()
        accentPopup = null
    }

    private fun applyLightTheme() {
        bgColor       = "#F1F3F4".toColorInt()
        keyWhite      = "#FFFFFF".toColorInt()
        keySpecial    = "#DADCE0".toColorInt()
        textPrimary   = "#202124".toColorInt()
        textSecondary = "#5F6368".toColorInt()
    }

    fun refreshKeyboard(ic: InputConnection?) {
        this.inputConnection = ic
        if (!::suggestionEngine.isInitialized) {
            suggestionEngine = ThaiWordSuggestionEngine(context)
        }
        val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        val metrics = context.resources.displayMetrics
        
        dp2 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.7f, metrics).toInt() // Réduction de 15% (2.0f -> 1.7f)
        dp3 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, metrics).toInt()
        dp10 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, metrics).toInt()
        dp1 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, metrics).toInt()

        try {
            val screenHeight = metrics.heightPixels
            val heightPercent = prefs.getInt("keyboard_height_percent", 50)
            val actualPercent = 0.20f + (heightPercent / 100f) * (0.38f - 0.20f)
            val totalDesiredHeight = screenHeight * actualPercent
            keyHeight = (totalDesiredHeight / 5).toInt()
            vibrationIntensity = prefs.getInt("vibration_intensity", 20)
        } catch (e: Exception) {
            keyHeight = 150
            vibrationIntensity = 20
        }

        val isPremium = KeyboardSettings.isPremium(context)
        when (prefs.getString("theme", "light")) {
            "dark" -> {
                bgColor    = "#1F1F1F".toColorInt()
                keyWhite   = "#3C4043".toColorInt()
                keySpecial = "#5F6368".toColorInt()
                textPrimary   = "#FFFFFF".toColorInt()
                textSecondary = "#BDC1C6".toColorInt()
            }
            "night_blue" -> if (isPremium) {
                bgColor    = "#0D1B2A".toColorInt()
                keyWhite   = "#1B2A3B".toColorInt()
                keySpecial = "#243447".toColorInt()
                textPrimary   = "#E8F4FD".toColorInt()
                textSecondary = "#7EB8D4".toColorInt()
            } else applyLightTheme()
            "rose" -> if (isPremium) {
                bgColor    = "#FDF0F3".toColorInt()
                keyWhite   = "#FFFFFF".toColorInt()
                keySpecial = "#F5C6D0".toColorInt()
                textPrimary   = "#3D1A22".toColorInt()
                textSecondary = "#C2637A".toColorInt()
            } else applyLightTheme()
            "forest" -> if (isPremium) {
                bgColor    = "#1A2B1F".toColorInt()
                keyWhite   = "#243D2A".toColorInt()
                keySpecial = "#2F5236".toColorInt()
                textPrimary   = "#D4EDDA".toColorInt()
                textSecondary = "#7DBF8A".toColorInt()
            } else applyLightTheme()
            "violet" -> if (isPremium) {
                bgColor    = "#1E1428".toColorInt()
                keyWhite   = "#2E1F42".toColorInt()
                keySpecial = "#3D2B56".toColorInt()
                textPrimary   = "#EDE0FF".toColorInt()
                textSecondary = "#A97FD4".toColorInt()
            } else applyLightTheme()
            else -> applyLightTheme()
        }

        setBackgroundColor(bgColor)
        val activeLangs = getActiveLanguageIds()
        if (!activeLangs.contains(currentLanguageId)) {
            currentLanguageId = activeLangs[0]
        }
        if (currentLanguageId != 0) updateShiftStateByContext()
        loadKeyboard()
    }

    fun getCurrentLanguageId(): Int = currentLanguageId

    private var currentSuggestions: List<String> = emptyList()

    private var lastAutoCorrection: Pair<String, String>? = null // (Saisie brute, Mot corrigé)

    fun updateSuggestions(suggestions: List<String>, currentInput: String = "") {
        currentSuggestions = suggestions
        val showSuggestions = KeyboardSettings.isWordSuggestionsEnabled(context)

        if (isNumber || isSymbol || isEmoji || isToolsPanel) {
            findViewWithTag<LinearLayout>("suggestionContainer")?.removeAllViews()
            return
        }
        val container = findViewWithTag<LinearLayout>("suggestionContainer") ?: return
        container.removeAllViews()

        if (!showSuggestions) {
            // Si les suggestions sont désactivées, on laisse la barre vide mais on ne l'enlève pas.
            // On ajoute 3 vues vides pour maintenir la structure si nécessaire, 
            // ou on laisse simplement le container vide.
            return
        }

        val chipRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)
        val chipPadH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt()
        val chipPadV = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()
        val chipMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics).toInt()

        val finalSuggestions = mutableListOf<String>()
        if (currentInput.isNotEmpty()) {
            // Case de GAUCHE (Verbatim) : Exactement les caractères saisis (ex: "t'ai")
            finalSuggestions.add(currentInput)
            
            // Case du MILIEU (Correction Intelligente) : Correction orthographique ou verbatim si valide
            val bestCorrection = suggestionEngine.getAutoCorrection(currentInput, currentLanguageId)
            finalSuggestions.add(bestCorrection ?: currentInput)
            
            // Case de DROITE (Prédiction) : Mot suivant probable (Bigrammes) basé sur le mot du milieu
            val predictedNext = suggestionEngine.getBigramPredictions(bestCorrection ?: currentInput, currentLanguageId)
            if (predictedNext.isNotEmpty()) {
                finalSuggestions.add(predictedNext[0])
            } else {
                // Fallback sur une alternative de complétion si pas de bigramme
                suggestions.firstOrNull { it.lowercase() != currentInput.lowercase() && it != (bestCorrection ?: "") }
                    ?.let { finalSuggestions.add(it) }
            }
        } else {
            // Pas d'input en cours : Proposer des bigrammes pour commencer la phrase ou après un espace
            val ic = inputConnection
            val before = ic?.getTextBeforeCursor(50, 0)?.toString() ?: ""
            val predictions = suggestionEngine.getSuggestions(before, currentLanguageId)
            finalSuggestions.addAll(predictions)
        }

        finalSuggestions.take(3).forEachIndexed { index, word ->
            val isTypedWord = index == 0 && currentInput.isNotEmpty()
            val isCenterCorrection = index == 1 && currentInput.isNotEmpty()

            val chip = TextView(context).apply {
                text = word
                // On met en avant la case du milieu si elle diffère de la saisie (c'est une correction)
                val isActuallyCorrecting = isCenterCorrection && word.lowercase() != currentInput.lowercase()
                
                setTextColor(if (isActuallyCorrecting) Color.WHITE else textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(chipPadH, chipPadV, chipPadH, chipPadV)
                
                val bg = GradientDrawable().apply {
                    cornerRadius = chipRadius
                    setColor(if (isActuallyCorrecting) "#1A73E8".toColorInt() else keySpecial)
                }
                background = bg
                
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins(chipMargin, 0, chipMargin, 0)
                }
                layoutParams = params
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                
                setOnClickListener {
                    if (index == 0 && currentInput.isNotEmpty()) {
                        // Clic sur Verbatim (GAUCHE) : Valider tel quel et apprendre en session
                        suggestionEngine.addToSessionDictionary(word)
                        commitVerbatim(word)
                    } else {
                        commitSuggestion(word)
                    }
                }
            }
            container.addView(chip)
        }
    }

    private fun commitVerbatim(word: String) {
        val ic = inputConnection ?: return
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
        val words = before.trim().split(Regex("[^a-zA-Z\u00C0-\u00FF\u0E00-\u0E7F']")).filter { it.isNotEmpty() }
        val lastWord = words.lastOrNull() ?: ""
        
        if (lastWord.isNotEmpty()) {
            ic.deleteSurroundingText(lastWord.length, 0)
        }
        ic.commitText(word + (if (currentLanguageId == 0) "" else " "), 1)
        
        // Apprentissage immédiat pour éviter de recorreger ce mot précis à l'avenir
        suggestionEngine.recordWord(word, null, currentLanguageId)
        
        updateSuggestions(emptyList())
        if (currentLanguageId != 0) updateShiftStateByContext()
        vibrate(vibrationIntensity.toLong())
    }

    private fun commitSuggestion(suggestion: String) {
        val ic = inputConnection ?: return
        
        // Déterminer le mot en cours (composing)
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
        var lastWordLen = 0
        var prevWord: String? = null
        
        if (before.isNotEmpty()) {
            val words = before.trim().split(Regex("[^a-zA-Z\u00C0-\u00FF\u0E00-\u0E7F']")).filter { it.isNotEmpty() }
            if (words.isNotEmpty()) {
                val lastWord = words.last()
                lastWordLen = lastWord.length
                if (words.size >= 2) prevWord = words[words.size - 2]
            }
        }

        if (lastWordLen > 0) {
            ic.deleteSurroundingText(lastWordLen, 0)
        }
        
        // Insérer la suggestion + espace (sauf en Thaï)
        val suffix = if (currentLanguageId == 0) "" else " "
        ic.commitText(suggestion + suffix, 1)
        
        vibrate(vibrationIntensity.toLong())
        
        // Apprentissage : Enregistrer le mot validé
        suggestionEngine.recordWord(suggestion, prevWord, currentLanguageId)
        
        // Reset des suggestions après validation
        updateSuggestions(emptyList())
        if (currentLanguageId != 0) updateShiftStateByContext()
    }

    private fun getActiveLanguageIds(): List<Int> {
        val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        val active = mutableListOf<Int>()
        if (prefs.getBoolean("pref_lang_th", true)) active.add(0)
        if (prefs.getBoolean("pref_lang_fr", true)) active.add(1)
        if (prefs.getBoolean("pref_lang_en", true)) active.add(2)
        if (active.isEmpty()) active.add(0)
        return active
    }

    private fun getConsonantColor(c: String): Int {
        val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        val isPremium = KeyboardSettings.isPremium(context)
        val isColorEnabled = prefs.getBoolean("pref_color_consonants", true)
        
        if (!isPremium || !isColorEnabled) return -1

        val high = listOf("ข", "ฃ", "ฉ", "ถ", "ผ", "ฝ", "ส", "ห", "ศ", "ษ", "ฐ")
        val mid = listOf("ก", "จ", "ด", "ต", "บ", "ป", "อ", "ฎ", "ฏ")
        val low = listOf("ค", "ฅ", "ช", "ท", "พ", "ฟ", "ม", "น", "ร", "ล", "ว", "ง", "ญ", "ณ", "ย", "ฆ", "ฌ","ฑ", "ธ", "ภ", "ฒ" , "ฬ", "ฮ" , "ซ")
        return when {
            c in high -> "#D32F2F".toColorInt()
            c in mid -> "#388E3C".toColorInt()
            c in low -> "#1976D2".toColorInt()
            else -> -1
        }
    }

    private fun loadKeyboard() {
        if ((isHandwritingMode || isLatinHandwritingMode) && (currentLanguageId == 0 || isLatinHandwritingMode)) {
            removeAllViews()
            addCandidateBar()
            loadToolsPanel()
            scheduleKeyBoundsUpdate()
            return
        }

        removeAllViews()
        addCandidateBar()

        if (isToolsPanel) {
            loadToolsPanel()
            scheduleKeyBoundsUpdate()
            return
        }
        if (isEmoji) {
            loadEmojiKeyboard()
            scheduleKeyBoundsUpdate()
            return 
        }
        if (isSymbol) { loadSymbolKeyboard(); scheduleKeyBoundsUpdate(); return }
        if (isNumber) { loadNumberKeyboard(); scheduleKeyBoundsUpdate(); return }

        val rows = when (currentLanguageId) {
            0 -> if (isShift || isCapsLock) KeyboardLayouts.THAI_SHIFT else KeyboardLayouts.THAI
            1 -> if (isShift || isCapsLock) KeyboardLayouts.FR_SHIFT else KeyboardLayouts.FR
            2 -> if (isShift || isCapsLock) KeyboardLayouts.EN_SHIFT else KeyboardLayouts.EN
            else -> KeyboardLayouts.THAI
        }

        val keyboardLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, 0)
        }
        keyboardContainer = keyboardLayout

        rows.forEachIndexed { index, row ->
            if (index > 0) addVerticalGap(dp10, keyboardLayout)
            addRow(row, 1.0f, keyboardLayout)
        }
        addVerticalGap(dp10, keyboardLayout)
        addBottomRow(keyboardLayout)
        addView(keyboardLayout)
        
        // S'assurer que la barre de suggestions est visible
        findViewWithTag<View>("candidateBar")?.visibility = View.VISIBLE
        
        scheduleKeyBoundsUpdate()

        // Capture de la hauteur réelle après construction du clavier
        post {
            if (!isHandwritingMode && !isToolsPanel && !isEmoji && !isNumber && !isSymbol) {
                actualKeyboardHeight = keyboardLayout.height
            }
        }
    }

    private fun scheduleKeyBoundsUpdate() {
        post { post { rebuildKeyBoundsCache() } }
    }

    private fun rebuildKeyBoundsCache() {
        if (!isAttachedToWindow) return
        val cache = mutableListOf<KeyBounds>()
        val parentLocation = IntArray(2)
        this.getLocationInWindow(parentLocation)

        fun collect(view: View) {
            if (view.tag == "clickableKey") {
                if (view.width > 0 && view.height > 0) {
                    val loc = IntArray(2)
                    view.getLocationInWindow(loc)
                    val centerX = (loc[0] - parentLocation[0]).toFloat() + (view.width / 2f)
                    val centerY = (loc[1] - parentLocation[1]).toFloat() + (view.height / 2f)
                    cache.add(KeyBounds(view, centerX, centerY))
                }
            } else if (view is ViewGroup) {
                for (i in 0 until view.childCount) collect(view.getChildAt(i))
            }
        }
        collect(this)
        keyBoundsCache = cache
    }

    private fun loadNumberKeyboard() {
        val container = LinearLayout(context).apply { orientation = VERTICAL }
        addRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), 1.0f, container)
        addVerticalGap(dp10, container)
        addRow(listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"), 1.0f, container)
        addVerticalGap(dp10, container)
        addRow(listOf("%", "~", "|", "•", "√", "π", "÷", "×", "{", "}"), 1.0f, container)
        addVerticalGap(dp10, container)
        val row4 = LinearLayout(context).apply { orientation = HORIZONTAL }
        row4.addView(createKey("=\\<", 1.5f, 1.0f))
        listOf("*", "\"", "'", ":", ";", "!", "?").forEach { row4.addView(createKey(it, 1.0f, 1.0f)) }
        row4.addView(createKey("⌫", 1.5f, 1.0f))
        container.addView(row4)
        addVerticalGap(dp10, container)
        addBottomRow(container)
        addView(container)
    }

    private fun loadSymbolKeyboard() {
        val container = LinearLayout(context).apply { orientation = VERTICAL }
        addRow(listOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"), 1.0f, container)
        addVerticalGap(dp10, container)
        addRow(listOf("£", "¥", "€", "¢", "^", "°", "=", "[", "]", "\\"), 1.0f, container)
        addVerticalGap(dp10, container)
        addRow(listOf("©", "®", "™", "¶", "§", "∆", "≠", "≈", "≤", "≥"), 1.0f, container)
        addVerticalGap(dp10, container)
        val row4 = LinearLayout(context).apply { orientation = HORIZONTAL }
        row4.addView(createKey("?123", 1.5f, 1.0f))
        listOf("‹", "›", "«", "»", "⟨", "⟩", "„").forEach { row4.addView(createKey(it, 1.0f, 1.0f)) }
        row4.addView(createKey("⌫", 1.5f, 1.0f))
        container.addView(row4)
        addVerticalGap(dp10, container)
        addBottomRow(container)
        addView(container)
    }

    private fun addVerticalGap(h: Int, parent: ViewGroup? = null) {
        if (h <= 0) return
        val v = View(context).apply { layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, h) }
        if (parent != null) parent.addView(v) else addView(v)
    }

    private fun loadEmojiKeyboard() {
        val container = LinearLayout(context).apply { orientation = VERTICAL }
        val themeColors = mapOf("bgColor" to bgColor, "textPrimary" to textPrimary, "textSecondary" to textSecondary)
        
        // Détruire l'ancienne pub emoji si elle existe déjà dans la hiérarchie
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is LinearLayout) {
                for (j in 0 until child.childCount) {
                    val subChild = child.getChildAt(j)
                    if (subChild is EmojiPickerView) {
                        subChild.destroyAd()
                    }
                }
            }
        }

        val emojiPicker = EmojiPickerView(context, { emoji ->
            inputConnection?.commitText(emoji, 1)
            vibrate(vibrationIntensity.toLong())
        }, themeColors)
        emojiPicker.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (keyHeight * 4.5).toInt())
        container.addView(emojiPicker)
        addBottomRow(container)
        addView(container)
    }

    private fun addRow(keys: List<String>, hMultiplier: Float, parent: ViewGroup? = null) {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        keys.forEach { row.addView(createKey(it, 1.0f, hMultiplier)) }
        if (parent != null) parent.addView(row) else addView(row)
    }

    private fun addBottomRow(parent: ViewGroup? = null) {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        if (isEmoji) {
            row.addView(createKey("ABC", 1.5f, 1.0f))
            row.addView(createKey(" ", 9.0f, 1.0f))
            row.addView(createKey("⌫", 1.5f, 1.0f))
        } else {
            val leftText = if (isNumber || isSymbol) "ABC" else "123"
            row.addView(createKey(leftText, 1.5f, 1.0f))
            row.addView(createKey("🙂", 1.0f, 1.0f))
            val isFrOrEn = currentLanguageId == 1 || currentLanguageId == 2
            val isAlphaLayout = !isNumber && !isSymbol
            if (isFrOrEn && isAlphaLayout) {
                row.addView(createKey(",", 1.0f, 1.0f))
                row.addView(createKey(" ", 6.0f, 1.0f))
                row.addView(createKey(".", 1.0f, 1.0f))
            } else {
                row.addView(createKey(" ", 8.0f, 1.0f))
            }
            row.addView(createKey("⏎", 1.5f, 1.0f))
        }
        if (parent != null) parent.addView(row) else addView(row)
    }

    private fun addCandidateBar() {
        // La barre reste visible en permanence, même si les suggestions sont OFF
        val barHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 38f, resources.displayMetrics).toInt()
        val bar = LinearLayout(context).apply {
            tag = "candidateBar"
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, barHeightPx)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp2, 0, dp2, 0)
            // Ligne de séparation fine en bas
            background = GradientDrawable().apply {
                setColor(bgColor)
                setStroke(1, (if (keyWhite != Color.WHITE) "#20FFFFFF" else "#20000000").toColorInt())
            }
        }

        val iconSize = (barHeightPx * 1.0).toInt() // Agrandissement subtil (environ 15%)
        val iconPadding = (barHeightPx * 0.22).toInt()
        val gridResId = context.resources.getIdentifier("ic_grid", "drawable", context.packageName)
        val gridBtn = ImageView(context).apply {
            if (gridResId != 0) setImageResource(gridResId)
            imageTintList = ColorStateList.valueOf(textSecondary)
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            layoutParams = LayoutParams(iconSize, iconSize)
            isClickable = true
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                vibrate(vibrationIntensity.toLong())
                
                // Si on est en mode écriture, on ferme le pad et on affiche les outils
                if (isHandwritingMode || isLatinHandwritingMode) {
                    isHandwritingMode = false
                    isLatinHandwritingMode = false
                    isToolsPanel = true
                    removeAllViews()
                    loadKeyboard()
                } else {
                    // Comportement standard : basculer le menu outils
                    isToolsPanel = !isToolsPanel
                    if (isToolsPanel) { isEmoji = false; isNumber = false; isSymbol = false }
                    loadKeyboard()
                }
            }
        }
        bar.addView(gridBtn)

        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
        }
        val suggestionContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            tag = "suggestionContainer"
        }
        scrollView.addView(suggestionContainer)
        bar.addView(scrollView)

        if (getActiveLanguageIds().size > 1) {
            val globeResId = context.resources.getIdentifier("ic_globe", "drawable", context.packageName)
            val globeBtn = ImageView(context).apply {
                if (globeResId != 0) setImageResource(globeResId)
                imageTintList = ColorStateList.valueOf(Color.parseColor("#2196F3")) // Bleu standard (système)
                setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
                layoutParams = LayoutParams(iconSize, iconSize)
                isClickable = true
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { handleKeyAction("GLOBE") }
            }
            bar.addView(globeBtn)
        }
        addView(bar)
        addView(View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 1)
            val isDark = keyWhite != Color.WHITE
            setBackgroundColor(if (isDark) "#3C4043".toColorInt() else "#DADCE0".toColorInt())
        })
    }

    private fun loadToolsPanel() {
        val dp = resources.displayMetrics.density
        val isDark = keyWhite != Color.WHITE

        // Si on n'est pas en Thaï, on ferme le mode écriture manuscrite Thaï
        if (currentLanguageId != 0 && isHandwritingMode) {
            isHandwritingMode = false
        }

        // Panel principal - Hauteur forcée par la capture du clavier réel ou calculée par défaut
        val panelHeight = if (actualKeyboardHeight > 0) actualKeyboardHeight else (keyHeight * 5) + (dp10 * 4)
        
        val panel = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, panelHeight)
            setBackgroundColor(bgColor)
            setPadding(0, 0, 0, 0)
        }

        // 1. AdView réelle (AdMob) - HAUT
        val isPremium = KeyboardSettings.isPremium(context)
        
        // Configuration Adaptive Banner selon les recommandations Google AdMob Quick Start
        val displayMetrics = resources.displayMetrics
        val adWidthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        val adaptiveSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
        val adHeight = adaptiveSize.getHeightInPixels(context)

        val adBarTop = FrameLayout(context).apply {
            tag = "adBar"
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, adHeight).apply {
                if (!isPremium) setMargins(0, 0, 0, (dp * 8).toInt())
            }
            visibility = if (isPremium) View.GONE else View.VISIBLE
            setBackgroundColor(if (isDark) "#3C4043".toColorInt() else "#F1F3F4".toColorInt())
            
            if (sharedAdView == null && !isPremium) {
                android.util.Log.d("ThaiTonesAd", "Requête envoyée pour le Pad")
                sharedAdView = AdView(context).apply {
                    adUnitId = "ca-app-pub-1813285379775825/8994366413"
                    setAdSize(adaptiveSize)
                }
            }
            
            sharedAdView?.let { ad ->
                (ad.parent as? ViewGroup)?.removeView(ad)
                ad.layoutParams = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                removeAllViews()
                addView(ad)
                ad.loadAd(AdRequest.Builder().build())
            }
        }
        panel.addView(adBarTop)

        if ((isHandwritingMode && currentLanguageId == 0) || isLatinHandwritingMode) {
            // Layout horizontal pour maximiser la surface (Pad à gauche, Boutons à droite)
            val mainHwLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            }

            // Zone de dessin : Occupation maximale (90% de la largeur)
            val hwView = HandwritingView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.9f)
                setBackgroundColor(if (isDark) "#1C1C1E".toColorInt() else "#FFFFFF".toColorInt())
                setPadding((2 * dp).toInt(), (2 * dp).toInt(), (2 * dp).toInt(), 0)
                isClickable = true
                isFocusable = true
                // Configurer le langage de reconnaissance
                setLanguage(if (isLatinHandwritingMode) "en" else "th")
            }
            handwritingView = hwView
            mainHwLayout.addView(hwView)

            // Colonne de boutons ultra-compacte (10% de la largeur)
            val controls = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.1f)
                setPadding(0, (4 * dp).toInt(), 0, 0)
                setBackgroundColor(bgColor)
            }

            val btnHeight = (32 * dp).toInt()
            val btnWidth = (46 * dp).toInt() // Légère réduction pour tenir dans les 10%
            val pillColor = if (isDark) "#3A3A3C".toColorInt() else "#E0E0E0".toColorInt()
            val marginBottom = (14 * dp).toInt()

            val btnClearDraw = TextView(context).apply {
                text = "Effacer"
                setTextColor(textPrimary); textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                isClickable = true; isFocusable = true
                background = GradientDrawable().apply { cornerRadius = btnHeight / 2f; setColor(pillColor) }
                layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply { setMargins(0, 0, 0, marginBottom) }
                setOnClickListener { vibrate(vibrationIntensity.toLong()); hwView.clear() }
            }

            val btnDeleteText = TextView(context).apply {
                text = "⌫"
                setTextColor(textPrimary); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                isClickable = true; isFocusable = true
                background = GradientDrawable().apply { cornerRadius = btnHeight / 2f; setColor(pillColor) }
                layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply { setMargins(0, 0, 0, marginBottom) }
                setOnClickListener { vibrate(vibrationIntensity.toLong()); inputConnection?.deleteSurroundingText(1, 0) }
            }

            val btnKeyboard = TextView(context).apply {
                text = "Clavier"
                setTextColor(textPrimary); textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                isClickable = true; isFocusable = true
                background = GradientDrawable().apply { cornerRadius = btnHeight / 2f; setColor(pillColor) }
                layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight)
                setOnClickListener {
                    val ims = context as? android.inputmethodservice.InputMethodService
                    if (ims == null) return@setOnClickListener
                    
                    vibrate(vibrationIntensity.toLong())
                    
                    post {
                        try {
                            // 1. Désactivation des modes spéciaux
                            isHandwritingMode = false
                            isLatinHandwritingMode = false
                            isToolsPanel = false

                            // 2. Nettoyage complet du conteneur actuel (Pad, boutons, pub)
                            this@KeyboardView.removeAllViews()

                            // 3. Reconstruction immédiate des touches alphabétiques
                            loadKeyboard()
                            
                            // 4. Forcer le rafraîchissement visuel instantané
                            this@KeyboardView.requestLayout()
                            this@KeyboardView.invalidate()
                            
                            // 5. Réafficher la barre de suggestions si nécessaire
                            ims.setCandidatesViewShown(true)
                            
                        } catch (e: Exception) {
                            android.util.Log.e("ThaiKeyboard", "CRITICAL ERROR in internal transition: ${e.message}")
                            isHandwritingMode = false
                            isLatinHandwritingMode = false
                            isToolsPanel = false
                            loadKeyboard()
                        }
                    }
                }
            }

            controls.addView(btnClearDraw); controls.addView(btnDeleteText); controls.addView(btnKeyboard)
            mainHwLayout.addView(controls)
            panel.addView(mainHwLayout)
        } else {
            // Tuile Écriture toujours visible
            val screenWidth = resources.displayMetrics.widthPixels
            val tileSize = screenWidth / 4

            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }

            val tileView = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(tileSize, tileSize).apply {
                    setMargins((dp * 8).toInt(), (dp * 8).toInt(), (dp * 4).toInt(), (dp * 8).toInt())
                }

                val bg = GradientDrawable().apply {
                    cornerRadius = dp * 12
                    setColor(if (isDark) "#2C2C2E".toColorInt() else "#FFFFFF".toColorInt())
                }
                val rippleColor = if (isDark) "#40FFFFFF".toColorInt() else "#20000000".toColorInt()
                background = RippleDrawable(ColorStateList.valueOf(rippleColor), bg, bg)
                elevation = dp * 4 // Effet de relief

                isClickable = true
                isFocusable = true
                setOnClickListener {
                    vibrate(vibrationIntensity.toLong())
                    isHandwritingMode = true
                    isLatinHandwritingMode = false
                    removeAllViews()
                    loadKeyboard()
                }

                addView(TextView(context).apply {
                    text = "✍️"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = (dp * 4).toInt()
                    }
                })

                addView(TextView(context).apply {
                    text = "Écriture Thaï"
                    setTextColor(textSecondary)
                    textSize = 10f
                    gravity = Gravity.CENTER
                })
            }
            row.addView(tileView)

            // Tuile Écriture Latine (FR/EN)
            val tileLatin = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(tileSize, tileSize).apply {
                    setMargins((dp * 4).toInt(), (dp * 8).toInt(), (dp * 8).toInt(), (dp * 8).toInt())
                }

                val bg = GradientDrawable().apply {
                    cornerRadius = dp * 12
                    setColor(if (isDark) "#2C2C2E".toColorInt() else "#FFFFFF".toColorInt())
                }
                val rippleColor = if (isDark) "#40FFFFFF".toColorInt() else "#20000000".toColorInt()
                background = RippleDrawable(ColorStateList.valueOf(rippleColor), bg, bg)
                elevation = dp * 4 // Effet de relief

                isClickable = true
                isFocusable = true
                setOnClickListener {
                    vibrate(vibrationIntensity.toLong())
                    isLatinHandwritingMode = true
                    isHandwritingMode = false
                    removeAllViews()
                    loadKeyboard()
                }

                addView(TextView(context).apply {
                    text = "✍️"
                    textSize = 16f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = (dp * 4).toInt()
                    }
                })

                addView(TextView(context).apply {
                    text = "Écriture FR/EN"
                    setTextColor(textSecondary)
                    textSize = 10f
                    gravity = Gravity.CENTER
                })
            }
            row.addView(tileLatin)

            // Tuile Paramètres (Ajoutée ici)
            val settingsResId = context.resources.getIdentifier("ic_preferences", "drawable", context.packageName)
            val tileSettings = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(tileSize, tileSize).apply {
                    setMargins((dp * 4).toInt(), (dp * 8).toInt(), (dp * 8).toInt(), (dp * 8).toInt())
                }

                val bg = GradientDrawable().apply {
                    cornerRadius = dp * 12
                    setColor(if (isDark) "#2C2C2E".toColorInt() else "#FFFFFF".toColorInt())
                }
                val rippleColor = if (isDark) "#40FFFFFF".toColorInt() else "#20000000".toColorInt()
                background = RippleDrawable(ColorStateList.valueOf(rippleColor), bg, bg)
                elevation = dp * 4

                isClickable = true
                isFocusable = true
                setOnClickListener {
                    vibrate(vibrationIntensity.toLong())
                    val intent = Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }

                addView(ImageView(context).apply {
                    if (settingsResId != 0) setImageResource(settingsResId)
                    else setImageResource(android.R.drawable.ic_menu_preferences)
                    imageTintList = ColorStateList.valueOf(Color.GRAY)
                    layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                        bottomMargin = (dp * 4).toInt()
                    }
                })

                addView(TextView(context).apply {
                    text = "Paramètres"
                    setTextColor(textSecondary)
                    textSize = 10f
                    gravity = Gravity.CENTER
                })
            }
            row.addView(tileSettings)

            panel.addView(row)
        }

        panel.tag = "toolsPanel"
        addView(panel)
    }

    private val HANDWRITING_TIMEOUT = 1300L

    inner class HandwritingView(context: Context) : View(context) {
        private val paint = Paint().apply {
            color = if (keyWhite != Color.WHITE) Color.WHITE else Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        private val path = Path()
        private var inkBuilder = Ink.builder()
        private var strokeBuilder = Stroke.builder()
        private var recognizer: DigitalInkRecognizer? = null
        private val handler = Handler(Looper.getMainLooper())
        private val recognizeRunnable = Runnable { recognize() }
        private var currentLang = "th"

        private var sharedAdView: AdView? = null

    init {
            setupRecognizer()
        }

        fun setLanguage(lang: String) {
            if (currentLang != lang) {
                currentLang = lang
                setupRecognizer()
            }
        }

        private fun setupRecognizer() {
            val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(currentLang)
            if (modelIdentifier != null) {
                val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
                RemoteModelManager.getInstance().download(model, com.google.mlkit.common.model.DownloadConditions.Builder().build())
                recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y
            val t = System.currentTimeMillis()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    path.moveTo(x, y)
                    strokeBuilder = Stroke.builder()
                    strokeBuilder.addPoint(Point.create(x, y, t))
                    handler.removeCallbacks(recognizeRunnable)
                }
                MotionEvent.ACTION_MOVE -> {
                    path.lineTo(x, y)
                    strokeBuilder.addPoint(Point.create(x, y, t))
                }
                MotionEvent.ACTION_UP -> {
                    inkBuilder.addStroke(strokeBuilder.build())
                    handler.removeCallbacks(recognizeRunnable)
                    handler.postDelayed(recognizeRunnable, HANDWRITING_TIMEOUT)
                }
            }
            invalidate()
            return true
        }

        fun clear() {
            path.reset()
            inkBuilder = Ink.builder()
            invalidate()
        }

        private fun recognize() {
            val ink = inkBuilder.build()
            if (ink.strokes.isEmpty()) return

            recognizer?.recognize(ink)?.addOnSuccessListener { result ->
                if (result.candidates.isNotEmpty()) {
                    val candidateText = result.candidates[0].text
                    // Filtrage : On ne garde que les caractères qui ne sont pas des chiffres (0-9)
                    val filteredText = candidateText.filter { !it.isDigit() }
                    if (filteredText.isNotEmpty()) {
                        inputConnection?.commitText(filteredText, 1)
                        vibrate(vibrationIntensity.toLong())
                    }
                    clear()
                }
            }
        }
    }

    private fun createKey(key: String, weight: Float, hMultiplier: Float): FrameLayout {
        val isSpecial = key in listOf("⇧", "⌫", "123", "?123", "ABC", "⏎", "⚙️", "🙂", "=\\<", "GLOBE")
        val isEnter = key == "⏎"
        val isShiftKey = key == "⇧"
        val isSpace = key == " "
        val isDark = keyWhite != Color.WHITE
        val dp = resources.displayMetrics.density

        val container = FrameLayout(context).apply {
            tag = "clickableKey"
            contentDescription = key
            val shape = GradientDrawable().apply {
                cornerRadius = dp * (if (isSpace) 10f else 8f)
                setColor(when {
                    isSpace -> if (isDark) "#4A4D50".toColorInt() else "#F8F9FA".toColorInt()
                    isEnter -> "#1A73E8".toColorInt()
                    isSpecial -> keySpecial
                    else -> keyWhite
                })
                if (isSpace) setStroke(dp1, if (isDark) "#2D2F31".toColorInt() else "#DADCE0".toColorInt())
            }
            val finalBg = if (isSpace) {
                StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
                        cornerRadius = dp * 10f; setColor(if (isDark) "#3C4043".toColorInt() else "#E8EAED".toColorInt())
                        setStroke(dp1, if (isDark) "#202124".toColorInt() else "#BDC1C6".toColorInt())
                    })
                    addState(intArrayOf(), shape)
                }
            } else shape
            background = RippleDrawable(ColorStateList.valueOf(if (isEnter) "#40FFFFFF".toColorInt() else "#20000000".toColorInt()), finalBg, shape)
            layoutParams = LinearLayout.LayoutParams(0, (keyHeight * hMultiplier).toInt(), weight).apply { setMargins(dp1, dp2, dp1, dp2) }
            elevation = if (isSpace) dp * 4f else dp * 2f
        }

        if (isEnter || isShiftKey) {
            container.addView(ImageView(context).apply {
                val iconResId = context.resources.getIdentifier(if (isEnter) "ic_keyboard_enter" else "ic_shift", "drawable", context.packageName)
                if (iconResId != 0) setImageResource(iconResId)
                imageTintList = ColorStateList.valueOf(if (isEnter) Color.WHITE else if (isShiftKey && (isShift || isCapsLock)) "#1A73E8".toColorInt() else textPrimary)
                scaleType = ImageView.ScaleType.FIT_CENTER
                val p = if (isShiftKey) (keyHeight * hMultiplier * 0.25f).toInt() else (keyHeight * hMultiplier * 0.3f).toInt()
                setPadding(0, p, 0, p)
            })
        } else if (key == "GLOBE") {
            container.addView(ImageView(context).apply {
                val resId = context.resources.getIdentifier("ic_globe", "drawable", context.packageName)
                if (resId != 0) setImageResource(resId)
                imageTintList = ColorStateList.valueOf("#1A73E8".toColorInt()); scaleType = ImageView.ScaleType.FIT_CENTER
                val p = (keyHeight * hMultiplier * 0.28f).toInt(); setPadding(0, p, 0, p)
            })
        } else {
            val spaceLabel = when (currentLanguageId) { 0 -> "ไทย"; 1 -> "Français"; 2 -> "English"; else -> "" }
            container.addView(TextView(context).apply {
                text = if (isSpace) (if (isNumber || isSymbol) "" else spaceLabel) else if (isModernMode && !isSpecial) getModernChar(key) else key
                val baseSize = keyHeight * hMultiplier
                if (isSpace) {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSize * 0.25f); setTextColor(textSecondary); setTypeface(null, Typeface.NORMAL)
                } else {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSize * 0.42f); setTypeface(null, Typeface.BOLD)
                    val tone = if (currentLanguageId == 0) getConsonantColor(key) else -1
                    setTextColor(if (tone != -1 && !isNumber && !isSymbol) tone else textPrimary)
                }
                gravity = Gravity.CENTER
            })
        }

        val rom = if (!isNumber && !isSymbol && !isEnter && !isShiftKey) romanize(key) else ""
        if (rom.isNotEmpty()) {
            container.addView(TextView(context).apply {
                text = rom; setTextSize(TypedValue.COMPLEX_UNIT_PX, (keyHeight * hMultiplier * 0.17f)); setTextColor(textSecondary)
                gravity = Gravity.BOTTOM or Gravity.END; setPadding(0, 0, dp3, dp2)
            })
        }

        if (key == "⌫") {
            val deleteHandler = Handler(Looper.getMainLooper())
            var deleteRunnable: Runnable? = null
            container.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        v.isPressed = true; vibrate(vibrationIntensity.toLong()); inputConnection?.deleteSurroundingText(1, 0)
                        val delay = KeyboardSettings.getLongPressTimeout(context).toLong().coerceAtLeast(200L)
                        deleteRunnable = object : Runnable {
                            override fun run() {
                                if (!inputConnection?.getTextBeforeCursor(1, 0).isNullOrEmpty()) {
                                    inputConnection?.deleteSurroundingText(1, 0); deleteHandler.postDelayed(this, 45)
                                } else if (currentLanguageId != 0) updateShiftStateByContext()
                            }
                        }
                        deleteHandler.postDelayed(deleteRunnable!!, delay)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        deleteRunnable?.let { deleteHandler.removeCallbacks(it) }; deleteRunnable = null; v.isPressed = false
                        if (currentLanguageId != 0) updateShiftStateByContext()
                    }
                }
                true
            }
        } else {
            container.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        v.isPressed = true; initialTouchX = event.x; handleKeyAction(key)
                        if (key == "GLOBE") {
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                            longPressRunnable = Runnable {
                                vibrate(vibrationIntensity.toLong() * 2)
                                context.startActivity(Intent(context, MainActivity::class.java).apply { putExtra("screen", "LANGUAGES"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, 600L)
                        } else if (accentMap.containsKey(key.lowercase())) {
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                            longPressRunnable = Runnable { showAccentPopup(v, key.lowercase()) }
                            longPressHandler.postDelayed(longPressRunnable!!, KeyboardSettings.getLongPressTimeout(context).toLong().coerceAtLeast(400L))
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (accentPopup?.isShowing == true) updateAccentSelection(event.rawX, event.rawY)
                        else if (Math.abs(event.x - initialTouchX) > 4f) longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        v.isPressed = false; val wasAccent = accentPopup?.isShowing == true
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        if (wasAccent) {
                            selectedAccent?.let {
                                val text = if (isShift || isCapsLock) it.uppercase() else it
                                inputConnection?.deleteSurroundingText(1, 0); inputConnection?.commitText(text, 1)
                                vibrate(vibrationIntensity.toLong())
                                if (currentLanguageId != 0 && isShift && !isCapsLock) { isShift = false; updateShiftStateByContext(); loadKeyboard() }
                            }
                            accentPopup?.dismiss(); accentPopup = null
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> { v.isPressed = false; longPressRunnable?.let { longPressHandler.removeCallbacks(it) }; accentPopup?.dismiss(); accentPopup = null }
                }
                true
            }
        }
        return container
    }

    private fun showAccentPopup(anchor: View, baseKey: String) {
        val accents = accentMap[baseKey] ?: return
        selectedAccent = null
        val dp = resources.displayMetrics.density
        val popupView = LinearLayout(context).apply {
            orientation = HORIZONTAL; setPadding(dp2, dp2, dp2, dp2)
            background = GradientDrawable().apply {
                setColor(keyWhite); cornerRadius = dp * 8f; setStroke(1, "#CCCCCC".toColorInt())
            }
            elevation = dp * 4f
        }
        accents.forEach { accent ->
            popupView.addView(TextView(context).apply {
                text = if (isShift || isCapsLock) accent.uppercase() else accent; textSize = 18f; setTextColor(textPrimary)
                gravity = Gravity.CENTER; setPadding(dp3 * 3, dp3 * 2, dp3 * 3, dp3 * 2); tag = accent
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp1, 0, dp1, 0) }
                setOnClickListener {
                    val text = if (isShift || isCapsLock) accent.uppercase() else accent
                    inputConnection?.deleteSurroundingText(1, 0)
                    inputConnection?.commitText(text, 1)
                    vibrate(15)
                    accentPopup?.dismiss()
                    accentPopup = null
                }
            })
        }
        if (anchor.windowToken == null) return
        accentPopup = android.widget.PopupWindow(popupView, -2, -2).apply {
            isFocusable = true
            isOutsideTouchable = true
            inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
            animationStyle = android.R.style.Animation_Dialog
            popupView.measure(0, 0)
            val loc = IntArray(2); anchor.getLocationInWindow(loc)
            val screenWidth = resources.displayMetrics.widthPixels
            var xPos = loc[0] + (anchor.width / 2) - (popupView.measuredWidth / 2)
            xPos = xPos.coerceAtLeast(10).coerceAtMost(screenWidth - popupView.measuredWidth - 10)
            showAtLocation(anchor.rootView, Gravity.NO_GRAVITY, xPos, loc[1] - popupView.measuredHeight - dp3 * 10)
        }
        vibrate(vibrationIntensity.toLong())
    }

    private fun updateAccentSelection(rawX: Float, rawY: Float) {
        val popup = accentPopup ?: return
        if (!popup.isShowing) return
        val popupView = popup.contentView as? LinearLayout ?: return
        var foundChild: TextView? = null
        val dp = resources.displayMetrics.density
        val isDark = keyWhite != Color.WHITE
        val highlightColor = if (isDark) "#555555".toColorInt() else "#E1F5FE".toColorInt()
        
        val rect = Rect()
        for (i in 0 until popupView.childCount) {
            val child = popupView.getChildAt(i) as TextView
            child.getGlobalVisibleRect(rect)
            
            // On élargit la zone de détection verticalement pour le confort du swipe
            val hitRect = Rect(rect)
            hitRect.top -= (50 * dp).toInt()
            hitRect.bottom += (80 * dp).toInt()
            
            if (hitRect.contains(rawX.toInt(), rawY.toInt())) {
                foundChild = child
                break
            }
        }
        
        for (i in 0 until popupView.childCount) {
            val child = popupView.getChildAt(i) as TextView
            if (child == foundChild) {
                child.background = GradientDrawable().apply { setColor(highlightColor); cornerRadius = dp * 6f }
                val tag = child.tag as String
                if (selectedAccent != tag) {
                    selectedAccent = tag
                    vibrate(10)
                }
            } else {
                child.background = null
            }
        }
        if (foundChild == null) selectedAccent = null
    }

    private fun handleKeyAction(key: String) {
        if (key.isEmpty()) return
        val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        val autoCorrectionEnabled = prefs.getBoolean("pref_auto_correction", true)

        when (key) {
            "⌫" -> { 
                vibrate(vibrationIntensity.toLong()); 
                inputConnection?.deleteSurroundingText(1, 0); 
                if (currentLanguageId != 0) updateShiftStateByContext()
                
                // Trigger suggestion update after delete
                val before = inputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: ""
                val suggestions = suggestionEngine.getSuggestions(before, currentLanguageId)
                val words = before.split(Regex("[^a-zA-Z\u00C0-\u00FF\u0E00-\u0E7F']")).filter { it.isNotEmpty() }
                updateSuggestions(suggestions, words.lastOrNull() ?: "")
            }
            "⏎" -> {
                vibrate(vibrationIntensity.toLong())
                val action = currentEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) inputConnection?.performEditorAction(action)
                else { 
                    inputConnection?.commitText("\n", 1)
                    val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
                    val autoCaps = prefs.getBoolean("pref_auto_caps", true)
                    if (currentLanguageId != 0 && !isCapsLock && autoCaps) { 
                        isShift = true; loadKeyboard() 
                    } else if (!autoCaps && isShift && !isCapsLock) {
                        isShift = false; loadKeyboard()
                    }
                }
            }
            "⇧" -> {
                if (currentLanguageId == 0) { isShift = !isShift; isCapsLock = false }
                else { if (!isShift && !isCapsLock) isShift = true else if (isShift && !isCapsLock) { isCapsLock = true; isShift = false } else { isCapsLock = false; isShift = false } }
                vibrate(vibrationIntensity.toLong()); loadKeyboard()
            }
            "123", "?123" -> { vibrate(vibrationIntensity.toLong()); isNumber = true; isSymbol = false; isEmoji = false; isToolsPanel = false; loadKeyboard() }
            "=\\<" -> { vibrate(vibrationIntensity.toLong()); isSymbol = true; isNumber = false; isEmoji = false; isToolsPanel = false; loadKeyboard() }
            "ABC" -> { vibrate(vibrationIntensity.toLong()); isNumber = false; isSymbol = false; isEmoji = false; isToolsPanel = false; loadKeyboard() }
            "🙂" -> { vibrate(vibrationIntensity.toLong()); isEmoji = true; isToolsPanel = false; loadKeyboard() }
            "GLOBE" -> {
                vibrate(vibrationIntensity.toLong()); val active = getActiveLanguageIds()
                if (active.size > 1) currentLanguageId = active[(active.indexOf(currentLanguageId) + 1) % active.size]
                isNumber = false; isSymbol = false; isEmoji = false; isToolsPanel = false
                if (currentLanguageId != 0) updateShiftStateByContext() else isShift = false
                isCapsLock = false; loadKeyboard()
            }
            " " -> {
                val ic = inputConnection; val now = System.currentTimeMillis()
                val showSuggestions = KeyboardSettings.isWordSuggestionsEnabled(context)
                val autoCorrectionEnabled = KeyboardSettings.isAutoCorrectionEnabled(context)
                
                // Action Espace : Insère le mot de la case MILIEU
                if (autoCorrectionEnabled && showSuggestions) {
                    val before = ic?.getTextBeforeCursor(50, 0)?.toString() ?: ""
                    val words = before.split(Regex("[^a-zA-Z\u00C0-\u00FF\u0E00-\u0E7F']")).filter { it.isNotEmpty() }
                    val currentTypedWord = if (!before.endsWith(" ")) words.lastOrNull() ?: "" else ""
                    
                    if (currentTypedWord.isNotEmpty()) {
                        // Le milieu est soit bestCorrection soit currentTypedWord (voir updateSuggestions)
                        val centerCorrection = suggestionEngine.getAutoCorrection(currentTypedWord, currentLanguageId)
                        
                        if (centerCorrection != null && centerCorrection.lowercase() != currentTypedWord.lowercase()) {
                            ic?.deleteSurroundingText(currentTypedWord.length, 0)
                            ic?.commitText(centerCorrection, 1)
                            lastAutoCorrection = Pair(currentTypedWord, centerCorrection)
                        } else {
                            lastAutoCorrection = null
                        }
                    } else {
                        lastAutoCorrection = null
                    }
                }

                if (KeyboardSettings.isDoubleSpaceToPeriodEnabled(context) && lastSpaceTime > 0L && (now - lastSpaceTime) < 300L) {
                    ic?.deleteSurroundingText(1, 0); ic?.commitText(". ", 1); lastSpaceTime = 0L
                } else { ic?.commitText(" ", 1); lastSpaceTime = now }
                vibrate(vibrationIntensity.toLong())
                updateShiftStateByContext()
                
                // Record word & update suggestions for next word prediction
                val afterSpace = ic?.getTextBeforeCursor(100, 0)?.toString() ?: ""
                val allWords = afterSpace.trim().split(Regex("[^a-zA-Z\u00C0-\u00FF\u0E00-\u0E7F']")).filter { it.isNotEmpty() }
                if (allWords.isNotEmpty()) {
                    val wordToRecord = allWords.last()
                    val prevWord = if (allWords.size >= 2) allWords[allWords.size - 2] else null
                    suggestionEngine.recordWord(wordToRecord, prevWord, currentLanguageId)
                }
                updateSuggestions(suggestionEngine.getSuggestions(ic?.getTextBeforeCursor(50, 0)?.toString() ?: "", currentLanguageId), "")
            }
            "DEL" -> {
                val ic = inputConnection
                
                // Annulation : Retour arrière restaure la version Verbatim si fait juste après une correction
                if (lastAutoCorrection != null) {
                    val before = ic?.getTextBeforeCursor(50, 0)?.toString() ?: ""
                    if (before.endsWith(lastAutoCorrection!!.second + " ")) {
                        ic?.deleteSurroundingText(lastAutoCorrection!!.second.length + 1, 0)
                        ic?.commitText(lastAutoCorrection!!.first, 1)
                        lastAutoCorrection = null
                        return
                    } else if (before.endsWith(lastAutoCorrection!!.second)) {
                        ic?.deleteSurroundingText(lastAutoCorrection!!.second.length, 0)
                        ic?.commitText(lastAutoCorrection!!.first, 1)
                        lastAutoCorrection = null
                        return
                    }
                }
                lastAutoCorrection = null
                
                ic?.deleteSurroundingText(1, 0)
                val before = ic?.getTextBeforeCursor(50, 0)?.toString() ?: ""
                val words = before.split(Regex("[^a-zA-Z\u00C0-\u00FF\u0E00-\u0E7F']")).filter { it.isNotEmpty() }
                updateSuggestions(suggestionEngine.getSuggestions(before, currentLanguageId), 
                                  if (before.isNotEmpty() && !before.endsWith(" ")) words.last() else "")
                vibrate(vibrationIntensity.toLong())
            }
            else -> {
                lastAutoCorrection = null
                lastSpaceTime = 0L; val text = if (isModernMode && currentLanguageId == 0) getModernChar(key) else key
                val charToCommit = if (isShift || isCapsLock) text.uppercase() else text
                inputConnection?.commitText(charToCommit, 1)
                vibrate(vibrationIntensity.toLong())

                // Trigger suggestion update
                val before = inputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: ""
                val words = before.split(Regex("[^a-zA-Z\u00C0-\u00FF\u0E00-\u0E7F']")).filter { it.isNotEmpty() }
                updateSuggestions(suggestionEngine.getSuggestions(before, currentLanguageId), words.lastOrNull() ?: "")

                val isPunctuation = key == "?" || key == "!" || key == "."
                if (isPunctuation) {
                    if (currentLanguageId != 0) { // Pas thaï (FR/EN)
                        inputConnection?.commitText(" ", 1)
                    }
                    if (isNumber || isSymbol) {
                        isNumber = false
                        isSymbol = false
                        loadKeyboard()
                    } else if (currentLanguageId != 0) {
                        updateShiftStateByContext()
                    }
                } else if (isShift && !isCapsLock) {
                    isShift = false
                    loadKeyboard()
                } else if (currentLanguageId != 0) {
                    updateShiftStateByContext()
                }
            }
        }
    }

    fun updateShiftStateByContext() {
        if (currentLanguageId == 0 || isNumber || isSymbol || isEmoji || isToolsPanel || isCapsLock || isDeleting) return
        
        val prefs = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
        val autoCaps = prefs.getBoolean("pref_auto_caps", true)
        
        if (!autoCaps) {
            if (isShift && !isCapsLock) {
                isShift = false
                loadKeyboard()
            }
            return
        }

        try {
            val ic = inputConnection ?: return
            val before = ic.getTextBeforeCursor(20, 0) ?: ""
            val t = before.toString().trimEnd()
            val newShift = if (t.isEmpty()) true else {
                t.endsWith(".") || t.endsWith("?") || t.endsWith("!") || t.endsWith("\n")
            }
            if (isShift != newShift) { isShift = newShift; loadKeyboard() }
        } catch (e: Exception) {}
    }

    private fun getModernChar(char: String): String = if (char == "ก") "ก°" else if (char == "ข") "ข°" else char
    private fun vibrate(d: Long) { if (d > 0 && vibrationIntensity > 0) try { (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.vibrate(d) } catch (e: Exception) {} }
    private fun romanize(c: String): String = when (c) {
        "ร" -> "r" ; "จ" -> "dj" ; "ก" -> "k" ; "ข", "ฃ", "ค", "ฅ", "ฆ" -> "kh" ; "ง" -> "ng"
        "ช", "ฌ", "ฉ" -> "ch" ; "ส", "ซ", "ศ", "ษ" -> "s" ; "ด", "ฎ" -> "d" ; "ต", "ฏ" -> "t"
        "ถ", "ท", "ธ", "ฑ", "ฒ", "ฐ" -> "th" ; "น", "ณ" -> "n" ; "บ" -> "b" ; "ป" -> "p"
        "พ", "ภ", "ผ" -> "ph" ; "ฟ", "ฝ" -> "f" ; "ม" -> "m" ; "ย", "ญ" -> "y"
        "ฤ", "ฦ" -> "rue" ; "ล", "ฬ" -> "l" ; "ว" -> "w" ; "ห", "ฮ" -> "h" ; "อ" -> "o"
        "ำ" -> "am" ; "ุ" -> "ou" ; "ู" -> "ouu" ; "ึ" -> "ue" ; "ื" -> "uue" ; "ั" -> "a" ; "ี" -> "ii"
        "ใ", "ไ" -> "ai" ; "เ" -> "éé" ; "แ" -> "èè" ; "โ" -> "ô" ; "๑" -> "1"
        "๒" -> "2" ; "๓" -> "3" ; "๔" -> "4" ; "๕" -> "5" ; "๖" -> "6" ; "๗" -> "7"
        "๘" -> "8" ; "๙" -> "9" ; "๐" -> "0" ; "า" -> "aa" ; "ิ" -> "i" ; else -> ""
    }
}
