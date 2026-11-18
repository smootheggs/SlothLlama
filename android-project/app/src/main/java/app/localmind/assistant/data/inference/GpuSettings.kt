package app.localmind.assistant.data.inference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether models run on the GPU, and a guard so a GPU problem can never lock the app up.
 *
 * GPU drivers abort the whole process when something goes wrong, so it can't be caught like an
 * exception. Instead, a marker is written (synchronously) before the GPU is first used for a
 * model and removed once that model has loaded and written its first reply. If the app starts
 * and finds the marker, the last GPU attempt took the app down: that model goes on the CPU-only
 * list and the Models screen says so, with a way to try again.
 *
 * Kept in its own preferences file because it describes this tablet, so it isn't part of backups.
 */
@Singleton
class GpuSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("gpu", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    /** The user's switch: use the GPU when this tablet has one llama.cpp supports. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _cpuOnlyModels = MutableStateFlow(prefs.getStringSet(KEY_CPU_ONLY, emptySet()).orEmpty().toSet())
    /** Model files that took the app down on the GPU; they run on the CPU until retried. */
    val cpuOnlyModels: StateFlow<Set<String>> = _cpuOnlyModels.asStateFlow()

    private val _gpuName = MutableStateFlow<String?>(null)
    /** The GPU found by llama.cpp ("" = none), or null before the native side has been started. */
    val gpuName: StateFlow<String?> = _gpuName.asStateFlow()

    init {
        prefs.getString(KEY_PENDING, null)?.let { crashed ->
            val updated = _cpuOnlyModels.value + crashed
            prefs.edit().remove(KEY_PENDING).putStringSet(KEY_CPU_ONLY, updated).commit()
            _cpuOnlyModels.value = updated
        }
    }

    /** The folder for compiled GPU kernels. Android clears the code cache when the app is updated. */
    val kernelCacheDir: String = context.codeCacheDir.resolve("cl-cache").absolutePath

    /** True until the GPU has compiled its kernels once on this tablet (that first load is slow). */
    fun firstGpuSetup(): Boolean = java.io.File(kernelCacheDir).list().isNullOrEmpty()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
    }

    fun reportGpu(name: String) {
        _gpuName.value = name
    }

    fun shouldUseGpu(modelFile: String): Boolean =
        _enabled.value && !_gpuName.value.isNullOrEmpty() && modelFile !in _cpuOnlyModels.value

    /** Called just before [modelFile] first touches the GPU. Written with commit() so it survives a crash. */
    fun beginAttempt(modelFile: String) {
        prefs.edit().putString(KEY_PENDING, modelFile).commit()
    }

    /** The attempt finished (successfully, or with an ordinary error that was handled). */
    fun endAttempt() {
        if (prefs.contains(KEY_PENDING)) prefs.edit().remove(KEY_PENDING).commit()
    }

    /** Lets a model try the GPU again. */
    fun retry(modelFile: String) {
        val updated = _cpuOnlyModels.value - modelFile
        prefs.edit().putStringSet(KEY_CPU_ONLY, updated).apply()
        _cpuOnlyModels.value = updated
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_CPU_ONLY = "cpu_only_models"
        const val KEY_PENDING = "pending_attempt"
    }
}
