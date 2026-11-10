package com.modo.app.ui.minutes

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 회의 받아쓰기. SpeechRecognizer는 한 번에 한 문장만 듣고 멈추므로, 멈출 때마다 다시 시작해 계속 듣는다.
 * 확정된 문장은 onFinal, 말하는 중인 문장은 onPartial. 권한·언어 문제처럼 다시 해도 안 되는 오류만 onFatal.
 */
class SpeechSession(
    private val context: Context,
    private val onFinal: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFatal: (error: Int) -> Unit,
) : RecognitionListener {

    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    var isRunning = false
        private set

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        if (Build.VERSION.SDK_INT >= 33) {
            // 길게 이어 듣기 (지원하는 엔진에서만) + 문장 부호
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10 * 60 * 1000L)
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        }
    }

    /** 기기에 음성 인식 서비스가 있는지 */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (isRunning) return
        isRunning = true
        recreate()
        recognizer?.startListening(intent)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun recreate() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(this) }
    }

    /** onError 안에서 바로 다시 시작하면 BUSY가 나므로 조금 뒤에 */
    private fun restart(delayMs: Long, fresh: Boolean = false) {
        handler.postDelayed({
            if (!isRunning) return@postDelayed
            if (fresh) recreate()
            recognizer?.startListening(intent)
        }, delayMs)
    }

    private fun Bundle.best(): String? = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

    override fun onPartialResults(results: Bundle) {
        results.best()?.let(onPartial)
    }

    override fun onSegmentResults(segmentResults: Bundle) {
        segmentResults.best()?.takeIf { it.isNotEmpty() }?.let(onFinal)
    }

    override fun onEndOfSegmentedSession() = restart(100)

    override fun onResults(results: Bundle) {
        results.best()?.takeIf { it.isNotEmpty() }?.let(onFinal)
        restart(100)
    }

    override fun onError(error: Int) {
        if (!isRunning) return
        when (error) {
            // 조용할 때 나오는 정상적인 끊김
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(100)
            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> restart(500, fresh = true)
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> restart(5_000, fresh = true)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                stop()
                onFatal(error)
            }
            else -> restart(1_000, fresh = true)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
