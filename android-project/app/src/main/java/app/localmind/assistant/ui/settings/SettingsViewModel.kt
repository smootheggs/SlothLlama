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
    private val assistant: AssistantSettingsStore,
    private val tutorLevels: app.localmind.assistant.data.persona.TutorLevelStore
) : AndroidViewModel(app) {

    val reviewReminder: StateFlow<Boolean> = assistant.reviewReminder
    val reminderHour: StateFlow<Int> = assistant.reminderHour
    val openToHome: StateFlow<Boolean> = assistant.openToHome
    val levels = tutorLevels.levels

    fun setLevel(language: app.localmind.assistant.domain.persona.SpeechLanguage, level: app.localmind.assistant.domain.persona.TutorLevel) =
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
            } catch (e: app.localmind.assistant.data.backup.WrongPasswordException) {
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

    private fun restoreDone(summary: BackupSummary) =
        "Restored ${summary.describe()}. Restart SlothLlama to apply restored settings."

    private fun BackupSummary.describe(): String =
        "$chats chats, $flashcards flashcards, $personas custom personas"

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
