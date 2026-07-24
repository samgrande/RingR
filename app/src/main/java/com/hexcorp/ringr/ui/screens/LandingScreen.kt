package com.hexcorp.ringr.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hexcorp.ringr.ui.theme.*

@Composable
fun LandingScreen(
    loading: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RingBg)
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = com.hexcorp.ringr.R.drawable.ic_ringr_logo),
                contentDescription = "Ring-R",
                modifier = Modifier.size(width = 400.dp, height = 78.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Convert YouTube links into ringtone",
                fontSize = 13.sp,
                color = RingDark,
            )

            Spacer(Modifier.height(48.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RingPill, RoundedCornerShape(50))
                    .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = RingMuted)
                Spacer(Modifier.width(12.dp))
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Paste your link", color = RingMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) onSubmit(url.trim()) }),
                )
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.text?.let { url = it.toString() }
                    },
                    colors = ButtonDefaults.textButtonColors(containerColor = RingPanelInner, contentColor = RingDark),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("PASTE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onSubmit(url.trim()) },
                enabled = !loading && url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RingDark, contentColor = RingWhite),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(if (loading) "LOADING\u2026" else "CREATE", fontWeight = FontWeight.Bold)
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = RingAccent, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = "made by @HeX",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            color = RingMuted,
            fontSize = 12.sp,
        )
    }
}
