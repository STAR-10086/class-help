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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    data object NotDownloaded : DownloadState()
    data class Downloading(val progress: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
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
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    private val modelsDir: File
        get() = File(context.filesDir, "models")

    private val modelFiles = listOf(
        ModelFile(
            name = "silero_vad.onnx",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
        ),
        ModelFile(
            name = "model.int8.onnx",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/model.int8.onnx"
        ),
        ModelFile(
            name = "tokens.txt",
            url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/tokens.txt"
        )
    )

    fun isModelReady(): Boolean {
        return modelFiles.all { File(modelsDir, it.name).exists() }
    }

    fun getModelDir(): String = modelsDir.absolutePath

    suspend fun ensureModelReady(): Result<String> = withContext(Dispatchers.IO) {
        if (isModelReady()) {
            _downloadState.value = DownloadState.Ready
            return@withContext Result.success(modelsDir.absolutePath)
        }

        modelsDir.mkdirs()

        try {
            for (modelFile in modelFiles) {
                val targetFile = File(modelsDir, modelFile.name)
                if (targetFile.exists()) continue

                downloadFile(modelFile.url, targetFile)
            }

            _downloadState.value = DownloadState.Ready
            Result.success(modelsDir.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            val msg = when {
                e.message?.contains("HTTP") == true -> "网络请求失败: ${e.message}"
                e.message?.contains("timeout", true) == true -> "下载超时，请检查网络连接"
                e.message?.contains("connect", true) == true -> "无法连接到下载服务器，请检查网络"
                else -> "模型下载失败: ${e.message}"
            }
            _downloadState.value = DownloadState.Error(msg)
            Result.failure(e)
        }
    }

    private fun downloadFile(url: String, targetFile: File) {
        _downloadState.value = DownloadState.Downloading(0, 0, 0)

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

                    _downloadState.value = DownloadState.Downloading(
                        progress = progress,
                        bytesDownloaded = totalRead,
                        totalBytes = totalBytes
                    )
                }
            }
        }

        Log.i(TAG, "Downloaded ${targetFile.name} (${targetFile.length()} bytes)")
    }

    fun deleteModels() {
        modelsDir.deleteRecursively()
        _downloadState.value = DownloadState.NotDownloaded
    }

    companion object {
        private const val TAG = "ModelManager"
    }

    private data class ModelFile(
        val name: String,
        val url: String
    )
}
