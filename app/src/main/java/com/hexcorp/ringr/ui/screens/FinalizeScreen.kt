package com.hexcorp.ringr.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hexcorp.ringr.ui.theme.*
import com.hexcorp.ringr.viewmodel.RingRJob
import java.io.File
import java.io.FileOutputStream

@Composable
fun FinalizeScreen(
    job: RingRJob,
    onRename: (String) -> Unit,
    onBack: () -> Unit,
    onMakeAnother: () -> Unit,
) {
    val context = LocalContext.current

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
            Text(
                "Finalize",
                style = MaterialTheme.typography.titleMedium,
                color = RingDark,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(RingDark),
            ) {
                job.thumbnailUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            EditableTitle(value = job.name, onChange = onRename)
            Text(job.uploader, color = RingMuted, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            Text(
                "Your Ringtone is ready!",
                style = MaterialTheme.typography.headlineMedium,
                color = RingDark,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    job.ringtoneFile?.let { file ->
                        val ok = saveRingtoneToDownloads(context, file, job.name)
                        Toast.makeText(
                            context,
                            if (ok) "Saved to Downloads" else "Could not save file",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RingDark, contentColor = RingWhite),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("DOWNLOAD", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    job.ringtoneFile?.let { file ->
                        val ok = saveAsSystemRingtone(context, file, job.name)
                        if (ok) {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                                Settings.System.canWrite(context)
                            ) {
                                setAsDefaultRingtone(context, file, job.name)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Ringtone saved! Grant WRITE_SETTINGS in Settings to set as default.",
                                    Toast.LENGTH_LONG,
                                ).show()
                                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Could not set ringtone",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RingDark, contentColor = RingWhite),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("SET AS RINGTONE", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) { Text("BACK", color = RingDark, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onMakeAnother,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RingDark, contentColor = RingWhite),
                shape = RoundedCornerShape(50),
            ) { Text("NEW", fontWeight = FontWeight.Bold) }
        }
    }
    }
}

private fun saveRingtoneToDownloads(context: Context, file: File, name: String): Boolean {
    val safeName = name.replace(Regex("[^a-zA-Z0-9 _-]"), "").ifBlank { "ringtone" }
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$safeName.mp3")
                put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outFile = File(downloadsDir, "$safeName.mp3")
            FileOutputStream(outFile).use { out -> file.inputStream().use { it.copyTo(out) } }
        }
        true
    } catch (e: Exception) {
        false
    }
}

private fun saveAsSystemRingtone(context: Context, file: File, name: String): Boolean {
    val safeName = name.replace(Regex("[^a-zA-Z0-9 _-]"), "").ifBlank { "ringtone" }
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeName.mp3")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.TITLE, safeName)
                put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val uri = resolver.insert(collectionUri, values) ?: return false
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val ringtonesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
            ringtonesDir.mkdirs()
            val outFile = File(ringtonesDir, "$safeName.mp3")
            FileOutputStream(outFile).use { out -> file.inputStream().use { it.copyTo(out) } }
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.getContentUriForPath(outFile.absolutePath)?.let { uri ->
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                    put(MediaStore.Audio.Media.TITLE, safeName)
                }
                context.contentResolver.update(uri, values, null, null)
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

private fun setAsDefaultRingtone(context: Context, file: File, name: String) {
    val safeName = name.replace(Regex("[^a-zA-Z0-9 _-]"), "").ifBlank { "ringtone" }
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME}=?"
        val args = arrayOf("$safeName.mp3")
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
            } else null
        }
    } else {
        @Suppress("DEPRECATION")
        val ringtonesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
        @Suppress("DEPRECATION")
        val outFile = File(ringtonesDir, "$safeName.mp3")
        if (outFile.exists()) {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.getContentUriForPath(outFile.absolutePath)
        } else null
    }
    if (uri != null) {
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
        Toast.makeText(context, "Ringtone set!", Toast.LENGTH_SHORT).show()
    }
}
