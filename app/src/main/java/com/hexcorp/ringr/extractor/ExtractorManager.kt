package com.hexcorp.ringr.extractor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import org.schabi.newpipe.extractor.MediaFormat as NewPipeMediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

data class VideoMeta(
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
)

data class PcmData(
    val sampleRate: Int,
    val channelCount: Int,
    val totalFrames: Long,
)

class RingRExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ExtractorManager(private val context: Context) {

    private val workDir: File
        get() = File(context.cacheDir, "ringr").apply { mkdirs() }

    suspend fun fetchInfo(url: String): VideoMeta = withContext(Dispatchers.IO) {
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            VideoMeta(
                title = info.name ?: "Untitled",
                uploader = info.uploaderName ?: "Unknown uploader",
                thumbnailUrl = info.thumbnails.maxByOrNull { it.width }?.url,
                durationSeconds = info.duration.takeIf { it > 0 }?.toInt(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchInfo failed for $url", e)
            throw RingRExtractionException("Could not read that link. Check the URL and try again.", e)
        }
    }

    suspend fun extractAudio(url: String, jobId: String): File = withContext(Dispatchers.IO) {
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            val bestAudio = info.audioStreams
                .firstOrNull {
                    it.format == NewPipeMediaFormat.M4A &&
                        it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
                }
                ?: info.audioStreams.maxByOrNull { it.averageBitrate }
                ?: throw RingRExtractionException("No audio stream found for that link.")

            val suffix = bestAudio.format?.suffix ?: "m4a"
            val outFile = File(workDir, "$jobId.source.$suffix")
            Log.i(
                TAG,
                "extractAudio: format=${bestAudio.format?.name} suffix=$suffix " +
                    "bitrate=${bestAudio.averageBitrate} url=${bestAudio.content.take(80)}"
            )

            val client = OkHttpClient()
            val request = OkRequest.Builder()
                .url(bestAudio.content)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw RingRExtractionException("Download failed (${resp.code}).")
                }
                outFile.outputStream().use { out ->
                    resp.body!!.byteStream().copyTo(out)
                }
            }

            if (!outFile.exists() || outFile.length() == 0L) {
                throw RingRExtractionException("Extraction finished but the audio file wasn't found.")
            }
            outFile
        } catch (e: RingRExtractionException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "extractAudio failed for $url", e)
            throw RingRExtractionException("Could not extract audio from that link.", e)
        }
    }

    suspend fun trimAudio(
        jobId: String,
        startSec: Double,
        durationSec: Double,
        pcmFile: File? = null,
        pcmData: PcmData? = null,
    ): File = withContext(Dispatchers.IO) {
        val pcm = pcmFile
            ?: throw RingRExtractionException("Audio not decoded yet.")
        val info = pcmData
            ?: throw RingRExtractionException("Audio not decoded yet.")
        val outFile = File(workDir, "$jobId.ringtone.m4a")

        val startFrame = (startSec * info.sampleRate).toLong().coerceIn(0L, info.totalFrames)
        val endFrame = ((startSec + durationSec) * info.sampleRate).toLong()
            .coerceIn(startFrame, info.totalFrames)
        val frameCount = (endFrame - startFrame).coerceAtLeast(1L)
        val bytesPerFrame = 2L

        Log.i(TAG, "trimAudio: startSec=$startSec durationSec=$durationSec " +
                "sampleRate=${info.sampleRate} startFrame=$startFrame endFrame=$endFrame " +
                "frameCount=$frameCount totalFrames=${info.totalFrames}")

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, info.sampleRate, 1
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            format.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1
            var muxerStarted = false

            val pcmIn = FileInputStream(pcm)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputBytesLeft = frameCount * bytesPerFrame
            var framesFed = 0L
            var inputEos = false
            var outputEos = false

            try {
                while (!outputEos) {
                    if (!inputEos) {
                        val inputIndex = encoder.dequeueInputBuffer(20000L)
                        if (inputIndex >= 0) {
                            val inputBuf = encoder.getInputBuffer(inputIndex) ?: continue
                            if (inputBytesLeft <= 0L) {
                                encoder.queueInputBuffer(
                                    inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEos = true
                            } else {
                                val offset = startFrame * bytesPerFrame + framesFed * bytesPerFrame
                                pcmIn.channel.position(offset)
                                inputBuf.clear()
                                inputBuf.limit(minOf(inputBuf.capacity(), inputBytesLeft.toInt()))
                                val read = pcmIn.channel.read(inputBuf)
                                if (read <= 0) {
                                    encoder.queueInputBuffer(
                                        inputIndex, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputEos = true
                                } else {
                                    inputBytesLeft -= read
                                    framesFed += read / bytesPerFrame
                                    val pts = framesFed * 1_000_000L / info.sampleRate
                                    encoder.queueInputBuffer(inputIndex, 0, read, pts, 0)
                                }
                            }
                        }
                    }

                    val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 20000L)
                    when {
                        outputIndex >= 0 -> {
                            val outBuf = encoder.getOutputBuffer(outputIndex) ?: continue
                            if (bufferInfo.size > 0) {
                                if (muxerTrackIndex < 0) {
                                    val outFormat = encoder.outputFormat
                                    muxerTrackIndex = muxer.addTrack(outFormat)
                                    muxer.start()
                                    muxerStarted = true
                                }
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(muxerTrackIndex, outBuf, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputEos = true
                            }
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            // ignore; handled via encoder.outputFormat when first sample arrives
                        }
                    }
                }
            } finally {
                pcmIn.close()
                if (muxerStarted) {
                    muxer.stop()
                    muxer.release()
                } else {
                    muxer.release()
                }
            }

            Log.i(TAG, "trimAudio: OK — frameCount=$frameCount file=${outFile.length()} bytes")
        } catch (e: RingRExtractionException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "trimAudio failed", e)
            throw RingRExtractionException("Could not trim that clip. Try a different range.", e)
        } finally {
            encoder.release()
        }

        outFile
    }

    suspend fun extractPcm(sourceFile: File, pcmOutFile: File): PcmData? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
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
            if (audioTrackIndex < 0) return@withContext null

            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var sampleRate = 44100
            var channels = 1
            var totalFrames = 0L

            FileOutputStream(pcmOutFile).use { out ->
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
                            val decoded = codec.getOutputBuffer(outputIndex)
                            if (decoded != null && bufferInfo.size > 0) {
                                decoded.position(bufferInfo.offset)
                                decoded.limit(bufferInfo.offset + bufferInfo.size)

                                val frameBytes = 2 * channels
                                val frames = bufferInfo.size / frameBytes
                                val shorts = ShortArray(frames * channels)
                                decoded.asShortBuffer().get(shorts)

                                var writePos = 0
                                val mono = ByteArray(frames * 2)
                                for (f in 0 until frames) {
                                    var sum = 0
                                    for (c in 0 until channels) {
                                        sum += shorts[f * channels + c]
                                    }
                                    val monoSample = (sum / channels).toShort()
                                    mono[writePos++] = (monoSample.toInt() and 0xFF).toByte()
                                    mono[writePos++] = ((monoSample.toInt() shr 8) and 0xFF).toByte()
                                }
                                out.write(mono, 0, writePos)
                                totalFrames += frames
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFormat = codec.outputFormat
                            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }

            Log.i(TAG, "extractPcm: OK — sampleRate=$sampleRate frames=$totalFrames " +
                    "file=${pcmOutFile.length()} bytes")
            PcmData(sampleRate = sampleRate, channelCount = 1, totalFrames = totalFrames)
        } catch (e: Exception) {
            Log.w(TAG, "pcm extraction failed", e)
            null
        } finally {
            extractor.release()
            codec?.release()
        }
    }

    suspend fun extractWaveform(pcmFile: File?, pcmData: PcmData?): List<Float> = withContext(Dispatchers.IO) {
        if (pcmFile == null || pcmData == null || !pcmFile.exists() || pcmData.totalFrames <= 0L) {
            return@withContext emptyList()
        }

        try {
            val targetBars = 600
            val groupSize = (pcmData.totalFrames / targetBars).coerceAtLeast(1L)

            val waveform = FloatArray(targetBars)
            var barIndex = 0
            var barMax = 0f
            var framesInBar = 0L

            val shorts = ShortArray(4096)
            FileInputStream(pcmFile).use { inp ->
                val bytes = ByteArray(8192)
                var n = inp.read(bytes)
                while (n > 0) {
                    val count = n / 2
                    var p = 0
                    for (i in 0 until count) {
                        val lo = bytes[p].toInt() and 0xFF
                        val hi = bytes[p + 1].toInt() shl 8
                        p += 2
                        val sample = (lo or hi).toShort()
                        val abs = kotlin.math.abs(sample.toFloat()) / 32768f
                        if (abs > barMax) barMax = abs
                        framesInBar++
                        if (framesInBar >= groupSize) {
                            if (barIndex < targetBars) waveform[barIndex] = barMax
                            barIndex++
                            framesInBar = 0L
                            barMax = 0f
                        }
                    }
                    n = inp.read(bytes)
                }
                if (framesInBar > 0 && barIndex < targetBars) waveform[barIndex] = barMax
            }

            waveform.toList()
        } catch (e: Exception) {
            Log.w(TAG, "waveform extraction failed", e)
            emptyList()
        }
    }

    fun pcmFile(jobId: String): File = File(workDir, "$jobId.pcm")

    fun cancel(jobId: String) {
        cleanup(jobId)
    }

    fun clearCache() {
        val dir = workDir
        if (dir.exists()) dir.deleteRecursively()
    }

    fun cleanup(jobId: String) {
        workDir.listFiles { f -> f.name.startsWith(jobId) }?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "ExtractorManager"
    }
}
