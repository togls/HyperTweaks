package io.github.togls.hypertweaks.logging.app.ingest

import android.os.Process

class CallerValidator(
    private val packagesForUid: (Int) -> Array<String>?,
    private val allowedPackages: Set<String> = DefaultAllowedPackages,
) {
    fun validate(uid: Int, senderPackage: String): Result<Unit> = runCatching {
        if (uid == Process.SYSTEM_UID) return@runCatching
        val callerPackages = packagesForUid(uid)?.toSet().orEmpty()
        require(senderPackage in callerPackages) { "Sender package does not belong to calling UID" }
        require(senderPackage in allowedPackages) { "Sender package is outside the module scope" }
    }

    companion object {
        val DefaultAllowedPackages = setOf(
            "io.github.togls.hypertweaks",
            "com.google.android.apps.photos",
            "com.android.systemui",
            "com.miui.powerkeeper",
            "com.miui.securitycenter",
            "com.miui.securitycenter.remote",
            "com.lbe.security.miui",
            "com.google.android.inputmethod.latin",
            "com.baidu.input",
            "com.baidu.input_mi",
            "com.sohu.inputmethod.sogou",
            "com.sohu.inputmethod.sogou.xiaomi",
            "com.iflytek.inputmethod",
            "com.iflytek.inputmethod.miui",
            "com.tencent.wetype",
            "keepass2android.keepass2android",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
        )
    }
}
