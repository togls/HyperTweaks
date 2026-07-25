package io.github.togls.hypertweaks.logging.app.ingest

import android.os.Process

class CallerValidator(
    private val packagesForUid: (Int) -> Array<String>?,
    private val allowedPackages: Set<String> = DefaultAllowedPackages,
) {
    fun validate(
        uid: Int,
        callingPid: Int,
        senderPackage: String,
        senderPid: Int,
    ): Result<Unit> = runCatching {
        require(callingPid == senderPid) { "Sender PID does not match Binder calling PID" }
        if (uid == Process.SYSTEM_UID) {
            require(senderPackage in SystemUidPackages) {
                "SYSTEM_UID sender package is not a trusted system context"
            }
            return@runCatching
        }
        val callerPackages = packagesForUid(uid)?.toSet().orEmpty()
        require(senderPackage in callerPackages) { "Sender package does not belong to calling UID" }
        require(senderPackage in allowedPackages) { "Sender package is outside the module scope" }
    }

    companion object {
        val SystemUidPackages = setOf("android")
        val DefaultAllowedPackages = setOf(
            "io.github.togls.hypertweaks",
            "com.google.android.apps.photos",
            "com.google.android.inputmethod.latin",
            "com.baidu.input",
            "com.baidu.input_mi",
            "com.sohu.inputmethod.sogou",
            "com.sohu.inputmethod.sogou.xiaomi",
            "com.iflytek.inputmethod",
            "com.iflytek.inputmethod.miui",
            "com.tencent.wetype",
            "keepass2android.keepass2android",
        )
    }
}
