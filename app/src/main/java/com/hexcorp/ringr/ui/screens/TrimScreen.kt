package com.hexcorp.ringr.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hexcorp.ringr.ui.theme.*
import com.hexcorp.ringr.viewmodel.RingRJob
import kotlinx.coroutines.delay
import kotlin.math.abs

private val PRESETS = listOf(30, 60, 90)

@Composable
fun TrimScreen(
    job: RingRJob,
    loading: Boolean,
    error: String?,
    onRename: (String) -> Unit,
    onBack: () -> Unit,
    onProceed: (startSec: Double, endSec: Double) -> Unit,
) {
    val totalDuration = (job.durationSeconds ?: 300).toFloat().coerceAtLeast(30f)

    var cropDuration by remember(job.id) { mutableFloatStateOf(minOf(30f, totalDuration)) }
    var cropStart by remember(job.id) { mutableFloatStateOf(0f) }

    val mediaPlayer = remember { MediaPlayer() }
    var muted by remember { mutableStateOf(false) }

    val cropStartRef = rememberUpdatedState(cropStart)
    val cropDurationRef = rememberUpdatedState(cropDuration)

    LaunchedEffect(job.sourceFile) {
        val file = job.sourceFile ?: return@LaunchedEffect
        mediaPlayer.apply {
            reset()
            setDataSource(file.absolutePath)
            setOnPreparedListener {
                seekTo((cropStartRef.value * 1000).toInt())
                start()
            }
            setOnCompletionListener {
                seekTo((cropStartRef.value * 1000).toInt())
                start()
            }
            prepareAsync()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            try {
                val startMs = (cropStartRef.value * 1000).toInt()
                val endMs = ((cropStartRef.value + cropDurationRef.value) * 1000).toInt()
                val pos = mediaPlayer.currentPosition
                if (pos >= endMs + 1 || pos < startMs - 200) {
                    mediaPlayer.seekTo(startMs)
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(cropStart) {
        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.seekTo((cropStart * 1000).toInt())
            }
        } catch (_: Exception) {}
    }

    var showMuteFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(muted) {
        mediaPlayer.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
        showMuteFeedback = true
        delay(800)
        showMuteFeedback = false
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .background(RingPanel, RoundedCornerShape(40.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Text("Trim Your Ringtone", style = MaterialTheme.typography.titleMedium, color = RingDark)
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .background(RingPill, RoundedCornerShape(50))
                .padding(4.dp),
        ) {
            PRESETS.forEach { p ->
                val active = abs(cropDuration - p) < 1f
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) RingAccentSoft else Color.Transparent)
                        .clickable {
                            val newDur = minOf(p.toFloat(), totalDuration)
                            cropDuration = newDur
                            if (cropStart + newDur > totalDuration) {
                                cropStart = totalDuration - newDur
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("${p}s", fontWeight = FontWeight.Bold, color = RingDark)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(RingDark)
                .clickable { muted = !muted },
            contentAlignment = Alignment.Center,
        ) {
            job.thumbnailUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            MuteOverlay(
                visible = showMuteFeedback,
                muted = muted,
            )
        }

        Spacer(Modifier.height(12.dp))
        EditableTitle(value = job.name, onChange = onRename)
        Text(job.uploader, color = RingMuted, fontWeight = FontWeight.Medium, fontSize = 14.sp)

        Spacer(Modifier.height(24.dp))

        val startTime = formatTime(cropStart)
        val endTime = formatTime(cropStart + cropDuration)
        Text(
            "$startTime \u2192 $endTime",
            color = RingDark,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )

        Spacer(Modifier.height(40.dp))

        var canvasWidth by remember { mutableFloatStateOf(1f) }

        MinimalWaveform(
            waveform = job.waveformData,
            totalDuration = totalDuration,
            cropStart = cropStart,
            cropDuration = cropDuration,
            canvasWidth = canvasWidth,
            onSizeChanged = { canvasWidth = it },
            onCropStartChanged = { cropStart = it.coerceIn(0f, totalDuration - cropDuration) },
        )

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatTime(0f), color = RingMuted, fontSize = 11.sp)
            Text(formatTime(totalDuration), color = RingMuted, fontSize = 11.sp)
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = RingAccent, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) { Text("BACK", color = RingDark, fontWeight = FontWeight.Bold) }

            Spacer(Modifier.width(16.dp))

            Button(
                onClick = {
                    onProceed(
                        cropStart.toDouble(),
                        (cropStart + cropDuration).toDouble(),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = RingDark, contentColor = RingWhite),
                shape = RoundedCornerShape(50),
            ) { Text(if (loading) "PROCESSING\u2026" else "PROCEED", fontWeight = FontWeight.Bold) }
        }
    }
    }
}

@Composable
private fun MuteOverlay(visible: Boolean, muted: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f)) + fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFFFDE0D9)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = RingAccent,
            )
        }
    }
}

@Composable
private fun MinimalWaveform(
    waveform: List<Float>?,
    totalDuration: Float,
    cropStart: Float,
    cropDuration: Float,
    canvasWidth: Float,
    onSizeChanged: (Float) -> Unit,
    onCropStartChanged: (Float) -> Unit,
) {
    val clipHeight = 44.dp
    val density = LocalDensity.current
    val onCropStartChangedState = rememberUpdatedState(onCropStartChanged)
    val cropStartState = rememberUpdatedState(cropStart)
    val cropDurationState = rememberUpdatedState(cropDuration)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(clipHeight)
            .onGloballyPositioned { coords ->
                val w = coords.size.width.toFloat()
                if (w > 0f) onSizeChanged(w)
            }
            .pointerInput(canvasWidth, totalDuration) {
                var dragStartX = 0f
                var initialCropStart = 0f

                detectDragGestures(
                    onDragStart = { offset ->
                        initialCropStart = cropStartState.value
                        dragStartX = offset.x
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (canvasWidth <= 0f) return@detectDragGestures
                        val deltaSec = (change.position.x - dragStartX) / canvasWidth * totalDuration
                        onCropStartChangedState.value(initialCropStart + deltaSec)
                    },
                )
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(RingPill),
        ) {
            val w = size.width
            val h = size.height

            val barCount = 50
            val step = w / (barCount + 1)

            for (i in 0 until barCount) {
                val x = step * (i + 1)
                val isInCrop = x >= (cropStart / totalDuration * w) && x <= ((cropStart + cropDuration) / totalDuration * w)
                val tall = i % 2 == 0
                val barH = if (tall) h * 0.30f else h * 0.18f
                val topY = (h - barH) / 2f
                val bw = 7f

                drawRect(
                    color = if (isInCrop) RingAccent else RingDark.copy(alpha = 0.25f),
                    topLeft = Offset(x - bw / 2f, topY),
                    size = Size(bw, barH),
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cropLeftPx = (cropStart / totalDuration * w).coerceIn(0f, w)
            val cropRightPx = ((cropStart + cropDuration) / totalDuration * w).coerceIn(0f, w)
            val cropW = (cropRightPx - cropLeftPx).coerceAtLeast(0f)

            if (cropW > 0f) {
                val rectColor = Color(0xFFE00202).copy(alpha = 0.50f)
                val strokeColor = Color(0xFF871111)
                val cornerR = 16.dp.toPx()

                drawRoundRect(
                    color = rectColor,
                    topLeft = Offset(cropLeftPx, 0f),
                    size = Size(cropW, h),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                )
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(cropLeftPx, 0f),
                    size = Size(cropW, h),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    style = Stroke(width = with(density) { 5.dp.toPx() }),
                )
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
