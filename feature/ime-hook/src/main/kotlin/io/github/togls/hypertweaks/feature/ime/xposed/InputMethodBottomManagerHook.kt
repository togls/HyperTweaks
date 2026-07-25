package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.view.inputmethod.InputMethodManager
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
import io.github.togls.hypertweaks.feature.ime.installer.ImeTargetInstallResult
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap

class InputMethodBottomManagerHook(
    private val context: HookContext,
) {
    private val engine = context.engine
    private val log = context.log

    private val hookedClassLoaders = Collections.newSetFromMap(
        IdentityHashMap<ClassLoader, Boolean>(),
    )

    @SuppressLint("PrivateApi")
    internal fun install(classLoader: ClassLoader): List<ImeTargetInstallResult> {
        logImeTargetResolveStarted(log, Target)
        val moduleManagerClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w(
                "skip InputMethodBottomManagerHook: InputMethodModuleManager not found",
                error
            )
        }.getOrNull() ?: return skipped("InputMethodModuleManager class not found")

        val loadDexMethod = runCatching {
            moduleManagerClass.getDeclaredMethod(
                "loadDex",
                ClassLoader::class.java,
                String::class.java,
            ).apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w(
                "skip InputMethodBottomManagerHook: loadDex(ClassLoader, String) not found",
                error,
            )
        }.getOrNull() ?: return skipped("InputMethodModuleManager.loadDex not found")

        val installResult = installImeTarget(Target, log) {
            engine.hook(loadDexMethod) { chain ->
                val result = chain.proceed()

                preserveOriginalOnFailure(log, "InputMethodModuleManager.loadDex", Unit) {
                    val imeModuleClassLoader = chain.args
                        .firstOrNull() as? ClassLoader
                        ?: return@preserveOriginalOnFailure

                    installBottomManagerHookOnce(imeModuleClassLoader)
                }

                result
            }
            log.i("hooked InputMethodModuleManager#loadDex(ClassLoader, String)")
        }
        return listOf(installResult)
    }

    private fun installBottomManagerHookOnce(imeModuleClassLoader: ClassLoader) {
        if (!markClassLoaderForInstall(imeModuleClassLoader)) return

        val bottomManagerClass = runCatching {
            imeModuleClassLoader.loadClass(BOTTOM_MANAGER_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip BottomManager hook: InputMethodBottomManager not found", error)
        }.getOrNull() ?: return

        val getSupportImeMethod = runCatching {
            bottomManagerClass.getDeclaredMethod("getSupportIme").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip BottomManager hook: getSupportIme not found", error)
        }.getOrNull() ?: return

        val bottomViewHelperClass = runCatching {
            imeModuleClassLoader.loadClass(BOTTOM_VIEW_HELPER_CLASS_NAME)
        }.onFailure { error ->
            log.w("skip BottomManager hook: BottomViewHelper not found", error)
        }.getOrNull() ?: return

        val immField = runCatching {
            bottomViewHelperClass.getDeclaredField("mImm").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip BottomManager hook: BottomViewHelper.mImm not found", error)
        }.getOrNull() ?: return

        val bottomViewHelperField = runCatching {
            bottomManagerClass.getDeclaredField("sBottomViewHelper").apply {
                isAccessible = true
            }
        }.onFailure { error ->
            log.w("skip BottomManager hook: sBottomViewHelper not found", error)
        }.getOrNull() ?: return

        hookGetSupportIme(
            getSupportImeMethod = getSupportImeMethod,
            bottomViewHelperField = bottomViewHelperField,
            immField = immField,
        )

        log.i("hooked InputMethodBottomManager#getSupportIme")
    }

    private fun markClassLoaderForInstall(classLoader: ClassLoader): Boolean {
        return synchronized(hookedClassLoaders) {
            hookedClassLoaders.add(classLoader)
        }
    }

    private fun hookGetSupportIme(
        getSupportImeMethod: Method,
        bottomViewHelperField: Field,
        immField: Field,
    ) {
        engine.hook(getSupportImeMethod) { chain ->
            val replacement = preserveOriginalOnFailure(
                log = log,
                event = "InputMethodBottomManager.getSupportIme",
                originalValue = null,
            ) {
                val thisObject = chain.thisObject ?: return@preserveOriginalOnFailure null
                val bottomViewHelper = bottomViewHelperField.get(thisObject)
                    ?: return@preserveOriginalOnFailure null
                val inputMethodManager = immField.get(bottomViewHelper) as? InputMethodManager
                    ?: return@preserveOriginalOnFailure null
                inputMethodManager.enabledInputMethodList
            }
            replacement ?: chain.proceed()
        }
    }

    private fun skipped(reason: String): List<ImeTargetInstallResult> {
        return listOf(skipImeTarget(Target, reason, log))
    }

    private companion object {
        private const val Target = "input_method_bottom_manager.load_dex"
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.InputMethodModuleManager"

        private const val BOTTOM_MANAGER_CLASS_NAME =
            "com.miui.inputmethod.InputMethodBottomManager"

        private const val BOTTOM_VIEW_HELPER_CLASS_NAME =
            $$"com.miui.inputmethod.InputMethodBottomManager$BottomViewHelper"
    }
}
