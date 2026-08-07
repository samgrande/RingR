package com.hexcorp.ringr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.hexcorp.ringr.extractor.NewPipeDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe

class RingRApp : Application() {

    var isReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()

        createNotificationChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            isForeground = when (event) {
                Lifecycle.Event.ON_STOP -> false
                else -> true
            }
        })

        NewPipe.init(NewPipeDownloader(OkHttpClient()))
        isReady = true
        Log.i(TAG, "NewPipe initialized")
    }

    private fun createNotificationChannels() {
        val downloadChannel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            "Audio Download",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows download progress" }
        val readyChannel = NotificationChannel(
            READY_CHANNEL_ID,
            "Ringtone Ready",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Notifies when ringtone is ready to crop" }
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(downloadChannel)
        mgr.createNotificationChannel(readyChannel)
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "ringr_download"
        const val READY_CHANNEL_ID = "ringr_ready"
        @Volatile
        var isForeground = true
        private const val TAG = "RingRApp"
    }
}
