package io.github.togls.hypertweaks.feature.keepalive.xposed

import java.lang.reflect.Method

internal fun Throwable.rethrowIfFatal() {
    if (this is Error) throw this
}

internal fun Method.describeSignature(): String {
    val parameters = parameterTypes.joinToString(", ") { parameterType -> parameterType.name }
    return "${declaringClass.name}#$name($parameters): ${returnType.name}"
}

internal fun defaultReturnValue(returnType: Class<*>): Any? {
    return when (returnType) {
        Void.TYPE -> null
        Boolean::class.javaPrimitiveType -> false
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }
}
