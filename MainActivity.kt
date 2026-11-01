package app.videogir

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()
    private var pending: Pair<VideoMeta, Option>? = null

    private val storagePerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            val p = pending
            pending = null
            if (p != null) {
                if (ok) startDownload(p.first, p.second)
                else Toast.makeText(this, "برای ذخیره فایل، اجازه دسترسی به حافظه لازم است.", Toast.LENGTH_LONG).show()
            }
        }

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // پس‌زمینه همیشه مشکی است؛ آیکن‌های نوار وضعیت و ناوبری باید روشن باشند.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (savedInstanceState == null) handleIntent(intent)

        val firstLaunch = savedInstanceState == null
        setContent {
            VoltexApp(vm = vm, onDownload = ::requestDownload, showSplashFirst = firstLaunch)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type == "text/plain") {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            vm.input = Extractor.findUrl(text) ?: text
            vm.load()
        }
    }

    private fun requestDownload(meta: VideoMeta, opt: Option) {
        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pending = meta to opt
            storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        startDownload(meta, opt)
    }

    private fun startDownload(meta: VideoMeta, opt: Option) {
        ContextCompat.startForegroundService(this, DownloadService.startIntent(this, meta, opt))
    }
}
