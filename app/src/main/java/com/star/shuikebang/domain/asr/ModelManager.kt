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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    data object NotDownloaded : DownloadState()
    data class Downloading(val progress: Int, val fileName: String = "") : DownloadState()
    data object Ready : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * 管理 Zipformer-Transducer 双语流式模型的下载和存储。
 *
 * 模型: sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20
 * 来源: https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20
 *
 * 文件结构 (解压后):
 *   sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/
 *     ├── encoder-epoch-99-avg-1.int8.onnx  (~65MB)
 *     ├── decoder-epoch-99-avg-1.onnx       (~2MB)
 *     ├── joiner-epoch-99-avg-1.int8.onnx   (~10MB)
 *     └── tokens.txt
 *
 * 优势: 有标点、断句准确、中英双语、真流式 endpoint detection
 */
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

    private val modelName = "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20"
    private val archiveUrl =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$modelName.tar.bz2"

    fun isModelReady(): Boolean {
        val dir = File(modelsDir, modelName)
        return File(dir, "encoder-epoch-99-avg-1.int8.onnx").exists() &&
                File(dir, "decoder-epoch-99-avg-1.onnx").exists() &&
                File(dir, "joiner-epoch-99-avg-1.int8.onnx").exists() &&
                File(dir, "tokens.txt").exists()
    }

    /** Returns the absolute path to the model directory */
    fun getModelDir(): String = File(modelsDir, modelName).absolutePath

    suspend fun ensureModelReady(): Result<String> = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _downloadState.value = DownloadState.Ready
            return@withContext Result.success(getModelDir())
        }

        modelsDir.mkdirs()

        try {
            val targetDir = File(modelsDir, modelName)
            if (!targetDir.exists()) {
                _downloadState.value = DownloadState.Downloading(0, "下载语音模型...")

                val archiveFile = File(modelsDir, "$modelName.tar.bz2")
                downloadFile(archiveUrl, archiveFile)

                _downloadState.value = DownloadState.Downloading(100, "解压模型文件...")
                extractTarBz2(archiveFile, modelsDir)
                archiveFile.delete()
            }

            if (!isModelReady()) {
                throw RuntimeException("模型文件不完整")
            }

            _downloadState.value = DownloadState.Ready
            Result.success(getModelDir())
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
}
