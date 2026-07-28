package com.hexcorp.ringr.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hexcorp.ringr.MainActivity
import com.hexcorp.ringr.R
import com.hexcorp.ringr.RingRApp
import com.hexcorp.ringr.ytdlp.RingRExtractionException
import com.hexcorp.ringr.ytdlp.VideoMeta
import com.hexcorp.ringr.ytdlp.YtDlpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class DownloadResult(
    val jobId: String,
    val cleanUrl: String,
    val meta: VideoMeta,
    val sourceFile: File,
    val waveform: List<Float>,
)

object DownloadEventBus {
    private val _result = MutableStateFlow<DownloadResult?>(null)
    val result: StateFlow<DownloadResult?> = _result.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun post(result: DownloadResult) { _result.value = result }
    fun postError(msg: String) { _error.value = msg }
    fun clear() {
        _result.value = null
        _error.value = null
    }
}

class DownloadService : Service() {

    private lateinit var manager: YtDlpManager
    private var downloadJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        manager = YtDlpManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelDownload(intent.getStringExtra(EXTRA_JOB_ID))
                return START_NOT_STICKY
            }
            else -> {
                val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val jobId = intent?.getStringExtra(EXTRA_JOB_ID) ?: return START_NOT_STICKY
                val cleanUrl = intent?.getStringExtra(EXTRA_CLEAN_URL) ?: url
                startDownload(url, jobId, cleanUrl)
                return START_NOT_STICKY
            }
        }
    }

    private var cancelled = false

    private fun startDownload(url: String, jobId: String, cleanUrl: String) {
        cancelled = false
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        downloadJob = serviceScope.launch {
            try {
                if (!isActive || cancelled) return@launch
                val meta = manager.fetchInfo(url)
                if (!isActive || cancelled) return@launch
                val sourceFile = manager.extractAudio(url, jobId)
                if (!isActive || cancelled) return@launch
                val waveform = manager.extractWaveform(sourceFile)

                if (cancelled) return@launch

                DownloadEventBus.post(DownloadResult(jobId, cleanUrl, meta, sourceFile, waveform))

                if (!RingRApp.isForeground) {
                    showReadyNotification(meta.title)
                }
            } catch (e: RingRExtractionException) {
                if (!cancelled) {
                    if (!RingRApp.isForeground) {
                        showReadyNotification("Download failed")
                    } else {
                        DownloadEventBus.postError(e.message ?: "Download failed")
                    }
                }
            } catch (e: Exception) {
                if (!cancelled) {
                    val msg = e.message ?: "Download failed"
                    if (!RingRApp.isForeground) {
                        showReadyNotification("Download failed")
                    } else {
                        DownloadEventBus.postError(msg)
                    }
                }
            } finally {
                stopSelf()
            }
        }
    }

    private fun cancelDownload(jobId: String?) {
        cancelled = true
        jobId?.let { manager.cancel(it) }
        downloadJob?.cancel()
        downloadJob = null
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        cancelled = true
        downloadJob?.cancel()
        downloadJob = null
        serviceScope.cancel()
    }

    private fun buildForegroundNotification() = NotificationCompat.Builder(this, RingRApp.DOWNLOAD_CHANNEL_ID)
        .setContentTitle("Downloading audio")
        .setContentText("Preparing your ringtone...")
        .setSmallIcon(R.drawable.ic_notification_tune)
        .setOngoing(true)
        .build()

    private fun showReadyNotification(title: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, RingRApp.READY_CHANNEL_ID)
            .setContentTitle("Ringtone ready to crop!")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_notification_tune)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val mgr = getSystemService(android.app.NotificationManager::class.java)
        mgr.notify(READY_NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_CANCEL = "com.hexcorp.ringr.action.CANCEL"
        const val EXTRA_URL = "url"
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_CLEAN_URL = "clean_url"
        private const val NOTIFICATION_ID = 1
        private const val READY_NOTIFICATION_ID = 2
        private const val TAG = "DownloadService"
    }
}
