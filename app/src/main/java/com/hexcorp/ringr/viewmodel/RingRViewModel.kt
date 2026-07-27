package com.hexcorp.ringr.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hexcorp.ringr.ytdlp.RingRExtractionException
import com.hexcorp.ringr.ytdlp.VideoMeta
import com.hexcorp.ringr.ytdlp.YtDlpManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.util.UUID

enum class Step { LANDING, LOADING, TRIM, FINALIZE }

data class RingRJob(
    val id: String,
    val url: String,
    val name: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val sourceFile: File? = null,
    val ringtoneFile: File? = null,
    val ringtoneDurationSeconds: Float? = null,
    val waveformData: List<Float>? = null,
)

data class RingRUiState(
    val step: Step = Step.LANDING,
    val job: RingRJob? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class RingRViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = YtDlpManager(application)

    private val _uiState = MutableStateFlow(RingRUiState())
    val uiState: StateFlow<RingRUiState> = _uiState

    fun submitLink(url: String) {
        if (url.isBlank()) {
            Log.w(TAG, "submitLink: blank URL")
            return
        }
        val cleanUrl = sanitizeUrl(url.trim())
        Log.d(TAG, "submitLink: original=$url sanitized=$cleanUrl")

        if (cleanUrl.contains("playlist", ignoreCase = true) ||
            cleanUrl.contains("youtube.com/playlist", ignoreCase = true)) {
            _uiState.update { it.copy(error = "Playlist links are not supported. Please paste a single video link.") }
            return
        }

        _uiState.update { it.copy(step = Step.LOADING, loading = true, error = null) }

        viewModelScope.launch {
            val jobId = UUID.randomUUID().toString().take(10)
            try {
                Log.d(TAG, "fetchInfo starting for $cleanUrl")
                val meta: VideoMeta = manager.fetchInfo(cleanUrl)
                Log.d(TAG, "fetchInfo OK — title=${meta.title}")
                val sourceFile = manager.extractAudio(cleanUrl, jobId)
                Log.d(TAG, "extractAudio OK — ${sourceFile.absolutePath} (${sourceFile.length()} bytes)")
                val waveform = manager.extractWaveform(sourceFile)

                _uiState.update {
                    it.copy(
                        step = Step.TRIM,
                        loading = false,
                        job = RingRJob(
                            id = jobId,
                            url = cleanUrl,
                            name = meta.title,
                            uploader = meta.uploader,
                            thumbnailUrl = meta.thumbnailUrl,
                            durationSeconds = meta.durationSeconds,
                            sourceFile = sourceFile,
                            waveformData = waveform.ifEmpty { null },
                        ),
                    )
                }
            } catch (e: RingRExtractionException) {
                Log.e(TAG, "Extraction failed: ${e.message}", e)
                _uiState.update { it.copy(step = Step.LANDING, loading = false, error = e.message) }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                _uiState.update { it.copy(step = Step.LANDING, loading = false, error = "Something went wrong. Try again.") }
            }
        }
    }

    fun proceedToFinalize(startSec: Double, endSec: Double) {
        val job = _uiState.value.job ?: return

        _uiState.update { it.copy(step = Step.LOADING, loading = true, error = null) }

        viewModelScope.launch {
            try {
                val ringtoneFile = manager.trimAudio(
                    jobId = job.id,
                    startSec = startSec,
                    durationSec = endSec - startSec,
                    sourceFile = job.sourceFile,
                )
                job.sourceFile?.delete()
                val ringtoneDuration = (endSec - startSec).toFloat()
                _uiState.update {
                    it.copy(
                        step = Step.FINALIZE,
                        loading = false,
                        job = job.copy(ringtoneFile = ringtoneFile, ringtoneDurationSeconds = ringtoneDuration),
                    )
                }
            } catch (e: RingRExtractionException) {
                _uiState.update { it.copy(step = Step.TRIM, loading = false, error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(step = Step.TRIM, loading = false, error = "Could not process that clip.") }
            }
        }
    }

    fun rename(newName: String) {
        val job = _uiState.value.job ?: return
        _uiState.update { it.copy(job = job.copy(name = newName)) }
    }

    fun backToLanding() {
        _uiState.update { RingRUiState(step = Step.LANDING) }
    }

    fun backToTrim() {
        _uiState.update { it.copy(step = Step.TRIM, error = null) }
    }

    fun makeAnother() {
        _uiState.value.job?.let { manager.cleanup(it.id) }
        _uiState.update { RingRUiState(step = Step.LANDING) }
    }

    companion object {
        private const val TAG = "RingRViewModel"
    }
}

private fun sanitizeUrl(url: String): String {
    return try {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return url

        if (host.contains("youtube.com") || host.contains("youtu.be")) {
            val query = uri.query ?: return url
            val params = query.split("&").filter {
                val key = it.split("=").firstOrNull() ?: ""
                key != "si" && key != "feature" && !key.startsWith("utm_")
            }
            if (params.size == query.split("&").size) return url
            val cleanQuery = params.joinToString("&")
            uri.toString().replace("?$query", if (cleanQuery.isEmpty()) "" else "?$cleanQuery")
        } else {
            url
        }
    } catch (_: Exception) {
        url
    }
}
