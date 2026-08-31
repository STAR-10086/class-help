package com.star.shuikebang.domain.asr

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    data object NotDownloaded : DownloadState()
    data class Downloading(val progress: Int, val fileName: String = "") : DownloadState()
    data object Ready : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.NotDownloaded)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .callTimeout(600, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val modelsDir: File
        get() = File(context.filesDir, "models")

    // VAD model: single file download
    private val vadFile = ModelDownload(
        name = "silero_vad.onnx",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
    )

    // SenseVoice: tar.bz2 archive
    private val senseVoiceArchive = ModelDownload(
        name = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2"
    )

    private val senseVoiceDir = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17"

    fun isModelReady(): Boolean {
        val vad = File(modelsDir, vadFile.name)
        val model = File(modelsDir, "$senseVoiceDir/model.int8.onnx")
        val tokens = File(modelsDir, "$senseVoiceDir/tokens.txt")
        return vad.exists() && model.exists() && tokens.exists()
    }

    fun getModelDir(): String = modelsDir.absolutePath

    suspend fun ensureModelReady(): Result<String> = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _downloadState.value = DownloadState.Ready
            return@withContext Result.success(modelsDir.absolutePath)
        }

        modelsDir.mkdirs()

        try {
            // Step 1: Download VAD model (single file)
            val vadTarget = File(modelsDir, vadFile.name)
            if (!vadTarget.exists()) {
                _downloadState.value = DownloadState.Downloading(0, "silero_vad.onnx")
                downloadFile(vadFile.url, vadTarget)
            }

            // Step 2: Download SenseVoice tar.bz2 and extract
            val modelFile = File(modelsDir, "$senseVoiceDir/model.int8.onnx")
            if (!modelFile.exists()) {
                _downloadState.value = DownloadState.Downloading(0, "SenseVoice 模型")
                val archiveFile = File(modelsDir, senseVoiceArchive.name)
                downloadFile(senseVoiceArchive.url, archiveFile)

                _downloadState.value = DownloadState.Downloading(100, "解压模型文件...")
                extractTarBz2(archiveFile, modelsDir)
                archiveFile.delete() // Clean up archive
            }

            _downloadState.value = DownloadState.Ready
            Result.success(modelsDir.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            val msg = when {
                e.message?.contains("HTTP") == true -> "下载失败: ${e.message}"
                e.message?.contains("timeout", true) == true -> "下载超时，请检查网络连接"
                e.message?.contains("connect", true) == true -> "无法连接到下载服务器"
                e.message?.contains("ENOSPC", true) == true -> "存储空间不足"
                else -> "模型下载失败: ${e.message}"
            }
            _downloadState.value = DownloadState.Error(msg)
            Result.failure(e)
        }
    }

    private fun downloadFile(url: String, targetFile: File) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw RuntimeException("HTTP ${response.code}: 下载 ${targetFile.name} 失败")
        }

        val body = response.body ?: throw RuntimeException("Empty response body")
        val totalBytes = body.contentLength()

        body.byteStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead

                    val progress = if (totalBytes > 0) {
                        (totalRead * 100 / totalBytes).toInt()
                    } else 0

                    _downloadState.value = DownloadState.Downloading(progress, targetFile.name)
                }
            }
        }

        Log.i(TAG, "Downloaded ${targetFile.name} (${targetFile.length()} bytes)")
    }

    private fun extractTarBz2(archiveFile: File, destDir: File) {
        // Use Java ProcessBuilder to call tar command
        val process = ProcessBuilder(
            "tar", "xjf", archiveFile.absolutePath,
            "-C", destDir.absolutePath
        ).start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val error = process.errorStream.bufferedReader().readText()
            throw RuntimeException("解压失败 (exit code $exitCode): $error")
        }

        Log.i(TAG, "Extracted ${archiveFile.name} to ${destDir.absolutePath}")
    }

    fun deleteModels() {
        modelsDir.deleteRecursively()
        _downloadState.value = DownloadState.NotDownloaded
    }

    companion object {
        private const val TAG = "ModelManager"
    }

    private data class ModelDownload(
        val name: String,
        val url: String
    )
}
