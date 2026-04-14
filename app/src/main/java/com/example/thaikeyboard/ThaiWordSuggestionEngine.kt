/*
 * Copyright (c) 2026 Marion Bayé. Tous droits réservés.
 * Ce code source et ses ressources graphiques sont la propriété exclusive de Marion Bayé.
 * Toute reproduction, modification ou distribution non autorisée est strictement interdite.
 */
package com.example.thaikeyboard

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import kotlin.math.abs

class ThaiWordSuggestionEngine(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    private var thaiUserFreqMap: MutableMap<String, Int> = mutableMapOf()
    private var frenchUserFreqMap: MutableMap<String, Int> = mutableMapOf()
    private var englishUserFreqMap: MutableMap<String, Int> = mutableMapOf()

    // N-grammes (Bigrammes) : "mot1" -> { "mot2" -> fréquence }
    private var thaiNGrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    private var frenchNGrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()
    private var englishNGrams: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()

    private var thaiDictionary: List<String> = emptyList()
    private var frenchDictionary: List<String> = emptyList()
    private var englishDictionary: List<String> = emptyList()

    init {
        loadDictionaries()
        loadFreqMaps()
    }

    private fun loadDictionaries() {
        thaiDictionary   = loadWordsFromAssets("words_th.txt")
        frenchDictionary = loadWordsFromAssets("words_fr.txt")
        englishDictionary = loadWordsFromAssets("words_en.txt")
    }

    private fun loadWordsFromAssets(fileName: String): List<String> {
        val words = mutableListOf<String>()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open(fileName)))
            var line = reader.readLine()
            while (line != null) {
                val word = line.trim()
                if (word.isNotEmpty()) words.add(word)
                line = reader.readLine()
            }
            reader.close()
        } catch (e: Exception) { e.printStackTrace() }
        return words
    }

    private fun loadFreqMaps() {
        fun load(key: String): MutableMap<String, Int> {
            val json = prefs.getString(key, null) ?: return mutableMapOf()
            val type = object : TypeToken<MutableMap<String, Int>>() {}.type
            return gson.fromJson(json, type) ?: mutableMapOf()
        }
        thaiUserFreqMap    = load("thai_word_freq")
        frenchUserFreqMap  = load("french_word_freq")
        englishUserFreqMap = load("english_word_freq")
        
        fun loadNGrams(key: String): MutableMap<String, MutableMap<String, Int>> {
            val json = prefs.getString(key, null) ?: return mutableMapOf()
            val type = object : TypeToken<MutableMap<String, MutableMap<String, Int>>>() {}.type
            return gson.fromJson(json, type) ?: mutableMapOf()
        }
        thaiNGrams = loadNGrams("thai_ngrams")
        frenchNGrams = loadNGrams("french_ngrams")
        englishNGrams = loadNGrams("english_ngrams")
    }

    private fun saveFreqMaps() {
        prefs.edit()
            .putString("thai_word_freq",    gson.toJson(thaiUserFreqMap))
            .putString("french_word_freq",  gson.toJson(frenchUserFreqMap))
            .putString("english_word_freq", gson.toJson(englishUserFreqMap))
            .putString("thai_ngrams",       gson.toJson(thaiNGrams))
            .putString("french_ngrams",     gson.toJson(frenchNGrams))
            .putString("english_ngrams",    gson.toJson(englishNGrams))
            .apply()
    }

    // Supprime les accents pour la comparaison phonétique (FR/EN)
    private fun stripAccents(s: String): String {
        val normalized = Normalizer.normalize(s, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
    }

    private var sessionDictionary: MutableSet<String> = mutableSetOf()

    /**
     * Ajoute un mot au dictionnaire de session (Verbatim) pour éviter sa correction.
     */
    fun addToSessionDictionary(word: String) {
        sessionDictionary.add(word.lowercase())
    }

    /**
     * Vérifie si un mot est considéré comme valide (dans le dictionnaire, appris, session ou schéma d'apostrophe).
     */
    fun isValidWord(word: String, langId: Int): Boolean {
        if (word.isEmpty()) return true
        val wordLower = word.lowercase()
        if (sessionDictionary.contains(wordLower)) return true
        
        val dictionary = when (langId) {
            0 -> thaiDictionary
            1 -> frenchDictionary
            2 -> englishDictionary
            else -> return true
        }
        val userFreqMap = when (langId) {
            0 -> thaiUserFreqMap
            1 -> frenchUserFreqMap
            2 -> englishUserFreqMap
            else -> emptyMap<String, Int>()
        }
        
        if (dictionary.any { it.lowercase() == wordLower }) return true
        if (userFreqMap.keys.any { it.lowercase() == wordLower }) return true
        
        // Apostrophes : traiter comme entités valides pour les contractions courantes
        if (langId != 0 && word.contains('\'')) {
            val parts = word.split('\'')
            if (parts.size == 2 && parts[1].isNotEmpty()) {
                val prefix = parts[0].lowercase()
                val knownPrefixes = listOf("j", "l", "t", "s", "c", "m", "n", "d", "qu", "lorsqu", "puisqu")
                if (knownPrefixes.contains(prefix)) return true
            }
        }
        return false
    }

    /**
     * Retourne une correction automatique pour le dernier mot tapé.
     * Respecte la règle : si le mot est valide, pas de remplacement par un mot plus long.
     */
    fun getAutoCorrection(word: String, langId: Int): String? {
        if (langId == 0) return null
        if (word.length < 2) return null
        
        // Si le mot est valide, on ne le corrige pas (ex: "je" reste "je", pas "jeune")
        if (isValidWord(word, langId)) return null

        val dictionary = when (langId) {
            1 -> frenchDictionary
            2 -> englishDictionary
            else -> return null
        }
        val userFreqMap: Map<String, Int> = when (langId) {
            1 -> frenchUserFreqMap
            2 -> englishUserFreqMap
            else -> emptyMap()
        }

        val wordLower = word.lowercase()
        val wordStripped = stripAccents(wordLower)

        // Chercher une correction (Distance 1 ou 2 selon longueur)
        data class Fix(val word: String, val userFreq: Int, val dictIdx: Int, val dist: Int)
        val fixes = mutableListOf<Fix>()
        val maxDist = if (word.length <= 4) 1 else 2

        val candidates = (dictionary.take(3000) + userFreqMap.keys).distinct()
        
        candidates.forEachIndexed { idx, candidate ->
            // On autorise une différence de longueur allant jusqu'à maxDist
            if (abs(candidate.length - word.length) > maxDist) return@forEachIndexed
            
            val cStripped = stripAccents(candidate.lowercase())
            val dist = levenshtein(wordStripped, cStripped)
            
            if (dist > 0 && dist <= maxDist) {
                fixes.add(Fix(candidate, userFreqMap[candidate] ?: 0, idx, dist))
            }
        }

        if (fixes.isEmpty()) return null

        // Meilleur candidat : proximité (dist), puis usage (userFreq), puis rang dico
        val best = fixes.sortedWith(
            compareBy<Fix> { it.dist }.thenByDescending { it.userFreq }.thenBy { it.dictIdx }
        ).first()

        return when {
            word[0].isUpperCase() -> best.word.replaceFirstChar { it.uppercase() }
            else -> best.word
        }
    }

    /** Levenshtein complet (pas sur préfixe) — pour l'autocorrection de mot entier */
    private fun levenshtein(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (m == 0) return n; if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1]
                       else minOf(dp[i-1][j] + 1, dp[i][j-1] + 1, dp[i-1][j-1] + 1)
        }
        return dp[m][n]
    }

    /**
     * Retourne les prédictions de bigrammes purs pour le mot suivant.
     */
    fun getBigramPredictions(previousWord: String?, langId: Int): List<String> {
        if (previousWord == null) return emptyList()
        val nGrams: Map<String, Map<String, Int>> = when (langId) {
            0 -> thaiNGrams
            1 -> frenchNGrams
            2 -> englishNGrams
            else -> emptyMap()
        }
        val nextWords = nGrams[previousWord.lowercase()] ?: return emptyList()
        return nextWords.entries.sortedByDescending { it.value }.take(5).map { it.key }
    }

    fun getSuggestions(inputBefore: String, langId: Int): List<String> {
        val words = inputBefore.split(Regex("[^a-zA-Z\u00C0-\u00FF\u0E00-\u0E7F']")).filter { it.isNotEmpty() }
        val lastWord = if (inputBefore.isNotEmpty() && !inputBefore.endsWith(" ")) words.lastOrNull() ?: "" else ""
        val previousWord = if (lastWord.isEmpty() && words.isNotEmpty()) words.last().lowercase() 
                          else if (words.size >= 2) words[words.size - 2].lowercase() 
                          else null

        // Si on vient de taper un espace, on propose des prédictions de bigrammes
        if (lastWord.isEmpty()) {
            return getBigramPredictions(previousWord, langId)
        }

        val dictionary = when (langId) {
            0 -> thaiDictionary
            1 -> frenchDictionary
            2 -> englishDictionary
            else -> emptyList()
        }
        val userFreqMap: Map<String, Int> = when (langId) {
            0 -> thaiUserFreqMap
            1 -> frenchUserFreqMap
            2 -> englishUserFreqMap
            else -> emptyMap()
        }
        val nGrams: Map<String, Map<String, Int>> = when (langId) {
            0 -> thaiNGrams
            1 -> frenchNGrams
            2 -> englishNGrams
            else -> emptyMap()
        }

        val fragmentLower = lastWord.lowercase()
        val fragmentStripped = if (langId != 0) stripAccents(fragmentLower) else fragmentLower

        // Tolérance aux fautes selon la longueur du fragment
        val maxDist = when {
            fragmentLower.length <= 2 -> 0
            fragmentLower.length <= 4 -> 1
            fragmentLower.length <= 6 -> 2
            else -> 2
        }

        data class Candidate(val word: String, val dist: Int, val contextFreq: Int, val userFreq: Int, val dictIdx: Int)
        val candidates = mutableListOf<Candidate>()
        val seen = mutableSetOf<String>()

        fun addCandidate(word: String, dictIdx: Int) {
            val wLower = word.lowercase()
            // Suggestion dynamique : Inclure le mot exact s'il y a des suggestions contextuelles fortes, 
            // sinon on l'exclut pour proposer des corrections.
            if (word.length < fragmentLower.length) return

            val wStripped = if (langId != 0) stripAccents(wLower) else wLower

            val dist = when {
                wLower.startsWith(fragmentLower) -> 0
                langId != 0 && wStripped.startsWith(fragmentStripped) -> 0
                else -> levenshteinPrefix(fragmentStripped, wStripped)
            }

            if (dist <= maxDist && seen.add(wLower)) {
                val contextFreq = if (previousWord != null) nGrams[previousWord]?.get(word) ?: 0 else 0
                candidates.add(Candidate(word, dist, contextFreq, userFreqMap[word] ?: 0, dictIdx))
            }
        }

        // Dictionnaire principal
        dictionary.take(5000).forEachIndexed { idx, word -> addCandidate(word, idx) }
        // Mots de l'utilisateur
        userFreqMap.keys.forEach { word -> addCandidate(word, Int.MAX_VALUE) }

        return candidates
            .sortedWith(
                compareBy<Candidate> { it.dist }
                .thenByDescending { it.contextFreq }
                .thenByDescending { it.userFreq }
                .thenBy { it.dictIdx }
            )
            .take(5)
            .map { it.word }
    }

    /**
     * Distance de Levenshtein entre le fragment et le préfixe du mot de même longueur.
     * Permet de détecter les fautes de frappe sans pénaliser la fin du mot non encore tapée.
     */
    private fun levenshteinPrefix(fragment: String, word: String): Int {
        val s = fragment
        val t = word.take(fragment.length + 1)
        val m = s.length; val n = t.length
        if (m == 0) return 0
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (s[i-1] == t[j-1]) dp[i-1][j-1]
                       else minOf(dp[i-1][j] + 1, dp[i][j-1] + 1, dp[i-1][j-1] + 1)
        }
        return dp[m][n]
    }

    fun recordWord(word: String, previousWord: String?, langId: Int) {
        if (word.length < 2) return
        val freqMap = when (langId) {
            0 -> thaiUserFreqMap
            1 -> frenchUserFreqMap
            2 -> englishUserFreqMap
            else -> null
        } ?: return
        val nGramMap = when (langId) {
            0 -> thaiNGrams
            1 -> frenchNGrams
            2 -> englishNGrams
            else -> null
        } ?: return

        // Record frequency
        freqMap[word] = (freqMap[word] ?: 0) + 1
        
        // Record N-Gram
        if (previousWord != null) {
            val pLower = previousWord.lowercase()
            val nextWords = nGramMap.getOrPut(pLower) { mutableMapOf() }
            nextWords[word] = (nextWords[word] ?: 0) + 1
            if (nextWords.size > 50) {
                nextWords.entries.sortedBy { it.value }.take(10).forEach { nextWords.remove(it.key) }
            }
        }

        if (freqMap.size > 1000) {
            freqMap.entries.sortedBy { it.value }.take(200).forEach { freqMap.remove(it.key) }
        }
        saveFreqMaps()
    }
}
