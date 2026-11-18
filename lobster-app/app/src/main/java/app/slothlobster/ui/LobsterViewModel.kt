package app.slothlobster.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.slothlobster.core.KeptCatch
import app.slothlobster.core.Live
import app.slothlobster.core.Lobster
import app.slothlobster.core.Saved
import app.slothlobster.core.Settings
import app.slothlobster.jobs.RepeatRule
import app.slothlobster.mailbox.OrderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LobsterViewModel(app: Application) : AndroidViewModel(app) {

    val lobster = Lobster.get(app)
    val saved: StateFlow<Saved> = lobster.store.saved
    val live: StateFlow<Live> = lobster.live

    /** Ticks every 20 seconds, for "2 min ago" and to notice SlothLlama filing a catch. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    /** Text shared to the Lobster from another app, waiting in the order box. */
    private val _shared = MutableStateFlow<String?>(null)
    val shared: StateFlow<String?> = _shared.asStateFlow()

    init {
        lobster.applyAwake()
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                lobster.refreshLive()
                _now.value = System.currentTimeMillis()
                delay(20_000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { lobster.refreshLive() }
        lobster.engine.poke()
    }

    fun share(text: String?) {
        if (!text.isNullOrBlank()) _shared.value = text.trim()
    }

    fun sharedUsed() {
        _shared.value = null
    }

    fun send(kind: OrderKind, text: String, deep: Boolean, repeat: RepeatRule?) = lobster.newOrder(kind, text, deep, repeat)
    fun setRepeatOn(id: String, on: Boolean) = lobster.setRepeatOn(id, on)
    fun removeRepeat(id: String) = lobster.removeRepeat(id)
    fun cancel(id: String) = lobster.cancel(id)
    fun sendNow(id: String) = lobster.sendNow(id)
    fun again(id: String) = lobster.again(id)
    fun remove(id: String) = lobster.remove(id)

    fun setPaused(on: Boolean) = lobster.setPaused(on)
    fun stopEverything() = lobster.stopEverything()

    fun addWatch(target: String, hours: Int, keywords: List<String>) = lobster.addWatch(target, hours, keywords)
    fun setWatchFilter(id: String, section: String, keywords: List<String>) = lobster.setWatchFilter(id, section, keywords)
    fun setWatchOn(id: String, on: Boolean) = lobster.setWatchOn(id, on)
    fun setWatchEvery(id: String, hours: Int) = lobster.setWatchEvery(id, hours)
    fun checkWatchNow(id: String) = lobster.checkWatchNow(id)
    fun removeWatch(id: String) = lobster.removeWatch(id)

    fun pair(code: String): Boolean = lobster.pair(code)
    fun unpair() = lobster.unpair()
    fun setFolder(uri: Uri) = lobster.setFolder(uri)
    fun settings(change: (Settings) -> Settings) = lobster.settings(change)
    fun briefNow() = lobster.briefNow()
    fun setFeeds(text: String) = lobster.setFeeds(text)
    fun importOpml(uri: Uri) = lobster.importOpml(uri)

    suspend fun loadCatch(id: String): KeptCatch? = withContext(Dispatchers.IO) { lobster.loadCatch(id) }
}
