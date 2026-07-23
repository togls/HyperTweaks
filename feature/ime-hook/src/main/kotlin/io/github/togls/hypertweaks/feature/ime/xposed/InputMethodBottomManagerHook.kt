package io.github.togls.hypertweaks.feature.ime.xposed

import android.annotation.SuppressLint
import android.view.inputmethod.InputMethodManager
import io.github.togls.hypertweaks.core.xposed.HookChain
import io.github.togls.hypertweaks.core.xposed.HookContext
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
    fun install(classLoader: ClassLoader) {
        val moduleManagerClass = runCatching {
            classLoader.loadClass(TARGET_CLASS_NAME)
        }.onFailure { error ->
            log.w(
                "skip InputMethodBottomManagerHook: InputMethodModuleManager not found",
                error
            )
        }.getOrNull() ?: return

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
        }.getOrNull() ?: return

        engine.hook(loadDexMethod) { chain ->
                val result = chain.proceed()

                runCatching {
                    val imeModuleClassLoader = chain.args
                        .firstOrNull() as? ClassLoader
                        ?: return@runCatching

                    installBottomManagerHookOnce(imeModuleClassLoader)
                }.onFailure { error ->
                    log.e("hook InputMethodModuleManager.loadDex failed", error)
                }

                result
            }

        log.i("hooked InputMethodModuleManager#loadDex(ClassLoader, String)")
    }

    private fun installBottomManagerHookOnce(imeModuleClassLoader: ClassLoader) {
        synchronized(hookedClassLoaders) {
            if (!hookedClassLoaders.add(imeModuleClassLoader)) {
                return
            }
        }

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

    private fun hookGetSupportIme(
        getSupportImeMethod: Method,
        bottomViewHelperField: Field,
        immField: Field,
    ) {
        engine.hook(getSupportImeMethod) { chain ->
                runCatching {
                    val thisObject = chain.thisObject
                        ?: return@runCatching null

                    val bottomViewHelper = bottomViewHelperField.get(thisObject)
                        ?: return@runCatching null

                    val inputMethodManager = immField.get(bottomViewHelper) as? InputMethodManager
                        ?: return@runCatching null

                    inputMethodManager.enabledInputMethodList
                }.onFailure { error ->
                    log.e("hook InputMethodBottomManager.getSupportIme failed", error)
                }.getOrNull()
                    ?: chain.proceed()
            }
    }

    private companion object {
        private const val TARGET_CLASS_NAME =
            "android.inputmethodservice.InputMethodModuleManager"

        private const val BOTTOM_MANAGER_CLASS_NAME =
            "com.miui.inputmethod.InputMethodBottomManager"

        private const val BOTTOM_VIEW_HELPER_CLASS_NAME =
            $$"com.miui.inputmethod.InputMethodBottomManager$BottomViewHelper"
    }
}
