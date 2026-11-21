package app.localmind.assistant.data.projects

import android.content.Context
import android.util.Log
import app.localmind.assistant.domain.projects.DocKind
import app.localmind.assistant.domain.projects.Project
import app.localmind.assistant.domain.projects.ProjectAsk
import app.localmind.assistant.domain.projects.ProjectDoc
import app.localmind.assistant.domain.projects.Projects
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Your projects: one file in private storage (projects/projects.json), written whole through a
 * temporary file; in backups. Nothing here is sent anywhere. A file that can't be read is set
 * aside as projects.unreadable.json rather than written over.
 */
@Singleton
class ProjectBook @Inject constructor(@ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "projects").apply { mkdirs() }
    private val file = File(dir, "projects.json")
    private val lock = Mutex()
    private var loaded = false

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    /** Most recently changed first. */
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _pending = MutableStateFlow<ProjectAsk?>(null)
    /** A write-up or grilling a project asked for in a chat, until its reply is kept. */
    val pending: StateFlow<ProjectAsk?> = _pending.asStateFlow()

    suspend fun load() = lock.withLock {
        if (loaded) return@withLock
        _projects.value = withContext(Dispatchers.IO) {
            if (!file.exists()) emptyList() else runCatching { Projects.parse(file.readText()) }.getOrElse {
                Log.w(TAG, "Couldn't read projects.json; set aside", it)
                runCatching { file.copyTo(File(dir, "projects.unreadable.json"), overwrite = true) }
                emptyList()
            }
        }.sortedByDescending { it.updatedAt }
        loaded = true
    }

    private suspend fun change(edit: (List<Project>) -> List<Project>) {
        load()
        lock.withLock {
            val next = edit(_projects.value).sortedByDescending { it.updatedAt }
            if (next == _projects.value) return@withLock
            _projects.value = next
            withContext(Dispatchers.IO) { writeAtomic(Projects.encode(next)) }
        }
    }

    suspend fun create(name: String, now: Long = System.currentTimeMillis()): Project {
        val p = Project(id = UUID.randomUUID().toString(), name = name.trim().take(Projects.MAX_NAME).ifEmpty { "New project" }, createdAt = now, updatedAt = now)
        change { listOf(p) + it }
        return p
    }

    /** Changes one project; [touch] marks it as just changed (archiving doesn't count). */
    suspend fun update(id: String, touch: Boolean = true, edit: (Project) -> Project) {
        val now = System.currentTimeMillis()
        change { list -> list.map { if (it.id == id) edit(it).let { p -> if (touch) p.copy(updatedAt = now) else p } else it } }
    }

    suspend fun delete(id: String) {
        change { list -> list.filterNot { it.id == id } }
        if (_pending.value?.projectId == id) _pending.value = null
    }

    suspend fun addDoc(projectId: String, kind: DocKind, title: String, text: String, now: Long = System.currentTimeMillis()): ProjectDoc? {
        val clean = text.trim().take(Projects.MAX_DOC)
        if (clean.isEmpty() || _projects.value.none { it.id == projectId }) return null
        val doc = ProjectDoc(UUID.randomUUID().toString(), kind, title, now, clean)
        update(projectId) { it.copy(docs = listOf(doc) + it.docs) }
        return doc
    }

    fun asked(ask: ProjectAsk) {
        _pending.value = ask
    }

    /** Keeps a chat reply in the project that asked for it: (project, the new document), or null. */
    suspend fun keep(text: String, today: LocalDate = LocalDate.now()): Pair<Project, ProjectDoc>? {
        load()
        val ask = _pending.value ?: return null
        val doc = addDoc(ask.projectId, ask.kind, Projects.docTitle(ask.kind, today), text) ?: return null
        val project = _projects.value.firstOrNull { it.id == ask.projectId } ?: return null
        return project to doc
    }

    suspend fun backupText(): String {
        load()
        return Projects.encode(_projects.value)
    }

    suspend fun mergeBackup(text: String) {
        val theirs = Projects.parse(text)
        change { mine -> Projects.merge(mine, theirs) }
    }

    private fun writeAtomic(text: String) {
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Couldn't save the projects" }
        }
    }

    private companion object {
        const val TAG = "ProjectBook"
    }
}
