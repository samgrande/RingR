package com.hexcorp.ringr

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_common.SharedPrefsHelper
import com.yausername.youtubedl_common.utils.ZipUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipFile

class RingRApp : Application() {

    var isReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                cleanupYtdlp()
                extractStdlib()
                YoutubeDL.getInstance().init(this@RingRApp)
                disableFfmpeg()
                isReady = true
                Log.i(TAG, "yt-dlp initialized")

                try {
                    // MASTER = daily builds from every commit, most up-to-date JS challenges
                    val updateStatus = YoutubeDL.getInstance()
                        .updateYoutubeDL(this@RingRApp, YoutubeDL.UpdateChannel.MASTER)
                    Log.i(TAG, "yt-dlp update result: $updateStatus")
                } catch (e: Exception) {
                    Log.w(TAG, "yt-dlp update failed (will use cached binary): ${e.message}")
                }

            } catch (e: YoutubeDLException) {
                Log.e(TAG, "Failed to initialize yt-dlp", e)
            }
        }
    }

    private fun disableFfmpeg() {
        try {
            val field = com.yausername.youtubedl_android.YoutubeDL::class.java
                .getDeclaredField("ffmpegPath")
            field.isAccessible = true
            field.set(null, File("/system/bin"))
            Log.i(TAG, "ffmpeg redirected to /system/bin")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable ffmpeg: ${e.message}")
        }
    }

    private fun cleanupYtdlp() {
        val ytdlpDir = File(noBackupFilesDir, "youtubedl-android/yt-dlp")
        if (ytdlpDir.exists()) {
            val testFile = File(ytdlpDir, "yt-dlp")
            if (testFile.isDirectory) {
                Log.w(TAG, "Corrupted yt-dlp detected, clearing entire youtubedl-android")
                File(noBackupFilesDir, "youtubedl-android").deleteRecursively()
            }
        }
    }

    private fun extractStdlib() {
        val pythonDir = File(noBackupFilesDir, "youtubedl-android/packages/python")
        val versionFile = File(pythonDir, ".stdlib_version")

        val currentHash = try {
            assets.open("python-stdlib.zip").use { stream ->
                val md = java.security.MessageDigest.getInstance("MD5")
                val buf = ByteArray(8192)
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "python-stdlib.zip not in assets, skipping extraction")
            return
        }

        if (pythonDir.exists() && versionFile.exists() && versionFile.readText() == currentHash) {
            return
        }

        pythonDir.deleteRecursively()
        pythonDir.mkdirs()

        try {
            val tempZip = File(cacheDir, "python-stdlib.zip")
            assets.open("python-stdlib.zip").use { input ->
                tempZip.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Create all directories first so symlink creation doesn't fail (the
            // library's ZipUtils.unzip doesn't mkdirs for symlink entries)
            java.util.zip.ZipFile(tempZip).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) {
                        File(pythonDir, entry.name).mkdirs()
                    }
                }
            }

            ZipUtils.unzip(tempZip, pythonDir)
            tempZip.delete()

            versionFile.writeText(currentHash)
            SharedPrefsHelper.update(this, "pythonLibVersion", "0")
            Log.i(TAG, "Python stdlib extracted (${currentHash.take(8)}…)")
        } catch (e: Exception) {
            pythonDir.deleteRecursively()
            Log.e(TAG, "Failed to extract Python stdlib", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "RingRApp"
    }
}
