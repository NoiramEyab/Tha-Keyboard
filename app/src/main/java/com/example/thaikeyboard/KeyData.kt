/*
 * Copyright (c) 2026 Marion Bayé. Tous droits réservés.
 * Ce code source et ses ressources graphiques sont la propriété exclusive de Marion Bayé.
 * Toute reproduction, modification ou distribution non autorisée est strictement interdite.
 */
package com.example.thaikeyboard

data class KeyData(val thai: String, val latin: String)

object KeyboardLayouts {
    val THAI = listOf(
        listOf("ๅ", "/", "-", "ภ", "ถ", "ุ", "ึ", "ค", "ต", "จ", "ข", "ช"),
        listOf("ๆ", "ไ", "ำ", "พ", "ะ", "ั", "ี", "ร", "น", "ย", "บ", "ล"),
        listOf("ฟ", "ห", "ก", "ด", "เ", "้", "่", "า", "ส", "ว", "ง", "ฃ"),
        listOf("⇧", "ผ", "ป", "แ", "อ", "ิ", "ื", "ท", "ม", "ใ", "ฝ", "⌫")
    )
    val THAI_SHIFT = listOf(
        listOf("+", "๑", "๒", "๓", "๔", "ู", "฿", "๕", "๖", "๗", "๘", "๙"),
        listOf("๐", "\"", "ฎ", "ฑ", "ธ", "ํ", "๊", "ณ", "ฯ", "ญ", "ฐ", ","),
        listOf("ฤ", "ฆ", "ฏ", "โ", "ฌ", "็", "๋", "ษ", "ศ", "ซ", ".", "ฅ"),
        listOf("⇧", "(", ")", "ฉ", "ฮ", "ฺ", "์", "?", "ฒ", "ฬ", "ฦ", "⌫")
    )

    val FR = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m"),
        listOf("⇧", "w", "x", "c", "v", "b", "n", "'", "⌫")
    )
    val FR_SHIFT = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("A", "Z", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("Q", "S", "D", "F", "G", "H", "J", "K", "L", "M"),
        listOf("⇧", "W", "X", "C", "V", "B", "N", "?", "!", "⌫")
    )

    val EN = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "m"),
        listOf("⇧", "z", "x", "c", "v", "b", "n", "'", "⌫")
    )
    val EN_SHIFT = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "M"),
        listOf("⇧", "Z", "X", "C", "V", "B", "N", "?", "!", "⌫")
    )
}
