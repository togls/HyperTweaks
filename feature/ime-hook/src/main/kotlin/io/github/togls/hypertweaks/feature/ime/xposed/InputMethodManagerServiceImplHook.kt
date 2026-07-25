package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.view.inputmethod.InputMethodManager
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import java.lang.reflect.Method

class InputMethodManagerServiceImplHook(
    context: HookContext
) {

    private val engine = context.engine
    private val log = context.log

    @SuppressLint("PrivateApi")
    internal fun install(classLoader: ClassLoader): List<ImeTargetInstallResult> {
        logImeTargetResolveStarted(log, Target)
        val targetClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip InputMethodManagerServiceImplHook: class not found", error)
        }.getOrNull() ?: return skipped("InputMethodManagerServiceImpl class not found")

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
        }.getOrNull()
            ?: return skipped("InputMethodManagerServiceImpl.isCallingBetweenCustomIME not found")

        val installResult = installImeTarget(Target, log) {
            engine.hook(method) { chain ->
                val args = chain.args
                val originalResult = chain.proceed()
                val shouldOverride = preserveOriginalOnFailure(
                    log = log,
                    event = "InputMethodManagerServiceImpl.isCallingBetweenCustomIME",
                    originalValue = false,
                ) {
                    shouldTreatAsCallingBetweenCustomIme(args)
                }

                if (originalResult is Boolean && !originalResult && shouldOverride) {
                    return@hook true
                }

                originalResult
            }
            log.i("hooked InputMethodManagerServiceImpl#isCallingBetweenCustomIME")
        }
        return listOf(installResult)
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

    private fun skipped(reason: String): List<ImeTargetInstallResult> {
        return listOf(skipImeTarget(Target, reason, log))
    }

    private companion object {
        private const val Target =
            "input_method_manager_service_impl.is_calling_between_custom_ime"
        private const val TARGET_CLASS_NAME =
            "com.android.server.inputmethod.InputMethodManagerServiceImpl"
    }
}
