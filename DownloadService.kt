package app.videogir

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

sealed interface Dl {
    data object Idle : Dl
    data class Running(val title: String, val phase: String, val progress: Float) : Dl // progress < 0 = نامشخص
    data class Done(val title: String, val where: String) : Dl
    data class Failed(val message: String) : Dl
}

object DownloadHub {
    val state = MutableStateFlow<Dl>(Dl.Idle)
}

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val j = job
            if (j == null || !j.isActive) stopSelf() else j.cancel()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(X_TITLE)
        val label = intent?.getStringExtra(X_LABEL) ?: ""
        val videoUrl = intent?.getStringExtra(X_VIDEO)
        val audioUrl = intent?.getStringExtra(X_AUDIO)

        // سیستم می‌خواهد بعد از startForegroundService حتماً startForeground صدا زده شود.
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notif(title ?: "VOLTEX", "در حال آماده‌سازی…", -1f),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )

        if (job?.isActive == true) return START_NOT_STICKY
        if (title == null || (videoUrl == null && audioUrl == null)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        DownloadHub.state.value = Dl.Running(title, "در حال آماده‌سازی…", -1f)

        job = scope.launch {
            try {
                runJob(title, label, videoUrl, audioUrl)
            } catch (e: CancellationException) {
                DownloadHub.state.value = Dl.Idle
                throw e
            } catch (e: Exception) {
                val msg = friendly(e)
                DownloadHub.state.value = Dl.Failed(msg)
                finalNotif(title, "دانلود ناموفق: $msg")
            } finally {
                File(cacheDir, "dl").deleteRecursively()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runJob(title: String, label: String, videoUrl: String?, audioUrl: String?) {
        val dir = File(cacheDir, "dl").apply { deleteRecursively(); mkdirs() }
        val vFile = File(dir, "video.mp4")
        val aFile = File(dir, "audio.m4a")

        val vTotal = videoUrl?.let { Fetcher.length(it) } ?: 0L
        val aTotal = audioUrl?.let { Fetcher.length(it) } ?: 0L
        val known = (videoUrl == null || vTotal > 0) && (audioUrl == null || aTotal > 0)
        val total = vTotal.coerceAtLeast(0) + aTotal.coerceAtLeast(0)
        var vDone = 0L
        var aDone = 0L
        var lastNotif = 0L

        fun push(phase: String, force: Boolean = false) {
            val p = if (known && total > 0) ((vDone + aDone).toFloat() / total).coerceIn(0f, 1f) else -1f
            DownloadHub.state.value = Dl.Running(title, phase, p)
            val now = SystemClock.elapsedRealtime()
            if (force || now - lastNotif > 700) {
                lastNotif = now
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif(title, phase, p))
            }
        }

        val merge = videoUrl != null && audioUrl != null
        if (videoUrl != null) {
            val phase = if (merge) "دانلود تصویر…" else "در حال دانلود…"
            push(phase, true)
            Fetcher.download(videoUrl, vFile) { vDone = it; push(phase) }
        }
        if (audioUrl != null) {
            val phase = if (merge) "دانلود صدا…" else "در حال دانلود…"
            push(phase, true)
            Fetcher.download(audioUrl, aFile) { aDone = it; push(phase) }
        }

        val audioOnly = videoUrl == null
        val result: File
        if (merge) {
            DownloadHub.state.value = Dl.Running(title, "ترکیب تصویر و صدا…", -1f)
            getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, notif(title, "ترکیب تصویر و صدا…", -1f))
            result = File(dir, "out.mp4")
            Muxer.mux(vFile, aFile, result)
        } else {
            result = if (audioOnly) aFile else vFile
        }

        DownloadHub.state.value = Dl.Running(title, "ذخیره فایل…", -1f)
        val base = Storage.safeName(title)
        val name = if (audioOnly) "$base.m4a" else "$base [$label].mp4"
        val mime = if (audioOnly) "audio/mp4" else "video/mp4"
        val where = Storage.saveToPublic(this, result, name, mime, audioOnly)

        DownloadHub.state.value = Dl.Done(title, where)
        finalNotif(title, "دانلود تمام شد · $where")
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: ""
        return when {
            "ENOSPC" in m || "No space" in m -> "حافظه گوشی پر است."
            "403" in m -> "یوتیوب دسترسی به فایل را رد کرد ($m). کیفیت دیگری را امتحان کن."
            e is IOException -> "خطای شبکه یا قطع اینترنت. دوباره امتحان کن."
            else -> "خطا: $m"
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "دانلودها", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notif(title: String, text: String, p: Float): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "لغو", cancel)
            .setProgress(100, (p.coerceAtLeast(0f) * 100).toInt(), p < 0)
            .build()
    }

    private fun finalNotif(title: String, text: String) {
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID + 1, n)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 41
        private const val ACTION_CANCEL = "app.videogir.CANCEL"
        private const val X_TITLE = "title"
        private const val X_LABEL = "label"
        private const val X_VIDEO = "video"
        private const val X_AUDIO = "audio"

        fun startIntent(ctx: Context, meta: VideoMeta, opt: Option): Intent =
            Intent(ctx, DownloadService::class.java)
                .putExtra(X_TITLE, meta.title)
                .putExtra(X_LABEL, opt.label)
                .putExtra(X_VIDEO, opt.videoUrl)
                .putExtra(X_AUDIO, opt.audioUrl)

        fun cancelIntent(ctx: Context): Intent =
            Intent(ctx, DownloadService::class.java).setAction(ACTION_CANCEL)
    }
}
