package com.hexcorp.ringr.ytdlp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

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
            addExtractorArgs()
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
            addOption("-o", outFile.absolutePath)
            addOption("--force-overwrites")
            addOption("--no-part")
            addExtractorArgs()
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
        jobId: String,
        startSec: Double,
        durationSec: Double,
        sourceFile: File? = null,
    ): File = withContext(Dispatchers.IO) {
        val srcFile = sourceFile
            ?: throw RingRExtractionException("Source file required for trimming.")
        val outFile = File(workDir, "$jobId.ringtone.m4a")

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(srcFile.absolutePath)

            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex < 0) {
                throw RingRExtractionException("No audio track found in source file.")
            }

            extractor.selectTrack(audioTrackIndex)
            val trackFormat = extractor.getTrackFormat(audioTrackIndex)
            val totalUs = trackFormat.getLong(MediaFormat.KEY_DURATION, 0L)
            val startUs = (startSec * 1_000_000).toLong()
            val endUs = ((startSec + durationSec) * 1_000_000).toLong()

            Log.i(TAG, "trimAudio: startSec=$startSec durationSec=$durationSec " +
                    "startUs=$startUs endUs=$endUs totalUs=$totalUs")

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(trackFormat)
            muxer.start()

            val directBuf = ByteBuffer.allocateDirect(256 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var wroteSamples = false
            var sampleCount = 0
            var firstSampleTime = -1L
            var lastSampleTime = -1L

            while (true) {
                val sampleSize = extractor.readSampleData(directBuf, 0)
                if (sampleSize < 0) break

                val sampleTime = extractor.sampleTime
                if (firstSampleTime < 0) firstSampleTime = sampleTime
                lastSampleTime = sampleTime

                if (sampleTime > endUs) {
                    Log.d(TAG, "trimAudio: reached end at sampleTime=$sampleTime")
                    break
                }

                if (sampleTime >= startUs) {
                    directBuf.position(0)
                    directBuf.limit(sampleSize)
                    bufferInfo.set(0, sampleSize, sampleTime, extractor.sampleFlags)
                    muxer.writeSampleData(muxerTrackIndex, directBuf, bufferInfo)
                    wroteSamples = true
                    sampleCount++
                }

                if (!extractor.advance()) break
            }

            muxer.stop()
            muxer.release()

            Log.i(TAG, "trimAudio: OK — $sampleCount samples, " +
                    "firstTime=$firstSampleTime lastTime=$lastSampleTime, " +
                    "file=${outFile.length()} bytes")

            if (!wroteSamples) {
                throw RingRExtractionException("No audio samples in selected range.")
            }
        } catch (e: RingRExtractionException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "trimAudio failed", e)
            throw RingRExtractionException("Could not trim that clip. Try a different range.", e)
        } finally {
            extractor.release()
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

    fun cancel(jobId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(jobId)
        } catch (_: Exception) {}
        cleanup(jobId)
    }

    fun clearCache() {
        val dir = workDir
        if (dir.exists()) dir.deleteRecursively()
    }

    fun cleanup(jobId: String) {
        workDir.listFiles { f -> f.name.startsWith(jobId) }?.forEach { it.delete() }
    }

    private fun YoutubeDLRequest.addExtractorArgs() {
        addOption("--extractor-args", "youtube:player_client=android,tv")
        addOption("--extractor-retries", "5")
        addOption("--fragment-retries", "10")
        addOption("--retry-sleep", "3")
    }

    companion object {
        private const val TAG = "YtDlpManager"
    }
}
