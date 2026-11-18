package app.slothlobster

import android.app.Application
import app.slothlobster.run.LobsterWorker
import app.slothlobster.run.Notifier

class LobsterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.channels(this)
        LobsterWorker.schedule(this)
    }
}
