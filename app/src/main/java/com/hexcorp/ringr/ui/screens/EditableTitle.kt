package com.hexcorp.ringr.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import androidx.compose.foundation.gestures.detectTapGestures
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
        var offsetX by remember { mutableFloatStateOf(0f) }
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

        Box(
            modifier = Modifier.fillMaxWidth().clipToBounds().onSizeChanged { containerWidth = it.width },
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
                    .then(if (textWidth > containerWidth) Modifier.offset { IntOffset(offsetX.toInt(), 0) } else Modifier)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { editing = true })
                    },
            )
        }

        LaunchedEffect(value, textWidth, containerWidth) {
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
}
