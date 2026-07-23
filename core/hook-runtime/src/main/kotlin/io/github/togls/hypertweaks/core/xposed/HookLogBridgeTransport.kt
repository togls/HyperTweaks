package io.github.togls.hypertweaks.core.xposed

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Process
import io.github.togls.hypertweaks.logging.api.LogBatchEnvelope
import io.github.togls.hypertweaks.logging.api.LogEvent
import io.github.togls.hypertweaks.logging.api.LogIngestResult
import io.github.togls.hypertweaks.logging.api.LogProtocol
import io.github.togls.hypertweaks.logging.hook.HookBatchTransport
import java.util.UUID

class HookLogBridgeTransport : HookBatchTransport {
    override fun send(events: List<LogEvent>): LogIngestResult {
        val context = CurrentProcessContext.find()
            ?: return LogIngestResult(0, events.size, true, "context_unavailable")
        val envelope = LogBatchEnvelope(
            batchId = UUID.randomUUID().toString(),
            senderPackage = context.packageName,
            senderProcess = Application.getProcessName(),
            senderPid = Process.myPid(),
            events = events,
        )
        val response = context.contentResolver.call(
            Uri.parse("content://${LogProtocol.Authority}"),
            LogProtocol.MethodAppendLogs,
            null,
            LogProtocol.encodeBatch(envelope),
        )
        return LogProtocol.decodeResult(response)
    }
}

// Hook 可能运行在没有 Application 的 system_server；公开 SDK 没有等价的 Context 获取入口。
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
private object CurrentProcessContext {
    fun find(): Context? = currentApplication() ?: systemContext()

    private fun currentApplication(): Context? {
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Context
        }.getOrNull()
    }

    private fun systemContext(): Context? {
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentThread = activityThread.getDeclaredMethod("currentActivityThread").invoke(null)
            activityThread.getDeclaredMethod("getSystemContext").invoke(currentThread) as? Context
        }.getOrNull()
    }
}
