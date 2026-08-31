package com.star.shuikebang

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class ShuikebangApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                pw.println("Thread: ${thread.name}")
                pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                pw.println()
                throwable.printStackTrace(pw)
                val crashLog = sw.toString()

                // Write to file
                val crashFile = File(filesDir, "crash.log")
                crashFile.writeText(crashLog)
                Log.e(TAG, "Crash saved to ${crashFile.absolutePath}")
                Log.e(TAG, crashLog)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save crash log", e)
            }
            // Let the default handler show the crash dialog
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Read the last crash log, if any. Returns null if no crash log exists.
     */
    fun getLastCrashLog(): String? {
        val crashFile = File(filesDir, "crash.log")
        return if (crashFile.exists()) {
            val log = crashFile.readText()
            crashFile.delete() // Clear after reading
            log
        } else null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "课堂记录",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "课堂记录服务通知"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "ShuikebangApp"
        const val CHANNEL_ID = "recording_service"
    }
}
