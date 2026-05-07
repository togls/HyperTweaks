package io.github.togls.hypertweaks.core.xposed

object ImePackageMatcher {

    private val imePackages = setOf(
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

    fun matches(packageName: String): Boolean {
        return packageName in imePackages
    }
}