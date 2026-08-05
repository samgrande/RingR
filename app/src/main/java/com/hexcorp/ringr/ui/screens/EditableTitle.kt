package com.hexcorp.ringr.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EditableTitle(value: String, onChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    if (editing) {
        TextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.onSurface,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onChange(draft.ifBlank { value })
                editing = false
            }),
            modifier = Modifier.focusRequester(focusRequester),
        )
        LaunchedEffect(Unit) { scope.launch { focusRequester.requestFocus() } }
    } else {
        var containerWidth by remember { mutableIntStateOf(0) }
        val textMeasurer = rememberTextMeasurer()
        val textStyle = MaterialTheme.typography.titleMedium
        val textWidth = remember(value) {
            textMeasurer.measure(
                text = value,
                style = textStyle,
                constraints = Constraints(maxWidth = Int.MAX_VALUE),
                maxLines = 1,
            ).size.width
        }

        val scroll = remember { Animatable(0f) }

        LaunchedEffect(value, textWidth, containerWidth) {
            scroll.snapTo(0f)
            if (textWidth > containerWidth && containerWidth > 0) {
                val distance = (textWidth - containerWidth).toFloat()
                val duration = 3600
                while (true) {
                    delay(1500)
                    scroll.animateTo(
                        targetValue = -distance,
                        animationSpec = tween(durationMillis = duration, easing = LinearEasing),
                    )
                    delay(2000)
                    scroll.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = duration, easing = LinearEasing),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f).clipToBounds().onSizeChanged { containerWidth = it.width },
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = if (textWidth <= containerWidth) TextAlign.Center else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                    modifier = Modifier
                        .then(if (textWidth <= containerWidth) Modifier.fillMaxWidth() else Modifier)
                        .then(if (textWidth > containerWidth) Modifier.offset { IntOffset(scroll.value.toInt(), 0) } else Modifier)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { editing = true })
                        },
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Rename ringtone",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable { editing = true }
                    .padding(2.dp),
            )
        }
    }
}
