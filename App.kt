package app.videogir

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(DownloaderImpl())
    }
}
