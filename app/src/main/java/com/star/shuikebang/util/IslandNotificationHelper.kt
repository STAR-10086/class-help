package com.star.shuikebang.util

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.star.shuikebang.MainActivity
import com.star.shuikebang.ShuikebangApp

object IslandNotificationHelper {

    enum class IslandType { NONE, XIAOMI_HYPER, VIVO_ORIGIN }

    fun detectIslandType(context: Context): IslandType {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
                if (isXiaomiIslandSupported(context)) IslandType.XIAOMI_HYPER else IslandType.NONE
            m.contains("vivo") || m.contains("iqoo") ->
                if (isVivoIslandSupported()) IslandType.VIVO_ORIGIN else IslandType.NONE
            else -> IslandType.NONE
        }
    }

    private fun isXiaomiIslandSupported(context: Context): Boolean {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val m = c.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.java)
            m.invoke(null, "persist.sys.feature.island", false) as Boolean
        } catch (e: Exception) { false }
    }

    private fun getXiaomiFocusProtocolVersion(context: Context): Int {
        return try { Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0) } catch (e: Exception) { 0 }
    }

    fun buildXiaomiIslandNotification(context: Context, isRecording: Boolean, questionCount: Int): Notification {
        val builder = Notification.Builder(context, ShuikebangApp.CHANNEL_ID)
            .setContentTitle(if (isRecording) "正在记录课堂" else "课堂记录")
            .setContentText(if (isRecording) { if (questionCount > 0) "已识别 ${questionCount} 个提问" else "识别中..." } else "待机")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(isRecording)
        val pi = android.app.PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(pi)
        val protocol = getXiaomiFocusProtocolVersion(context)
        val t = if (isRecording) "正在记录" else "已停止"
        val c = if (isRecording) { if (questionCount > 0) "已识别 ${questionCount} 个提问" else "识别中..." } else "课堂记录已结束"
        val color = if (questionCount > 0) "#FF6B35" else "#1976D2"
        val qText = if (questionCount > 0) "${questionCount}个提问" else ""
        val islandParams = "{\"param_v2\":{\"protocol\":$protocol,\"business\":\"class_recording\",\"updatable\":true,\"enableFloat\":false,\"ticker\":\"$t\",\"aodTitle\":\"$t - $c\",\"param_island\":{\"islandProperty\":1,\"bigIslandArea\":{\"imageTextInfoLeft\":{\"type\":1,\"miui.focus.paramtextInfo\":{\"frontTitle\":\"$t\",\"title\":\"$qText\",\"content\":\"$c\",\"useHighLight\":${questionCount > 0}}}},\"smallIslandArea\":{\"picInfo\":{\"type\":1}}},\"baseInfo\":{\"title\":\"$t\",\"content\":\"$c\",\"colorTitle\":\"$color\",\"type\":2}}}"
        val n = builder.build()
        n.extras.putString("miui.focus.param", islandParams)
        return n
    }

    private fun isVivoIslandSupported(): Boolean {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val m = c.getDeclaredMethod("get", String::class.java, String::class.java)
            val v = m.invoke(null, "ro.build.version.origin_os", "") as String
            v.isNotEmpty() && v.toFloatOrNull()?.let { it >= 5.0f } == true
        } catch (e: Exception) { false }
    }

    fun buildVivoIslandNotification(context: Context, isRecording: Boolean, questionCount: Int): Notification {
        val sb = Bundle()
        sb.putInt("notification.superx.operation", if (isRecording) 0 else 2)
        sb.putBoolean("notification.superx.showNotify", true)
        sb.putInt("notification.superx.template", 1)
        sb.putString("notification.superx.scene", "METTING")
        val pi = android.app.PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        sb.putParcelable("notification.superx.clickResp", pi)
        val bi = Bundle()
        bi.putCharSequence("notification.superx.baseInfos.title", if (isRecording) "正在记录课堂" else "课堂记录")
        bi.putCharSequence("notification.superx.baseInfos.content", if (isRecording) { if (questionCount > 0) "已识别 ${questionCount} 个提问" else "识别中..." } else "已结束")
        sb.putBundle("notification.superx.baseInfos", bi)
        val id = Bundle()
        id.putInt("leftTemplate", 1)
        id.putInt("rightTemplate", 4)
        val left = Bundle()
        left.putCharSequence("text", if (isRecording) "录音中" else "已停止")
        left.putString("textColor", "#FFFFFF")
        id.putBundle("leftInfo", left)
        val right = Bundle()
        right.putCharSequence("text", if (questionCount > 0) "${questionCount}个提问" else "等待识别")
        right.putString("textColor", "#FFFFFF")
        right.putString("bgColor", if (questionCount > 0) "#FF6B35" else "#1976D2")
        id.putBundle("rightInfo", right)
        id.putBoolean("forceShow", true)
        id.putInt("clickType", 1)
        id.putInt("showTime", 180)
        sb.putBundle("notification.superx.islandData", id)
        val builder = Notification.Builder(context, ShuikebangApp.CHANNEL_ID)
            .setContentTitle(if (isRecording) "正在记录课堂" else "课堂记录")
            .setContentText(if (isRecording) { if (questionCount > 0) "已识别 ${questionCount} 个提问" else "识别中..." } else "待机")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(isRecording)
            .setExtras(sb)
        builder.setContentIntent(pi)
        return builder.build()
    }

    fun sendIslandNotification(context: Context, isRecording: Boolean, questionCount: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = when (detectIslandType(context)) {
            IslandType.XIAOMI_HYPER -> buildXiaomiIslandNotification(context, isRecording, questionCount)
            IslandType.VIVO_ORIGIN -> buildVivoIslandNotification(context, isRecording, questionCount)
            IslandType.NONE -> return
        }
        nm.notify(1, n)
    }
}
