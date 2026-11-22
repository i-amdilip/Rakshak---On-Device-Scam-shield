package com.example.integrations.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/**
 * Wraps Android's native [SpeechRecognizer] on the main looper to stream live call/speakerphone
 * speech into local on-device scam and impersonation analysis.
 */
class LiveVoiceScamMonitor(
    private val context: Context,
    private val onPartialTranscript: (String) -> Unit,
    private val onFinalTranscriptSegment: (String) -> Unit,
    private val onRmsChanged: (Float) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onErrorMessage: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var keepListening: Boolean = false
    private var currentLocaleTag: String = "en-IN"

    fun start(localeTag: String = "en-IN") {
        mainHandler.post {
            if (!hasRecordAudioPermission(context)) {
                onErrorMessage("Microphone permission is required for live voice scam detection.")
                onListeningStateChanged(false)
                return@post
            }
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onErrorMessage("On-device speech recognition service is unavailable on this device. You can paste a call transcript below.")
                onListeningStateChanged(false)
                return@post
            }
            currentLocaleTag = localeTag
            keepListening = true
            startSingleSession()
        }
    }

    fun stop() {
        mainHandler.post {
            keepListening = false
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            onListeningStateChanged(false)
            onRmsChanged(0f)
        }
    }

    fun destroy() {
        stop()
    }

    private fun startSingleSession() {
        if (!keepListening) return
        runCatching { speechRecognizer?.destroy() }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningStateChanged(true)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                onRmsChanged(rmsdB.coerceIn(0f, 12f) / 12f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val shouldRestart = keepListening && (
                    error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    )
                if (shouldRestart) {
                    mainHandler.postDelayed({ startSingleSession() }, 250L)
                    return
                }
                keepListening = false
                onListeningStateChanged(false)
                onRmsChanged(0f)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error — check microphone availability."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech service network unavailable. Use transcript paste for offline inspection."
                    else -> "Voice listener stopped (code $error)."
                }
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    onErrorMessage(msg)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val top = matches?.firstOrNull()?.trim().orEmpty()
                if (top.isNotEmpty()) {
                    onFinalTranscriptSegment(top)
                }
                onPartialTranscript("")
                if (keepListening) {
                    mainHandler.postDelayed({ startSingleSession() }, 180L)
                } else {
                    onListeningStateChanged(false)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim().orEmpty()
                if (partial.isNotEmpty()) {
                    onPartialTranscript(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocaleTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching {
            recognizer.startListening(intent)
        }.onFailure { err ->
            keepListening = false
            onListeningStateChanged(false)
            onErrorMessage(err.localizedMessage ?: "Unable to start speech recognizer.")
        }
    }

    companion object {
        fun hasRecordAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
