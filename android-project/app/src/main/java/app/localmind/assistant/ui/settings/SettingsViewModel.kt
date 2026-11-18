package app.localmind.assistant.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.backup.BackupManager
import app.localmind.assistant.data.backup.BackupSummary
import app.localmind.assistant.data.backup.PasswordRequiredException
import app.localmind.assistant.data.settings.AssistantSettingsStore
import app.localmind.assistant.data.study.ReviewReminder
import app.localmind.assistant.data.backup.AutoBackup
import app.localmind.assistant.data.backup.AutoBackupState
import app.localmind.assistant.data.backup.WrongPasswordException
import app.localmind.assistant.data.diagnostics.Diagnostics
import app.localmind.assistant.data.models.ModelLibrary
import app.localmind.assistant.data.persona.TutorLevelStore
import app.localmind.assistant.domain.persona.SpeechLanguage
import app.localmind.assistant.domain.persona.TutorLevel
import app.localmind.assistant.data.memory.Memory
import app.localmind.assistant.data.memory.MemoryStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A dialog the backup flow is waiting on. */
sealed interface BackupDialog {
    /** Choose an optional password before picking where to save. */
    data object ExportPassword : BackupDialog
    /** The chosen backup is protected: ask for its password. */
    data class RestorePassword(val uri: Uri, val wrongBefore: Boolean = false) : BackupDialog
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val backups: BackupManager,
    private val autoBackup: AutoBackup,
    private val modelLibrary: ModelLibrary,
    private val memoryStore: MemoryStore,
    private val assistant: AssistantSettingsStore,
    private val tutorLevels: TutorLevelStore
) : AndroidViewModel(app) {

    val reviewReminder: StateFlow<Boolean> = assistant.reviewReminder
    val reminderHour: StateFlow<Int> = assistant.reminderHour
    val openToHome: StateFlow<Boolean> = assistant.openToHome
    val levels = tutorLevels.levels

    fun setLevel(language: SpeechLanguage, level: TutorLevel) =
        tutorLevels.set(language, level)

    fun setOpenToHome(home: Boolean) = assistant.setOpenToHome(home)

    val toolsOn: StateFlow<Boolean> = assistant.toolsOn
    val toolsForTutors: StateFlow<Boolean> = assistant.toolsForTutors
    val calendarOn: StateFlow<Boolean> = assistant.calendarOn

    fun setToolsOn(on: Boolean) = assistant.setToolsOn(on)
    fun setToolsForTutors(on: Boolean) = assistant.setToolsForTutors(on)
    fun setCalendarOn(on: Boolean) = assistant.setCalendarOn(on)

    private val _dialog = MutableStateFlow<BackupDialog?>(null)
    val dialog: StateFlow<BackupDialog?> = _dialog.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    /** "Saving backup…" / "Restoring…" while working. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** After a restore: models and Library files the backup lists that need importing again. */
    private val _toImport = MutableStateFlow<List<String>?>(null)
    val toImport: StateFlow<List<String>?> = _toImport.asStateFlow()

    fun dismissToImport() {
        _toImport.value = null
    }

    // --- Automatic backups -------------------------------------------------------------------

    val autoBackupState: StateFlow<AutoBackupState> = autoBackup.state

    fun setAutoBackupFolder(uri: Uri?) {
        if (uri == null) return
        runCatching { autoBackup.setFolder(uri) }
            .onFailure { _message.value = "SlothLlama couldn't keep access to that folder. Try another one." }
    }

    fun setAutoBackupPassword(password: String) {
        runCatching { autoBackup.setPassword(password) }.onFailure { _message.value = it.message }
    }

    fun setAutoBackup(on: Boolean) = autoBackup.setEnabled(on)

    fun autoBackupNow() = run("Backing up…") { "Backup saved: ${autoBackup.runNow()}." }

    // --- Memory of you ----------------------------------------------------------------------

    val memoryOn: StateFlow<Boolean> = memoryStore.enabled
    val memories: StateFlow<List<Memory>> =
        memoryStore.memories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMemoryOn(on: Boolean) = memoryStore.setEnabled(on)

    fun saveMemory(id: String?, text: String) {
        viewModelScope.launch {
            runCatching { if (id == null) memoryStore.add(text) else memoryStore.update(id, text) }
                .onFailure { _message.value = it.message }
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { memoryStore.delete(id) }
    }

    fun deleteAllMemories() {
        viewModelScope.launch {
            memoryStore.deleteAll()
            _message.value = "Everything SlothLlama remembered about you is deleted."
        }
    }

    // --- Diagnostics -------------------------------------------------------------------------

    suspend fun diagnosticsReport(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val models = runCatching { modelLibrary.refresh() }.getOrDefault(emptyList())
        val active = modelLibrary.activePath.value?.let { java.io.File(it).name } ?: "none"
        val auto = autoBackup.state.value
        Diagnostics.report(
            app,
            listOf(
                "Models" to "${models.size} (active: $active)",
                "Automatic backup" to when {
                    !auto.enabled -> "off"
                    auto.lastAt == 0L -> "on, not run yet"
                    else -> "on, last ${if (auto.lastOk) "saved" else "failed"} " +
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(auto.lastAt))
                }
            )
        )
    }

    fun crashCount(): Int = Diagnostics.crashes(app).size

    fun clearCrashes() = Diagnostics.clear(app)

    /** Password chosen for the export in progress (kept only until the file is written). */
    private var pendingExportPassword: String? = null

    fun setReviewReminder(enabled: Boolean, hour: Int = reminderHour.value) {
        assistant.setReviewReminder(enabled, hour)
        ReviewReminder.schedule(app, enabled, hour, replace = true)
    }

    fun startExport() {
        _dialog.value = BackupDialog.ExportPassword
    }

    /** After the password dialog; the screen then asks where to save. */
    fun confirmExportPassword(password: String?) {
        pendingExportPassword = password?.takeIf { it.isNotEmpty() }
        _dialog.value = null
    }

    fun export(uri: Uri?) {
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri == null) return
        run("Saving backup…") {
            val summary = backups.export(uri, password)
            "Backup saved: ${summary.describe()}" + if (password != null) ", password-protected." else "."
        }
    }

    fun restore(uri: Uri?) {
        if (uri == null) return
        run("Reading backup…") {
            if (backups.isEncrypted(uri)) {
                _dialog.value = BackupDialog.RestorePassword(uri)
                null
            } else {
                restoreDone(backups.restore(uri, null))
            }
        }
    }

    fun restoreWithPassword(uri: Uri, password: String) {
        _dialog.value = null
        run("Restoring…") {
            try {
                restoreDone(backups.restore(uri, password))
            } catch (e: PasswordRequiredException) {
                _dialog.value = BackupDialog.RestorePassword(uri, wrongBefore = true)
                null
            } catch (e: WrongPasswordException) {
                _dialog.value = BackupDialog.RestorePassword(uri, wrongBefore = true)
                null
            }
        }
    }

    fun dismissDialog() {
        _dialog.value = null
        pendingExportPassword = null
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun restoreDone(summary: BackupSummary): String {
        val missing = summary.missingModels.map { "Model: $it" } + summary.missingLibrary.map { "Library: $it" }
        if (missing.isNotEmpty()) _toImport.value = missing
        return "Restored ${summary.describe()}. Restart SlothLlama to apply restored settings."
    }

    private fun BackupSummary.describe(): String =
        "$chats chats, $flashcards flashcards, $personas custom personas" + if (memories > 0) ", $memories memories" else ""

    /** Runs [work] with a busy label; its result (if any) becomes the snackbar message. */
    private fun run(label: String, work: suspend () -> String?) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = label
            try {
                work()?.let { _message.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _message.value = e.message ?: "Something went wrong."
            } finally {
                _busy.value = null
            }
        }
    }
}
