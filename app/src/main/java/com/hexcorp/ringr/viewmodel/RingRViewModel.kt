package com.hexcorp.ringr.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hexcorp.ringr.service.DownloadEventBus
import com.hexcorp.ringr.service.DownloadService
import com.hexcorp.ringr.ytdlp.RingRExtractionException
import com.hexcorp.ringr.ytdlp.YtDlpManager
import kotlinx.coroutines.Job
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

    private var currentJobId: String? = null
    private var currentCleanUrl: String? = null

    init {
        viewModelScope.launch {
            DownloadEventBus.result.collect { result ->
                if (result != null && _uiState.value.step == Step.LOADING) {
                    _uiState.update {
                        it.copy(
                            step = Step.TRIM,
                            loading = false,
                            job = RingRJob(
                                id = result.jobId,
                                url = result.cleanUrl,
                                name = result.meta.title,
                                uploader = result.meta.uploader,
                                thumbnailUrl = result.meta.thumbnailUrl,
                                durationSeconds = result.meta.durationSeconds,
                                sourceFile = result.sourceFile,
                                waveformData = result.waveform.ifEmpty { null },
                            ),
                        )
                    }
                    currentJobId = null
                    currentCleanUrl = null
                    DownloadEventBus.clear()
                }
            }
        }
        viewModelScope.launch {
            DownloadEventBus.error.collect { msg ->
                if (msg != null && _uiState.value.step == Step.LOADING) {
                    _uiState.update { it.copy(step = Step.LANDING, loading = false, error = msg) }
                    currentJobId = null
                    currentCleanUrl = null
                    DownloadEventBus.clear()
                }
            }
        }
    }

    fun cancelLoading() {
        getApplication<Application>().startService(
            Intent(getApplication(), DownloadService::class.java).apply {
                action = DownloadService.ACTION_CANCEL
                putExtra(DownloadService.EXTRA_JOB_ID, currentJobId)
            }
        )
        currentJobId = null
        currentCleanUrl = null
        _uiState.update { RingRUiState(step = Step.LANDING) }
        DownloadEventBus.clear()
    }

    fun submitLink(url: String) {
        if (url.isBlank()) {
            Log.w(TAG, "submitLink: blank URL")
            return
        }

        manager.clearCache()

        val cleanUrl = sanitizeUrl(url.trim())
        Log.d(TAG, "submitLink: original=$url sanitized=$cleanUrl")

        if (cleanUrl.contains("playlist", ignoreCase = true) ||
            cleanUrl.contains("youtube.com/playlist", ignoreCase = true)) {
            _uiState.update { it.copy(error = "Playlist links are not supported. Please paste a single video link.") }
            return
        }

        _uiState.update { it.copy(step = Step.LOADING, loading = true, error = null) }

        val jobId = UUID.randomUUID().toString().take(10)
        currentJobId = jobId
        currentCleanUrl = cleanUrl

        getApplication<Application>().startService(
            Intent(getApplication(), DownloadService::class.java).apply {
                action = Intent.ACTION_DEFAULT
                putExtra(DownloadService.EXTRA_URL, url)
                putExtra(DownloadService.EXTRA_JOB_ID, jobId)
                putExtra(DownloadService.EXTRA_CLEAN_URL, cleanUrl)
            }
        )
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
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
