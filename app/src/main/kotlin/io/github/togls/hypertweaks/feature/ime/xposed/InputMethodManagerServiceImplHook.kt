package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.view.inputmethod.InputMethodManager
import io.github.libxposed.api.XposedInterface
import io.github.togls.hypertweaks.core.xposed.HookContext
import java.lang.reflect.Method

class InputMethodManagerServiceImplHook(
    context: HookContext
) {

    private val module = context.module
    private val log = context.log

    @SuppressLint("PrivateApi")
    fun install(classLoader: ClassLoader) {
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip InputMethodManagerServiceImplHook: class not found", error)
        }.getOrNull() ?: return

        val method = runCatching {
            targetClass.getDeclaredMethod(
                "isCallingBetweenCustomIME",
                Context::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
            ).apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip InputMethodManagerServiceImplHook: isCallingBetweenCustomIME not found",
                error,
            )
        }.getOrNull() ?: return

        module.hook(method)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val args = chain.args
                val result = chain.proceed()

                if (result is Boolean && !result && shouldTreatAsCallingBetweenCustomIme(args)) {
                    return@intercept true
                }

                result
            }

        log.i("hooked InputMethodManagerServiceImpl#isCallingBetweenCustomIME")
    }

    private fun shouldTreatAsCallingBetweenCustomIme(args: List<Any?>): Boolean {
        return runCatching {
            if (args.size < 2) {
                return@runCatching false
            }

            val context = args[0] as? Context
                ?: return@runCatching false

            val uid = args[1] as? Int
                ?: return@runCatching false

            val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as? InputMethodManager
                ?: return@runCatching false

            val currentInputMethodInfo = getCurrentInputMethodInfo(inputMethodManager)
                ?: return@runCatching false

            val currentPackageName = getPackageName(currentInputMethodInfo)
                ?: return@runCatching false

            val packagesForUid = context.packageManager.getPackagesForUid(uid)
                ?: return@runCatching false

            packagesForUid.any { packageName ->
                packageName == currentPackageName
            }
        }.onFailure { error ->
            log.e("check isCallingBetweenCustomIME failed", error)
        }.getOrDefault(false)
    }

    private fun getCurrentInputMethodInfo(inputMethodManager: InputMethodManager): Any? {
        val method = inputMethodManager.javaClass.findDeclaredMethod(
            name = "getCurrentInputMethodInfo",
            parameterCount = 0,
        ) ?: return null

        return method.invoke(inputMethodManager)
    }

    private fun getPackageName(inputMethodInfo: Any): String? {
        val method = inputMethodInfo.javaClass.findDeclaredMethod(
            name = "getPackageName",
            parameterCount = 0,
        ) ?: return null

        return method.invoke(inputMethodInfo) as? String
    }

    private fun Class<*>.findDeclaredMethod(
        name: String,
        parameterCount: Int,
    ): Method? {
        var current: Class<*>? = this

        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.size == parameterCount
            }

            if (method != null) {
                method.isAccessible = true
                return method
            }

            current = current.superclass
        }

        return null
    }

    private companion object {
        private const val TARGET_CLASS_NAME =
            "com.android.server.inputmethod.InputMethodManagerServiceImpl"
    }
}