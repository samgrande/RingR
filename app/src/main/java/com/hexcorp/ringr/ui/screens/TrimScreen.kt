package com.hexcorp.ringr.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hexcorp.ringr.viewmodel.RingRJob
import kotlinx.coroutines.delay
import kotlin.math.abs
import java.io.File

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
    var playheadPosition by remember { mutableFloatStateOf(0f) }
    var playerReady by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (playerReady && !mediaPlayer.isPlaying) {
                        mediaPlayer.start()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun preparePlayer(file: File, startMs: Int) {
        playerReady = false
        mediaPlayer.apply {
            reset()
            setDataSource(file.absolutePath)
            setOnPreparedListener {
                seekTo(startMs)
                start()
                playerReady = true
            }
            prepareAsync()
        }
    }

    LaunchedEffect(job.sourceFile) {
        val file = job.sourceFile ?: return@LaunchedEffect
        preparePlayer(file, (cropStart * 1000).toInt())
    }

    LaunchedEffect(cropStart, cropDuration) {
        if (!playerReady) return@LaunchedEffect
        delay(300)
        val startMs = (cropStart * 1000).toInt()
        preparePlayer(job.sourceFile ?: return@LaunchedEffect, startMs)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            try {
                playheadPosition = mediaPlayer.currentPosition / 1000f
            } catch (_: Exception) {}
        }
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
        Card(
            modifier = Modifier.wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCut,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Trim Your Ringtone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))

                val selectedIndex = PRESETS.indexOfFirst { abs(cropDuration - it) < 1f }.coerceAtLeast(0)
                var trackWidth by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .onSizeChanged { trackWidth = it.width }
                        .pointerInput(trackWidth) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                if (trackWidth <= 0) return@detectHorizontalDragGestures
                                val pos = change.position.x.coerceIn(0f, trackWidth.toFloat())
                                val idx = (pos / trackWidth * PRESETS.size).toInt().coerceIn(0, PRESETS.size - 1)
                                cropDuration = minOf(PRESETS[idx].toFloat(), totalDuration)
                            }
                        },
                ) {
                    val thumbWidthPx = if (trackWidth > 0) trackWidth / PRESETS.size else 0
                    val targetX = selectedIndex * thumbWidthPx
                    val animatedOffset by animateIntOffsetAsState(
                        targetValue = IntOffset(x = targetX, y = 0),
                        animationSpec = spring(
                            stiffness = 800f,
                            dampingRatio = 0.35f,
                        ),
                    )

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(animatedOffset.x, 0) }
                            .width(with(density) { thumbWidthPx.toDp() })
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )

                    Row(modifier = Modifier.fillMaxSize()) {
                        PRESETS.forEachIndexed { index, p ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        val newDur = minOf(p.toFloat(), totalDuration)
                                        cropDuration = newDur
                                        if (cropStart + newDur > totalDuration) {
                                            cropStart = totalDuration - newDur
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${p}s",
                                    fontWeight = FontWeight.Bold,
                                    color = if (index == selectedIndex)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
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
                MarqueeText(text = job.name, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(job.uploader, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 13.sp)

                Spacer(Modifier.height(24.dp))

                val startTime = formatTime(cropStart)
                val endTime = formatTime(cropStart + cropDuration)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InfoChip(label = "START", value = startTime)
                        Spacer(Modifier.width(12.dp))
                        InfoChip(label = "END", value = endTime)
                        Spacer(Modifier.width(12.dp))
                        InfoChip(label = "TOTAL", value = formatTime(totalDuration))
                    }
                }

                Spacer(Modifier.height(20.dp))

                var canvasWidth by remember { mutableFloatStateOf(1f) }

                MinimalWaveform(
                    waveform = job.waveformData,
                    totalDuration = totalDuration,
                    cropStart = cropStart,
                    cropDuration = cropDuration,
                    playheadPosition = playheadPosition,
                    canvasWidth = canvasWidth,
                    onSizeChanged = { canvasWidth = it },
                    onCropStartChanged = { cropStart = it.coerceIn(0f, totalDuration - cropDuration) },
                )

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
                        shape = RoundedCornerShape(28.dp),
                    ) { Text("BACK", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }

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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = RoundedCornerShape(28.dp),
                    ) { Text("PROCEED", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                tint = MaterialTheme.colorScheme.secondary,
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
    playheadPosition: Float,
    canvasWidth: Float,
    onSizeChanged: (Float) -> Unit,
    onCropStartChanged: (Float) -> Unit,
) {
    val clipHeight = 64.dp
    val density = LocalDensity.current
    val onCropStartChangedState = rememberUpdatedState(onCropStartChanged)
    val cropStartState = rememberUpdatedState(cropStart)
    val cropDurationState = rememberUpdatedState(cropDuration)

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val onSurfaceDim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(clipHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val barCount = 60
            val step = w / (barCount + 1)
            val barW = 6f
            val maxBarH = h * 0.55f
            val minBarH = h * 0.15f

            for (i in 0 until barCount) {
                val x = step * (i + 1)
                val amplitude = waveform?.getOrNull((i * (waveform.size - 1).coerceAtLeast(1)) / barCount) ?: 0.5f
                val barH = minBarH + (maxBarH - minBarH) * amplitude
                val topY = (h - barH) / 2f

                val isInCrop = x >= (cropStart / totalDuration * w) && x <= ((cropStart + cropDuration) / totalDuration * w)
                val isPlayed = isInCrop && x <= (playheadPosition / totalDuration * w)

                val barColor = when {
                    isPlayed -> primaryColor
                    isInCrop -> primaryContainerColor.copy(alpha = 0.5f)
                    else -> onSurfaceDim
                }

                drawRect(
                    color = barColor,
                    topLeft = Offset(x - barW / 2f, topY),
                    size = Size(barW, barH),
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
                val fillColor = primaryColor.copy(alpha = 0.08f)
                val strokeColor = primaryColor.copy(alpha = 0.5f)
                val cornerR = 16.dp.toPx()
                val borderW = 2.dp.toPx()

                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(cropLeftPx, 0f),
                    size = Size(cropW, h),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                )
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(cropLeftPx, 0f),
                    size = Size(cropW, h),
                    cornerRadius = CornerRadius(cornerR, cornerR),
                    style = Stroke(width = borderW),
                )
            }
        }
    }
}

@Composable
private fun MarqueeText(text: String, modifier: Modifier = Modifier) {
    var containerWidth by remember { mutableIntStateOf(0) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.titleMedium
    val textWidth = remember(text) {
        textMeasurer.measure(
            text = text,
            style = textStyle,
            constraints = Constraints(maxWidth = Int.MAX_VALUE),
            maxLines = 1,
        ).size.width
    }

    Box(
        modifier = modifier.clipToBounds().onSizeChanged { containerWidth = it.width },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = if (textWidth <= containerWidth) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier = Modifier
                .then(if (textWidth <= containerWidth) Modifier.fillMaxWidth() else Modifier)
                .then(if (textWidth > containerWidth) Modifier.offset { IntOffset(offsetX.toInt(), 0) } else Modifier),
        )
    }

    LaunchedEffect(text, textWidth, containerWidth) {
        offsetX = 0f
        if (textWidth > containerWidth && containerWidth > 0) {
            val distance = (textWidth - containerWidth).toFloat()
            val stepDelay = 20L
            val steps = 60
            while (true) {
                delay(1500)
                for (i in 1..steps) {
                    offsetX = -(distance * i / steps)
                    delay(stepDelay)
                }
                delay(2000)
                for (i in steps downTo 1) {
                    offsetX = -(distance * i / steps)
                    delay(stepDelay)
                }
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
