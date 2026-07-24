package com.hexcorp.ringr.ui.screens

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectTapGestures
import com.hexcorp.ringr.ui.theme.RingDark
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
                focusedIndicatorColor = RingDark,
                unfocusedIndicatorColor = RingDark,
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
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = RingDark,
            textAlign = TextAlign.Center,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { editing = true })
            },
        )
    }
}
