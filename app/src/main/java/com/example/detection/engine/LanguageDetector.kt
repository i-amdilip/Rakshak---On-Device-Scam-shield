package com.example.detection.engine

data class LanguageDetectionResult(
    val code: String,          // "en", "hi", "mr", "bn", "ta", "te", "gu", "kn", "ml", "pa", "es", "fr", "hinglish", "mixed", "unsupported"
    val displayName: String,
    val unsupportedScriptWarning: Boolean
)

object LanguageDetector {

    private val marathiDistinctMarkers = setOf(
        "आहे", "नाही", "तुमचे", "तुमचा", "तुमच्या", "करा", "नका", "सांगा", "पाठवा", "तातडीने", "अटक", "खाते", "कोणालाही"
    )

    private val spanishMarkers = setOf(
        "cuenta", "bloqueada", "verifique", "inmediatamente", "premio", "ganado", "transferencia",
        "codigo", "código", "cuelgue", "arresto", "usted", "banco", "ahora"
    )

    private val frenchMarkers = setOf(
        "compte", "bloqué", "vérifiez", "immédiatement", "virement", "loterie", "gagnée",
        "raccrochez", "mandat", "arrêt", "banque", "votre", "avec"
    )

    fun detect(
        normalizedText: String,
        hinglishMarkers: List<String>,
        manualHint: String = "auto"
    ): LanguageDetectionResult {
        when (manualHint.lowercase()) {
            "en" -> return LanguageDetectionResult("en", "English", false)
            "hi" -> return LanguageDetectionResult("hi", "Hindi (हिन्दी)", false)
            "hinglish" -> return LanguageDetectionResult("hinglish", "Hinglish", false)
            "mr" -> return LanguageDetectionResult("mr", "Marathi (मराठी)", false)
            "bn" -> return LanguageDetectionResult("bn", "Bengali (বাংলা)", false)
            "ta" -> return LanguageDetectionResult("ta", "Tamil (தமிழ்)", false)
            "te" -> return LanguageDetectionResult("te", "Telugu (తెలుగు)", false)
            "gu" -> return LanguageDetectionResult("gu", "Gujarati (ગુજરાતી)", false)
            "kn" -> return LanguageDetectionResult("kn", "Kannada (ಕನ್ನಡ)", false)
            "ml" -> return LanguageDetectionResult("ml", "Malayalam (മലയാളം)", false)
            "pa" -> return LanguageDetectionResult("pa", "Punjabi (ਪੰਜਾਬੀ)", false)
            "es" -> return LanguageDetectionResult("es", "Spanish (Español)", false)
            "fr" -> return LanguageDetectionResult("fr", "French (Français)", false)
        }

        var devanagariCount = 0
        var bengaliCount = 0
        var tamilCount = 0
        var teluguCount = 0
        var gujaratiCount = 0
        var kannadaCount = 0
        var malayalamCount = 0
        var gurmukhiCount = 0
        var latinCount = 0
        var otherScriptCount = 0

        for (ch in normalizedText) {
            if (!ch.isLetter()) continue
            when {
                ch in '\u0900'..'\u097F' -> devanagariCount++
                ch in '\u0980'..'\u09FF' -> bengaliCount++
                ch in '\u0A00'..'\u0A7F' -> gurmukhiCount++
                ch in '\u0A80'..'\u0AFF' -> gujaratiCount++
                ch in '\u0B80'..'\u0BFF' -> tamilCount++
                ch in '\u0C00'..'\u0C7F' -> teluguCount++
                ch in '\u0C80'..'\u0CFF' -> kannadaCount++
                ch in '\u0D00'..'\u0D7F' -> malayalamCount++
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '\u00C0'..'\u00FF' -> latinCount++
                else -> otherScriptCount++
            }
        }

        val supportedRegionalMax = listOf(
            "bn" to bengaliCount,
            "ta" to tamilCount,
            "te" to teluguCount,
            "gu" to gujaratiCount,
            "kn" to kannadaCount,
            "ml" to malayalamCount,
            "pa" to gurmukhiCount
        ).maxByOrNull { it.second }

        val regionalCountTotal = bengaliCount + tamilCount + teluguCount + gujaratiCount + kannadaCount + malayalamCount + gurmukhiCount
        val totalLetters = devanagariCount + regionalCountTotal + latinCount + otherScriptCount
        if (totalLetters == 0) {
            return LanguageDetectionResult("en", "English / Symbols", false)
        }

        val otherRatio = otherScriptCount.toFloat() / totalLetters
        if (otherRatio > 0.35f) {
            return LanguageDetectionResult(
                code = "unsupported",
                displayName = "Unsupported / Other Script",
                unsupportedScriptWarning = true
            )
        }

        if (supportedRegionalMax != null && supportedRegionalMax.second >= 4 &&
            supportedRegionalMax.second.toFloat() / totalLetters >= 0.25f
        ) {
            val (code, name) = when (supportedRegionalMax.first) {
                "bn" -> "bn" to "Bengali (বাংলা)"
                "ta" -> "ta" to "Tamil (தமிழ்)"
                "te" -> "te" to "Telugu (తెలుగు)"
                "gu" -> "gu" to "Gujarati (ગુજરાતી)"
                "kn" -> "kn" to "Kannada (ಕನ್ನಡ)"
                "ml" -> "ml" to "Malayalam (മലയാളം)"
                "pa" -> "pa" to "Punjabi (ਪੰਜਾਬੀ)"
                else -> "en" to "Multilingual"
            }
            return LanguageDetectionResult(code, name, false)
        }

        val devRatio = devanagariCount.toFloat() / totalLetters
        val latinRatio = latinCount.toFloat() / totalLetters

        if (devRatio >= 0.45f) {
            val isMarathi = marathiDistinctMarkers.count { normalizedText.contains(it) } >= 2
            return when {
                isMarathi -> LanguageDetectionResult("mr", "Marathi (मराठी)", false)
                latinCount >= 12 && latinRatio >= 0.20f -> LanguageDetectionResult("mixed", "Hindi-English Mixed", false)
                else -> LanguageDetectionResult("hi", "Hindi (हिन्दी)", false)
            }
        }

        if (devanagariCount >= 6 && latinCount >= 10) {
            return LanguageDetectionResult("mixed", "Hindi-English Mixed", false)
        }

        val tokens = normalizedText.split(Regex("[^\\p{L}0-9]+")).filter { it.isNotEmpty() }.toSet()
        val markerSet = hinglishMarkers.map { it.lowercase() }.toSet()
        val hinglishHits = tokens.count { it in markerSet }
        if (hinglishHits >= 2) {
            return LanguageDetectionResult("hinglish", "Hinglish", false)
        }

        val spanishHits = tokens.count { it in spanishMarkers }
        if (spanishHits >= 2) {
            return LanguageDetectionResult("es", "Spanish (Español)", false)
        }

        val frenchHits = tokens.count { it in frenchMarkers }
        if (frenchHits >= 2) {
            return LanguageDetectionResult("fr", "French (Français)", false)
        }

        return LanguageDetectionResult("en", "English", false)
    }
}
