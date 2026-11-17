package app.localmind.assistant.data.inference

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds a model file on the device. Checked in order:
 *
 * 1. The catalog layout the downloader will use: filesDir/models/<modelId>/model.(litertlm|task)
 * 2. The sideload folder, reachable over USB with no permissions:
 *      Android/data/<applicationId>/files/models/
 *    (debug builds use applicationId "app.localmind.assistant.debug")
 * 3. filesDir/models/ and /data/local/tmp/llm/ (Google's documented `adb push` location)
 *
 * Within the sideload folders, a file whose name contains the modelId wins;
 * otherwise the most recently copied model is used.
 */
@Singleton
class ModelFileLocator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val sideloadDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "models").apply { mkdirs() }

    fun searchDirs(): List<File> = listOf(
        sideloadDir,
        File(context.filesDir, "models"),
        File("/data/local/tmp/llm")
    )

    fun locate(modelId: String): File? {
        MODEL_EXTENSIONS
            .map { ext -> File(context.filesDir, "models/$modelId/model.$ext") }
            .firstOrNull { it.isFile && it.length() > 0 }
            ?.let { return it }

        val candidates = searchDirs().flatMap { dir ->
            dir.listFiles()
                ?.filter { it.isFile && it.length() > 0 && it.extension.lowercase() in MODEL_EXTENSIONS }
                .orEmpty()
        }
        return candidates.firstOrNull { it.nameWithoutExtension.contains(modelId, ignoreCase = true) }
            ?: candidates.maxByOrNull { it.lastModified() }
    }

    companion object {
        val MODEL_EXTENSIONS = setOf("task", "litertlm")
    }
}
