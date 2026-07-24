package com.hexcorp.ringr

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RingRApp : Application() {

    // Exposed so screens can show "still preparing" state on cold start.
    var isReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@RingRApp)
                FFmpeg.getInstance().init(this@RingRApp)
                isReady = true
                Log.i(TAG, "yt-dlp + ffmpeg initialized")

                // Update yt-dlp on every cold start so extractors stay current.
                // YouTube frequently breaks old extractors; this keeps things working.
                try {
                    val updateStatus = YoutubeDL.getInstance()
                        .updateYoutubeDL(this@RingRApp, YoutubeDL.UpdateChannel.NIGHTLY)
                    Log.i(TAG, "yt-dlp update result: $updateStatus")
                } catch (e: Exception) {
                    // Update failure is non-fatal — existing binary can still work.
                    Log.w(TAG, "yt-dlp update failed (will use cached binary): ${e.message}")
                }

            } catch (e: YoutubeDLException) {
                Log.e(TAG, "Failed to initialize yt-dlp/ffmpeg", e)
            }
        }
    }

    companion object {
        private const val TAG = "RingRApp"
    }
}
