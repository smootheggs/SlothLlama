package app.slothlobster

import android.app.Application
import app.slothlobster.run.LobsterWorker
import app.slothlobster.run.Notifier

class LobsterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Crashes are noted in private storage for Settings → Diagnostics (nothing is sent).
        app.slothlobster.core.Diagnostics.install(this)
        Notifier.channels(this)
        LobsterWorker.schedule(this)
    }
}
