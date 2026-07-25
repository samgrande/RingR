package com.hexcorp.ringr.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

@Composable
fun BackgroundShapes() {
    val fillColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
    val fillDarker = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
    val strokeColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)
    val dotColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)

    val infiniteTransition = rememberInfiniteTransition()
    val drift1 by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse),
    )
    val drift2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = -1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse),
    )
    val drift3 by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse),
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val s = w / 500f
        val amp = 4f * s

        // Dot grid 1 — top left
        drawDotGrid(
            center = Offset(w * 0.05f, h * 0.03f + drift1 * amp * 0.5f),
            spacing = 22f * s,
            radius = 3.5f * s,
            color = dotColor,
            count = 4,
        )

        // Dot grid 2 — bottom right
        drawDotGrid(
            center = Offset(w * 0.90f, h * 0.95f + drift2 * amp * 0.5f),
            spacing = 22f * s,
            radius = 3.5f * s,
            color = dotColor,
            count = 4,
        )

        // Large squircle — top right
        val squirlW = 180f * s
        val squirlH = 110f * s
        withTransform({
            translate(left = w * 0.78f, top = h * 0.01f + drift1 * amp)
            rotate(degrees = 12f, pivot = Offset(squirlW / 2f, squirlH / 2f))
        }) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset.Zero,
                size = Size(squirlW, squirlH),
                cornerRadius = CornerRadius(55f * s),
            )
        }

        // Arc — left edge
        val arcW = 200f * s
        val arcH = 70f * s
        withTransform({
            translate(left = w * -0.06f + drift2 * amp * 0.7f, top = h * 0.22f + drift2 * amp)
            rotate(degrees = 25f, pivot = Offset(arcW / 2f, arcH / 2f))
        }) {
            drawRoundRect(
                color = strokeColor,
                topLeft = Offset.Zero,
                size = Size(arcW, arcH),
                cornerRadius = CornerRadius(35f * s),
                style = Stroke(width = 2.5f * s),
            )
        }

        // Diamond — top left
        val diamondSize = 55f * s
        withTransform({
            translate(left = w * 0.10f + drift3 * amp * 0.5f, top = h * 0.22f + drift3 * amp)
            rotate(degrees = 45f, pivot = Offset(diamondSize / 2f, diamondSize / 2f))
        }) {
            drawRect(
                color = strokeColor,
                topLeft = Offset.Zero,
                size = Size(diamondSize, diamondSize),
                style = Stroke(width = 2f * s),
            )
        }

        // Very large filled circle — bottom left
        drawCircle(
            color = fillColor,
            radius = 180f * s,
            center = Offset(w * -0.08f + drift1 * amp * 0.3f, h * 0.92f + drift1 * amp * 0.5f),
        )

        // Medium filled circles
        drawCircle(color = fillColor, radius = 24f * s, center = Offset(w * 0.85f + drift3 * amp * 0.3f, h * 0.20f + drift3 * amp * 0.8f))
        drawCircle(color = fillColor, radius = 16f * s, center = Offset(w * 0.40f + drift2 * amp * 0.3f, h * 0.85f + drift2 * amp * 0.5f))

        // Stroked circles
        drawCircle(color = strokeColor, radius = 32f * s, center = Offset(w * 0.92f, h * 0.55f + drift1 * amp * 0.6f), style = Stroke(width = 2.5f * s))
        drawCircle(color = strokeColor, radius = 20f * s, center = Offset(w * 0.20f, h * 0.78f + drift3 * amp * 0.5f), style = Stroke(width = 2f * s))

        // Pill shape — bottom right
        val pillW = 120f * s
        val pillH = 60f * s
        withTransform({
            translate(left = w * 0.65f, top = h * 0.70f + drift2 * amp * 0.7f)
            rotate(degrees = -6f, pivot = Offset(pillW / 2f, pillH / 2f))
        }) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset.Zero,
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(30f * s),
            )
        }

        // Small overlapping shape — darker
        val overlapW = 50f * s
        val overlapH = 40f * s
        withTransform({
            translate(left = w * 0.72f, top = h * 0.72f + drift3 * amp * 0.6f)
            rotate(degrees = 18f, pivot = Offset(overlapW / 2f, overlapH / 2f))
        }) {
            drawRoundRect(
                color = fillDarker,
                topLeft = Offset.Zero,
                size = Size(overlapW, overlapH),
                cornerRadius = CornerRadius(20f * s),
            )
        }
    }
}

private fun DrawScope.drawDotGrid(
    center: Offset,
    spacing: Float,
    radius: Float,
    color: Color,
    count: Int,
) {
    val half = (count - 1) / 2f
    for (row in 0 until count) {
        for (col in 0 until count) {
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(
                    center.x + (col - half) * spacing,
                    center.y + (row - half) * spacing,
                ),
            )
        }
    }
}
