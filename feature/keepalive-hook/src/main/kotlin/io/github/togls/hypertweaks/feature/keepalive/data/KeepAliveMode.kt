package io.github.togls.hypertweaks.feature.keepalive.data

enum class KeepAliveMode(
    val value: String,
) {
    // 只做 OOM 优先级保护，不拦截 kill / forceStop。
    OomOnly("oom_only"),
    // 只拦截后台清理 / 智能省电清理，尽量不影响用户手动结束应用。
    Conservative("conservative"),

    // 激进模式，额外拦截 forceStop / ProcessRecord.kill 等强杀路径。
    Aggressive("aggressive"),
    ;

    companion object {
        val Default = OomOnly

        fun fromValue(value: String?): KeepAliveMode {
            return entries.firstOrNull { mode -> mode.value == value }
                ?: Default
        }
    }
}
