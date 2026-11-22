package com.example.detection.voice

import com.example.detection.engine.RuleBasedScamEngine
import com.example.domain.models.RiskLevel
import com.example.domain.models.ScanInput
import com.example.domain.models.ScanResult
import com.example.domain.models.Source

enum class VoiceImpersonationPersona(
    val titleEn: String,
    val titleHi: String,
    val guidanceEn: String,
    val guidanceHi: String
) {
    LAW_ENFORCEMENT(
        titleEn = "Police / CBI / Cyber Cell Impersonation",
        titleHi = "पुलिस / सीबीआई / साइबर सेल का झूठा रूप",
        guidanceEn = "Police or courts NEVER arrest over video/phone calls or demand 'safe account' transfers. Hang up immediately.",
        guidanceHi = "पुलिस या कोर्ट कभी वीडियो/फोन कॉल पर गिरफ्तारी या 'सेफ अकाउंट' में पैसे नहीं मांगते। तुरंत कॉल काटें।"
    ),
    BANK_KYC_OFFICER(
        titleEn = "Bank / RBI / KYC Officer Impersonation",
        titleHi = "बैंक / आरबीआई / केवाईसी अधिकारी का रूप",
        guidanceEn = "Banks NEVER ask for OTP, UPI PIN, CVV, or remote screen sharing over a call.",
        guidanceHi = "बैंक कॉल पर कभी ओटीपी, यूपीआई पिन या स्क्रीन-शेयरिंग नहीं मांगते।"
    ),
    TELECOM_TRAI(
        titleEn = "TRAI / SIM Disconnection Impersonation",
        titleHi = "ट्राई / सिम बंद करने की धमकी वाला कॉल",
        guidanceEn = "TRAI does not call citizens directly to disconnect mobile numbers or demand ID/money.",
        guidanceHi = "ट्राई नंबर बंद करने के लिए व्यक्तिगत कॉल करके पैसे या दस्तावेज़ नहीं मांगता।"
    ),
    COURIER_CUSTOMS(
        titleEn = "Courier / Customs Illegal Parcel Impersonation",
        titleHi = "कूरियर / कस्टम पार्सल का झूठा डर",
        guidanceEn = "Customs or FedEx will not connect you to 'police on Skype' to clear a seized parcel.",
        guidanceHi = "कस्टम या कूरियर कंपनी पार्सल छुड़ाने के लिए पुलिस वीडियो कॉल या पैसे नहीं मांगती।"
    ),
    FAMILY_EMERGENCY_AI_VOICE(
        titleEn = "Family / AI Voice-Clone Emergency Scam",
        titleHi = "परिवार / एआई आवाज़ नकल आपातकालीन ठगी",
        guidanceEn = "Hang up and dial your family member directly on their saved phone number before sending any money.",
        guidanceHi = "पैसे भेजने से पहले कॉल काटकर अपने परिजन के सहेजे गए नंबर पर खुद कॉल करके पुष्टि करें।"
    ),
    REMOTE_TECH_SUPPORT(
        titleEn = "Customer Care / Remote Screen-Share Impersonation",
        titleHi = "कस्टमर केयर / स्क्रीन-शेयरिंग ठगी",
        guidanceEn = "Never install AnyDesk, TeamViewer, QuickSupport, or read out a 9-digit screen code.",
        guidanceHi = "कॉल पर किसी के कहने से AnyDesk, TeamViewer या कोई रिमोट ऐप इंस्टॉल न करें।"
    ),
    UTILITY_DISCONNECTION(
        titleEn = "Electricity / Utility Disconnection Threat",
        titleHi = "बिजली कनेक्शन काटने की धमकी",
        guidanceEn = "Check your official electricity board app directly instead of calling back mobile numbers.",
        guidanceHi = "मोबाइल नंबर पर कॉल या भुगतान न करें; आधिकारिक बिजली ऐप में बिल जांचें।"
    ),
    NONE(
        titleEn = "No Impersonation Persona Detected",
        titleHi = "कोई छद्म अधिकारी पैटर्न नहीं मिला",
        guidanceEn = "Keep verifying unexpected callers via official numbers saved independently.",
        guidanceHi = "अनजान कॉलर की पुष्टि हमेशा आधिकारिक नंबर से ही करें।"
    )
}

data class VoiceImpersonationReport(
    val persona: VoiceImpersonationPersona = VoiceImpersonationPersona.NONE,
    val matchedVoiceCues: List<String> = emptyList(),
    val recognizedTactics: List<String> = emptyList(),
    val riskScore: Int = 0,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val contextualHeadline: String = "",
    val immediateAction: String = "",
    val scanResult: ScanResult? = null
)

object VoiceImpersonationAnalyzer {

    private data class PersonaRule(
        val persona: VoiceImpersonationPersona,
        val cues: List<String>,
        val minScoreFloor: Int
    )

    private val personaRules = listOf(
        PersonaRule(
            persona = VoiceImpersonationPersona.LAW_ENFORCEMENT,
            cues = listOf(
                "digital arrest", "cbi", "crime branch", "cyber cell", "narcotics",
                "enforcement directorate", "ed officer", "mumbai police", "delhi police",
                "arrest warrant", "money laundering", "supreme court", "stay on video call",
                "skype", "giraftar", "giraftari", "case darj", "fir darj", "सीबीआई", "पुलिस", "गिरफ्तार", "सायबर पोलीस", "अटक वॉरंट"
            ),
            minScoreFloor = 78
        ),
        PersonaRule(
            persona = VoiceImpersonationPersona.REMOTE_TECH_SUPPORT,
            cues = listOf(
                "anydesk", "teamviewer", "quicksupport", "rustdesk", "airdroid",
                "9 digit code", "9-digit", "share your screen", "screen share", "remote support",
                "screen dikhao", "स्क्रीन शेयर"
            ),
            minScoreFloor = 82
        ),
        PersonaRule(
            persona = VoiceImpersonationPersona.FAMILY_EMERGENCY_AI_VOICE,
            cues = listOf(
                "voice clone", "kidnapped", "in the hospital", "police station send money",
                "bail money right now", "accident urgent transfer", "don't tell mom",
                "don't tell dad", "mama help me", "papa bacha lo", "अस्पताल में", "एक्सीडेंट"
            ),
            minScoreFloor = 76
        ),
        PersonaRule(
            persona = VoiceImpersonationPersona.COURIER_CUSTOMS,
            cues = listOf(
                "fedex", "dhl", "blue dart", "customs", "seized parcel", "illegal parcel",
                "drugs found", "passport in parcel", "mumbai customs", "पार्सल", "कस्टम"
            ),
            minScoreFloor = 72
        ),
        PersonaRule(
            persona = VoiceImpersonationPersona.TELECOM_TRAI,
            cues = listOf(
                "trai", "telecom regulatory", "sim will be blocked", "number will be disconnected",
                "aadhaar linked to illegal sim", "sim band", "नंबर बंद", "ट्राई"
            ),
            minScoreFloor = 70
        ),
        PersonaRule(
            persona = VoiceImpersonationPersona.BANK_KYC_OFFICER,
            cues = listOf(
                "bank manager", "rbi officer", "reserve bank", "sbi officer", "hdfc verification",
                "kyc department", "account frozen", "card blocked", "read the otp",
                "otp batao", "ओटीपी बताएं", "बैंक अधिकारी", "खाता बंद"
            ),
            minScoreFloor = 68
        ),
        PersonaRule(
            persona = VoiceImpersonationPersona.UTILITY_DISCONNECTION,
            cues = listOf(
                "electricity officer", "power will be disconnected", "bijli vibhag",
                "bijli kat jayegi", "meter disconnected", "बिजली कट"
            ),
            minScoreFloor = 68
        )
    )

    fun analyzeTranscript(
        transcript: String,
        engine: RuleBasedScamEngine,
        outputLanguage: String = "en"
    ): VoiceImpersonationReport {
        val clean = transcript.trim()
        if (clean.isBlank()) return VoiceImpersonationReport()

        val isHindi = outputLanguage.startsWith("hi", ignoreCase = true)
        val scanResult = engine.analyze(
            ScanInput(text = clean, source = Source.TRANSCRIPT),
            outputLanguage = outputLanguage
        )
        val lower = clean.lowercase()

        var matchedPersona = VoiceImpersonationPersona.NONE
        var bestCues = emptyList<String>()
        var personaFloor = 0

        for (rule in personaRules) {
            val hits = rule.cues.filter { cue -> lower.contains(cue) }
            if (hits.size > bestCues.size || (hits.isNotEmpty() && hits.size == bestCues.size && rule.minScoreFloor > personaFloor)) {
                matchedPersona = rule.persona
                bestCues = hits
                personaFloor = rule.minScoreFloor
            }
        }

        // Only elevate score to impersonation floor when voice cue coincides with pressure/action or multi-cue hit
        val hasActionOrPressure = scanResult.manipulation.isNotEmpty() || scanResult.intents.isNotEmpty()
        val combinedScore = if (matchedPersona != VoiceImpersonationPersona.NONE && (bestCues.size >= 2 || hasActionOrPressure)) {
            maxOf(scanResult.riskScore, personaFloor)
        } else {
            scanResult.riskScore
        }.coerceIn(0, 100)

        val level = when {
            combinedScore >= 80 -> RiskLevel.CRITICAL
            combinedScore >= 60 -> RiskLevel.HIGH
            combinedScore >= 30 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val headline = when {
            level == RiskLevel.CRITICAL || level == RiskLevel.HIGH -> {
                if (matchedPersona != VoiceImpersonationPersona.NONE) {
                    if (isHindi) matchedPersona.titleHi else matchedPersona.titleEn
                } else {
                    if (isHindi) "उच्च जोखिम वाला संदिग्ध कॉल" else "High-Risk Voice Call Scam Warning"
                }
            }
            level == RiskLevel.MEDIUM -> {
                if (isHindi) "संदिग्ध कॉल संकेत — सतर्क रहें" else "Suspicious Call Patterns Detected"
            }
            else -> {
                if (isHindi) "कॉल में कम जोखिम संकेत" else "Low Risk Signals So Far"
            }
        }

        val immediateAction = if (matchedPersona != VoiceImpersonationPersona.NONE && combinedScore >= 30) {
            if (isHindi) matchedPersona.guidanceHi else matchedPersona.guidanceEn
        } else if (combinedScore >= 60) {
            if (isHindi) "कॉल काटें और किसी भी ओटीपी, पिन या पैसे भेजने से पहले परिवार से बात करें।"
            else "Hang up and verify independently before sharing any OTP, PIN, or payment."
        } else {
            if (isHindi) "सुनना जारी रखें — यदि कॉलर जल्दी करे या गोपनीयता मांगे तो तुरंत रोकें।"
            else "Stay alert — stop immediately if the caller demands secrecy, urgency, or payment."
        }

        return VoiceImpersonationReport(
            persona = matchedPersona,
            matchedVoiceCues = bestCues.take(5),
            recognizedTactics = scanResult.manipulation,
            riskScore = combinedScore,
            riskLevel = level,
            contextualHeadline = headline,
            immediateAction = immediateAction,
            scanResult = scanResult.copy(
                riskScore = combinedScore,
                riskLevel = level
            )
        )
    }
}
