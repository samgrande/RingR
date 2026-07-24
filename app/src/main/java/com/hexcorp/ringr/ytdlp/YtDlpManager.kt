package com.hexcorp.ringr.ytdlp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class VideoMeta(
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
)

class RingRExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

class YtDlpManager(private val context: Context) {

    private val workDir: File
        get() = File(context.cacheDir, "ringr").apply { mkdirs() }

    suspend fun fetchInfo(url: String): VideoMeta = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--skip-download")
        }
        val info = try {
            YoutubeDL.getInstance().getInfo(request)
        } catch (e: Exception) {
            Log.e(TAG, "fetchInfo failed for $url", e)
            throw RingRExtractionException("Could not read that link. Check the URL and try again.", e)
        }

        VideoMeta(
            title = info.title ?: "Untitled",
            uploader = info.uploader ?: "Unknown uploader",
            thumbnailUrl = info.thumbnail,
            durationSeconds = info.duration.takeIf { it > 0 },
        )
    }

    suspend fun extractAudio(url: String, jobId: String): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "$jobId.source.m4a")

        val request = YoutubeDLRequest(url).apply {
            addOption("-f", "bestaudio[ext=m4a]/bestaudio[ext=opus]/bestaudio/best")
            addOption("--no-playlist")
            addOption("--extract-audio")
            addOption("--audio-format", "m4a")
            addOption("--audio-quality", "0")
            addOption("-o", outFile.absolutePath)
            addOption("--force-overwrites")
            addOption("--no-part")
        }

        try {
            YoutubeDL.getInstance().execute(request, jobId, null)
        } catch (e: Exception) {
            Log.e(TAG, "extractAudio failed for $url", e)
            throw RingRExtractionException("Could not extract audio from that link.", e)
        }

        if (!outFile.exists()) {
            val fallback = workDir.listFiles { f -> f.name.startsWith("$jobId.source") }
                ?.firstOrNull()
            if (fallback != null) {
                Log.w(TAG, "Expected .m4a but found ${fallback.name} — using it directly")
                return@withContext fallback
            }
            throw RingRExtractionException("Extraction finished but the audio file wasn't found.")
        }
        outFile
    }

    suspend fun trimAudio(
        url: String,
        jobId: String,
        startSec: Double,
        durationSec: Double,
        sourceFile: File? = null,
    ): File = withContext(Dispatchers.IO) {
        val outFile = File(workDir, "$jobId.ringtone.mp3")
        val endSec = startSec + durationSec
        val sectionSpec = "*${startSec.toLong()}-${endSec.toLong()}"

        val request = YoutubeDLRequest(url).apply {
            addOption("-f", "bestaudio[ext=m4a]/bestaudio[ext=opus]/bestaudio/best")
            addOption("--no-playlist")
            addOption("--download-sections", sectionSpec)
            addOption("--extract-audio")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "192K")
            addOption("-o", outFile.absolutePath)
            addOption("--force-overwrites")
            addOption("--no-part")
        }

        try {
            YoutubeDL.getInstance().execute(request, "$jobId-trim", null)
        } catch (e: Exception) {
            Log.e(TAG, "trimAudio failed", e)
            throw RingRExtractionException("Could not trim that clip. Try a different range.", e)
        }

        if (!outFile.exists()) {
            throw RingRExtractionException("Trim finished but the output file wasn't found.")
        }
        outFile
    }

    suspend fun extractWaveform(sourceFile: File): List<Float> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext emptyList()

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(sourceFile.absolutePath)

            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex < 0) return@withContext emptyList()

            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val pcmStream = ByteArrayOutputStream(64 * 1024)

            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(20000L)
                    if (inputIndex >= 0) {
                        val inputBuf = codec.getInputBuffer(inputIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 20000L)
                when {
                    outputIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outputIndex) ?: continue
                        val data = ByteArray(bufferInfo.size)
                        outBuf.get(data)
                        outBuf.clear()
                        pcmStream.write(data)
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Continue decoding with new format
                    }
                }
            }

            codec.stop()
            codec.release()

            val rawBytes = pcmStream.toByteArray()
            val bytesPerSample = 2
            val sampleCount = rawBytes.size / bytesPerSample
            if (sampleCount < 2) return@withContext emptyList()

            val targetBars = 600
            val groupSize = (sampleCount / targetBars).coerceAtLeast(1)

            val waveform = FloatArray(targetBars)
            for (i in 0 until targetBars) {
                var maxAbs = 0f
                val start = i * groupSize
                val end = minOf(start + groupSize, sampleCount)
                for (j in start until end) {
                    val lo = rawBytes[j * 2].toInt() and 0xFF
                    val hi = rawBytes[j * 2 + 1].toInt() shl 8
                    val sample = (lo or hi).toShort().toFloat()
                    maxAbs = maxOf(maxAbs, kotlin.math.abs(sample))
                }
                waveform[i] = maxAbs / 32768f
            }

            waveform.toList()
        } catch (e: Exception) {
            Log.w(TAG, "waveform extraction failed", e)
            emptyList()
        } finally {
            extractor.release()
        }
    }

    fun cleanup(jobId: String) {
        workDir.listFiles { f -> f.name.startsWith(jobId) }?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "YtDlpManager"
    }
}
