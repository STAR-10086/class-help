package com.star.shuikebang.domain.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import javax.inject.Inject
import javax.inject.Singleton

data class AsrResult(
    val text: String,
    val isFinal: Boolean = true
)

/**
 * 基于 sherpa-onnx OnlineRecognizer 的真流式 ASR 引擎。
 *
 * 使用 Zipformer-Transducer 双语流式模型 (INT8)：
 * - encoder.int8.onnx + decoder.onnx + joiner.int8.onnx + tokens.txt
 * - 中英双语，有标点，断句准确
 * - 模型自带 endpoint detection 自动断句
 *
 * 流程: AudioRecord → acceptWaveform() → decode() → getResult()
 *       → isEndpoint() → 如果是句尾: 取结果, reset stream
 */
@Singleton
class AsrEngine @Inject constructor() {

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var isInitialized = false

    fun initialize(modelDir: String) {
        if (isInitialized) return

        Log.i(TAG, "Initializing streaming ASR engine: $modelDir")

        val config = OnlineRecognizerConfig(
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelDir/encoder-epoch-99-avg-1.int8.onnx",
                    decoder = "$modelDir/decoder-epoch-99-avg-1.onnx",
                    joiner = "$modelDir/joiner-epoch-99-avg-1.int8.onnx",
                ),
                tokens = "$modelDir/tokens.txt",
                numThreads = 2,
                provider = "cpu",
                debug = false,
                modelType = "zipformer"
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search"
        )

        recognizer = OnlineRecognizer(config = config)
        stream = recognizer!!.createStream()

        isInitialized = true
        Log.i(TAG, "Streaming ASR engine initialized (Zipformer Transducer)")
    }

    /**
     * 喂入一段音频，返回识别结果。
     * 如果检测到句尾断句，返回该句最终文本并自动 reset stream。
     *
     * @param samples 16kHz 16bit PCM 转换后的 float 数组
     * @param callback 回调，isFinal=true 表示一个完整句子
     */
    fun processAudioChunk(samples: FloatArray, callback: (AsrResult) -> Unit) {
        val currentRecognizer = recognizer ?: return
        val currentStream = stream ?: return

        currentStream.acceptWaveform(samples, SAMPLE_RATE)

        while (currentRecognizer.isReady(currentStream)) {
            currentRecognizer.decode(currentStream)
        }

        if (currentRecognizer.isEndpoint(currentStream)) {
            val result = currentRecognizer.getResult(currentStream)
            if (result.text.isNotBlank()) {
                callback(AsrResult(text = result.text.trim(), isFinal = true))
            }
            currentRecognizer.reset(currentStream)
        } else {
            val result = currentRecognizer.getResult(currentStream)
            if (result.text.isNotBlank()) {
                callback(AsrResult(text = result.text.trim(), isFinal = false))
            }
        }
    }

    fun reset() {
        recognizer?.reset(stream!!)
    }

    fun release() {
        stream?.release()
        recognizer?.release()
        stream = null
        recognizer = null
        isInitialized = false
    }

    fun isActive(): Boolean = isInitialized

    companion object {
        private const val TAG = "AsrEngine"
        const val SAMPLE_RATE = 16000
    }
}
