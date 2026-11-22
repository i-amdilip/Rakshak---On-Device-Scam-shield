package com.example

import com.example.detection.engine.LanguageDetector
import com.example.detection.engine.RuleBasedScamEngine
import com.example.detection.voice.VoiceImpersonationAnalyzer
import com.example.detection.voice.VoiceImpersonationPersona
import com.example.domain.models.RiskLevel
import com.example.domain.models.ScanInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultilingualAndVoiceDetectionTest {

    private val engine = RuleBasedScamEngine(FileSystemRuleSource())
    private val markers = engine.getRuleBundleOrNull()!!.lexiconHinglish.hinglishMarkers

    @Test
    fun `voice analyzer detects police CBI digital arrest impersonation and social engineering`() {
        val transcript = "This is Mumbai Crime Branch calling. Your Aadhaar parcel had narcotics. " +
            "There is a digital arrest warrant issued against you. Stay on video call on Skype, " +
            "don't tell your family, and transfer bail money right now to our safe account."

        val report = VoiceImpersonationAnalyzer.analyzeTranscript(transcript, engine)
        assertEquals(VoiceImpersonationPersona.LAW_ENFORCEMENT, report.persona)
        assertTrue("Score was ${report.riskScore}", report.riskScore >= 78)
        assertTrue(report.riskLevel in listOf(RiskLevel.HIGH, RiskLevel.CRITICAL))
        assertTrue(report.recognizedTactics.contains("isolation"))
        assertTrue(report.recognizedTactics.contains("authority"))
        assertTrue(report.matchedVoiceCues.isNotEmpty())
    }

    @Test
    fun `voice analyzer detects remote tech support AnyDesk screen-share impersonation`() {
        val transcript = "Hello, I am from bank customer care. To release your refund, install AnyDesk now and read out the 9 digit code."
        val report = VoiceImpersonationAnalyzer.analyzeTranscript(transcript, engine)
        assertEquals(VoiceImpersonationPersona.REMOTE_TECH_SUPPORT, report.persona)
        assertEquals(RiskLevel.CRITICAL, report.riskLevel)
        assertTrue(report.riskScore >= 80)
    }

    @Test
    fun `voice analyzer detects family AI voice clone emergency pressure`() {
        val transcript = "Papa bacha lo, I am in the hospital after an accident. Don't tell mom, please send bail money right now via UPI."
        val report = VoiceImpersonationAnalyzer.analyzeTranscript(transcript, engine)
        assertEquals(VoiceImpersonationPersona.FAMILY_EMERGENCY_AI_VOICE, report.persona)
        assertTrue(report.riskScore >= 76)
        assertTrue(report.riskLevel in listOf(RiskLevel.HIGH, RiskLevel.CRITICAL))
    }

    @Test
    fun `detects Marathi scam message accurately without unsupported script flag`() {
        val marathiScam = "तातडीने सूचना: तुमचे बँक खाते आज रात्री बंद होईल. अटक वॉरंट टाळण्यासाठी कोणालाही सांगू नका, ओटीपी सांगा आणि पैसे पाठवा."
        val lang = LanguageDetector.detect(marathiScam.lowercase(), markers)
        assertEquals("mr", lang.code)
        assertFalse(lang.unsupportedScriptWarning)

        val res = engine.analyze(ScanInput(marathiScam))
        assertFalse(res.unsupportedLanguageWarning)
        assertTrue("Score was ${res.riskScore}", res.riskScore >= 60)
        assertTrue(res.manipulation.contains("isolation") || res.manipulation.contains("urgency"))
    }

    @Test
    fun `detects Bengali scam message accurately`() {
        val bengaliScam = "অবিলম্বে সতর্কতা: আপনার ব্যাংক অ্যাকাউন্ট আজ রাতেই ব্লক হয়ে যাবে। ওটিপি শেয়ার করুন এবং টাকা পাঠান http://bank-verify.xyz"
        val lang = LanguageDetector.detect(bengaliScam.lowercase(), markers)
        assertEquals("bn", lang.code)
        assertFalse(lang.unsupportedScriptWarning)

        val res = engine.analyze(ScanInput(bengaliScam))
        assertFalse(res.unsupportedLanguageWarning)
        assertTrue("Score was ${res.riskScore}", res.riskScore >= 60)
        assertTrue(res.riskLevel in listOf(RiskLevel.HIGH, RiskLevel.CRITICAL))
    }

    @Test
    fun `detects Tamil scam message accurately`() {
        val tamilScam = "உடனடியாக கவனிக்கவும்: உங்கள் வங்கி கணக்கு முடக்கப்படும். யாரிடமும் சொல்லாதீர்கள், ஓடிபி சொல்லுங்கள் மற்றும் பணம் அனுப்பவும்."
        val lang = LanguageDetector.detect(tamilScam.lowercase(), markers)
        assertEquals("ta", lang.code)
        assertFalse(lang.unsupportedScriptWarning)

        val res = engine.analyze(ScanInput(tamilScam))
        assertFalse(res.unsupportedLanguageWarning)
        assertTrue("Score was ${res.riskScore}", res.riskScore >= 60)
    }

    @Test
    fun `detects Spanish phishing message accurately`() {
        val spanishScam = "Último aviso: su cuenta bloqueada será suspendida inmediatamente. Verifique su cuenta y comparta su otp aquí https://banco-ayuda.xyz"
        val lang = LanguageDetector.detect(spanishScam.lowercase(), markers)
        assertEquals("es", lang.code)
        assertFalse(lang.unsupportedScriptWarning)

        val res = engine.analyze(ScanInput(spanishScam))
        assertFalse(res.unsupportedLanguageWarning)
        assertTrue("Score was ${res.riskScore}", res.riskScore >= 60)
    }
}
