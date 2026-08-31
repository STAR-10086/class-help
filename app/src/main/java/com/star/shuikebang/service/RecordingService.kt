package com.star.shuikebang.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.star.shuikebang.MainActivity
import com.star.shuikebang.R
import com.star.shuikebang.ShuikebangApp
import com.star.shuikebang.domain.asr.AsrEngine
import com.star.shuikebang.domain.asr.ModelManager
import com.star.shuikebang.domain.question.QuestionDetector
import com.star.shuikebang.domain.session.SessionManager
import com.star.shuikebang.util.IslandNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class RecordingEvent {
    data class TranscriptLine(val text: String, val isQuestion: Boolean) : RecordingEvent()
    data class LiveTextUpdate(val text: String) : RecordingEvent()
    data class QuestionDetected(val text: String) : RecordingEvent()
    data class StatusChanged(val isRecording: Boolean) : RecordingEvent()
    data class Error(val message: String) : RecordingEvent()
}

/**
 * 前台录音服务。
 *
 * 音频流程:
 *   AudioRecord (16kHz 16bit mono)
 *   → 流式送入 AsrEngine (OnlineRecognizer)
 *   → 检测到句尾 → 句子文本
 *   → QuestionDetector 判断是否提问
 *   → 存入数据库 + UI回调
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var asrEngine: AsrEngine
    @Inject lateinit var modelManager: ModelManager
    @Inject lateinit var questionDetector: QuestionDetector
    @Inject lateinit var sessionManager: SessionManager

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isRecording = false
    private var questionCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "RecordingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (isRecording) return
        serviceScope.launch {
            val modelResult = modelManager.ensureModelReady()
            if (modelResult.isFailure) {
                _events.emit(RecordingEvent.Error("Model not ready"))
                return@launch
            }
            asrEngine.initialize(modelManager.getModelDir())
            sessionManager.startNewSession()
            withContext(Dispatchers.Main) {
                startForeground(NOTIFICATION_ID, createNotification())
                acquireWakeLock()
            }
            isRecording = true
            _status.value = true
            _events.emit(RecordingEvent.StatusChanged(true))
            startAudioRecording()
        }
    }

    private fun startAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        audioRecord?.startRecording()

        recordingThread = Thread {
            // 每次读 300ms 音频 (4800 samples @ 16kHz)
            val readSize = (0.3 * SAMPLE_RATE).toInt()
            val buffer = ShortArray(readSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val samples = FloatArray(read) { buffer[it] / 32768.0f }
                    processAudioChunk(samples)
                }
            }
        }.apply {
            name = "AudioRecord-Thread"
            start()
        }
    }

    private fun processAudioChunk(samples: FloatArray) {
        asrEngine.processAudioChunk(samples) { result ->
            serviceScope.launch {
                if (result.text.isBlank()) return@launch

                // 只有 final（完整句子）才存数据库
                if (result.isFinal) {
                    val questionResult = questionDetector.detect(result.text)
                    val isQuestion = questionResult.isQuestion
                    val lineId = sessionManager.addTranscriptLine(result.text, isQuestion)
                    if (isQuestion && lineId != null) {
                        sessionManager.addQuestion(result.text, lineId)
                        questionCount++
                        vibrate()
                        _events.emit(RecordingEvent.QuestionDetected(result.text))
                        updateIslandNotification()
                    }
                }

                // 中间结果推给 UI 实时展示（不入库）
                if (!result.isFinal) {
                    _events.emit(RecordingEvent.LiveTextUpdate(result.text))
                }
            }
        }
    }

    private fun updateIslandNotification() {
        try {
            IslandNotificationHelper.sendIslandNotification(this, true, questionCount)
        } catch (e: Exception) {
            Log.w(TAG, "Island notification failed: ${e.message}")
        }
    }

    private fun stopRecording() {
        isRecording = false
        _status.value = false
        try {
            IslandNotificationHelper.sendIslandNotification(this, false, questionCount)
        } catch (e: Exception) { Log.w(TAG, "Island stop notification failed") }
        questionCount = 0
        recordingThread?.join(1000)
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        asrEngine.release()
        serviceScope.launch { sessionManager.endCurrentSession() }
        releaseWakeLock()
        _events.tryEmit(RecordingEvent.StatusChanged(false))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun createNotification(): Notification {
        try {
            val islandType = IslandNotificationHelper.detectIslandType(this)
            if (islandType != IslandNotificationHelper.IslandType.NONE) {
                return when (islandType) {
                    IslandNotificationHelper.IslandType.XIAOMI_HYPER ->
                        IslandNotificationHelper.buildXiaomiIslandNotification(this, true, questionCount)
                    IslandNotificationHelper.IslandType.VIVO_ORIGIN ->
                        IslandNotificationHelper.buildVivoIslandNotification(this, true, questionCount)
                    else -> buildStandardNotification()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Island notification build failed, fallback: ${e.message}")
        }
        return buildStandardNotification()
    }

    private fun buildStandardNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val si = PendingIntent.getService(this, 1,
            Intent(this, RecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, ShuikebangApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.stop_recording), si)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShuikeBang::RecordingLock")
        wakeLock?.acquire(12 * 60 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) stopRecording()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 16000
        const val ACTION_START = "com.star.shuikebang.START_RECORDING"
        const val ACTION_STOP = "com.star.shuikebang.STOP_RECORDING"
        private val _status = MutableStateFlow(false)
        val status: StateFlow<Boolean> = _status.asStateFlow()
        private val _events = MutableSharedFlow<RecordingEvent>(extraBufferCapacity = 64)
        val events: SharedFlow<RecordingEvent> = _events.asSharedFlow()
        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecordingService::class.java).apply { action = ACTION_START })
        }
        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).apply { action = ACTION_STOP })
        }
    }
}
