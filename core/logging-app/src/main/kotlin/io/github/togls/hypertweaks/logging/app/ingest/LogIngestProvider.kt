package io.github.togls.hypertweaks.logging.app.ingest

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import io.github.togls.hypertweaks.logging.api.LogIngestResult
import io.github.togls.hypertweaks.logging.api.LogProtocol
import io.github.togls.hypertweaks.logging.app.AppLogRuntime

class LogIngestProvider : ContentProvider() {
    private val decoder = LogBatchDecoder()

    override fun onCreate(): Boolean {
        val providerContext = context ?: return false
        AppLogRuntime.initialize(providerContext)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != LogProtocol.MethodAppendLogs) {
            return LogProtocol.encodeResult(LogIngestResult(0, 0, false, "unsupported_method"))
        }
        val envelope = decoder.decode(extras).getOrElse {
            return LogProtocol.encodeResult(LogIngestResult(0, 0, false, "invalid_batch"))
        }
        val validator = CallerValidator(
            packagesForUid = { uid -> context?.packageManager?.getPackagesForUid(uid) },
        )
        if (validator.validate(Binder.getCallingUid(), envelope.senderPackage).isFailure) {
            return LogProtocol.encodeResult(
                LogIngestResult(0, envelope.events.size, false, "caller_rejected"),
            )
        }
        val acceptedCount = AppLogRuntime.ingest(envelope.events)
        return LogProtocol.encodeResult(
            LogIngestResult(
                acceptedCount = acceptedCount,
                rejectedCount = envelope.events.size - acceptedCount,
                retryable = acceptedCount < envelope.events.size,
                errorCode = if (acceptedCount < envelope.events.size) "buffer_full" else null,
            ),
        )
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
