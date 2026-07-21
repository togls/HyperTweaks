package io.github.togls.hypertweaks.logging.app.ingest

import android.os.Bundle
import io.github.togls.hypertweaks.logging.api.LogBatchEnvelope
import io.github.togls.hypertweaks.logging.api.LogProtocol

class LogBatchDecoder {
    fun decode(bundle: Bundle?): Result<LogBatchEnvelope> {
        if (bundle == null) return Result.failure(IllegalArgumentException("Missing request bundle"))
        return LogProtocol.decodeBatch(bundle)
    }
}
