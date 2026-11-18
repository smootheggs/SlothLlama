package app.localmind.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.localmind.assistant.data.backup.AutoBackup
import app.localmind.assistant.data.backup.AutoBackupState
import app.localmind.assistant.data.meaning.MeaningModel
import app.localmind.assistant.data.meaning.MeaningModelState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Home page's own status lines: the weekly backup and search by meaning. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    autoBackup: AutoBackup,
    meaning: MeaningModel
) : ViewModel() {
    val backup: StateFlow<AutoBackupState> = autoBackup.state

    val meaningOn: StateFlow<Boolean> = meaning.state
        .map { it is MeaningModelState.Ready }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), meaning.state.value is MeaningModelState.Ready)
}
