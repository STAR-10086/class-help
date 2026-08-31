package com.star.shuikebang.domain.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import javax.inject.Inject
import javax.inject.Singleton

data class AsrResult(
    val text: String,
    val isFinal: Boolean = true
)

@Singleton
class AsrEngine @Inject constructor() {

    private var vad: Vad? = null
    private var recognizer: OfflineRecognizer? = null
    private var isInitialized = false

    fun initialize(modelDir: String) {
        if (isInitialized) return

        Log.i(TAG, "Initializing ASR engine with model dir: $modelDir")

        // Initialize Silero VAD
        val vadConfig = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "$modelDir/silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.8f,
                minSpeechDuration = 0.5f,
                windowSize = 512,
                maxSpeechDuration = 10.0f
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 2,
            provider = "cpu",
            debug = false
        )
        vad = Vad(config = vadConfig)

        // Initialize SenseVoice offline recognizer
        val asrConfig = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = "$modelDir/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/model.int8.onnx",
                    language = "",  // auto-detect
                    useInverseTextNormalization = true
                ),
                tokens = "$modelDir/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/tokens.txt",
                numThreads = 2,
                provider = "cpu"
            )
        )
        recognizer = OfflineRecognizer(config = asrConfig)

        isInitialized = true
        Log.i(TAG, "ASR engine initialized successfully")
    }

    fun processAudioChunk(samples: FloatArray, callback: (AsrResult) -> Unit) {
        val currentVad = vad ?: return
        val currentRecognizer = recognizer ?: return

        currentVad.acceptWaveform(samples)

        while (!currentVad.empty()) {
            val segment = currentVad.front()
            currentVad.pop()

            // Recognize this speech segment with SenseVoice
            val stream = currentRecognizer.createStream()
            stream.acceptWaveform(segment.samples, sampleRate = SAMPLE_RATE)
            currentRecognizer.decode(stream)
            val result = currentRecognizer.getResult(stream)
            stream.release()

            if (result.text.isNotBlank()) {
                callback(AsrResult(text = result.text.trim(), isFinal = true))
            }
        }
    }

    fun reset() {
        vad?.reset()
    }

    fun release() {
        vad?.release()
        recognizer?.release()
        vad = null
        recognizer = null
        isInitialized = false
    }

    fun isActive(): Boolean = isInitialized

    companion object {
        private const val TAG = "AsrEngine"
        const val SAMPLE_RATE = 16000
    }
}
