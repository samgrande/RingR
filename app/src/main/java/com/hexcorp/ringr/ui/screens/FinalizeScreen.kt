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
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
    var isSaving by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                modifier = Modifier.wrapContentWidth().wrapContentHeight(),
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
                                imageVector = Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Ringtone Ready",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                            Spacer(Modifier.height(4.dp))
                            Text(job.uploader, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Ringtone duration",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "${job.ringtoneDurationSeconds?.let { it.toInt() } ?: "--"}s",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        enabled = !isSaving,
                        onClick = {
                            if (isSaving) return@Button
                            isSaving = true
                            Log.d("RingR", "SET AS RINGTONE clicked, ringtoneFile=${job.ringtoneFile}")
                            val file = job.ringtoneFile
                            if (file == null || !file.exists()) {
                                Log.e("RingR", "ringtoneFile is null or missing: $file")
                                Toast.makeText(
                                    context,
                                    "Trim output file not found. Please trim again.",
                                    Toast.LENGTH_LONG,
                                ).show()
                                isSaving = false
                                return@Button
                            }
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
                                    "Could not save ringtone. Check storage permissions.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            isSaving = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("SET AS RINGTONE", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            FilledTonalButton(
                onClick = onMakeAnother,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("CREATE NEW", fontWeight = FontWeight.Bold) }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private fun saveRingtoneToDownloads(context: Context, file: File, name: String): Boolean {
    val safeName = name.replace(Regex("[^a-zA-Z0-9 _-]"), "").ifBlank { "ringtone" }
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$safeName.m4a")
                put(MediaStore.Downloads.MIME_TYPE, "audio/mp4")
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
            val outFile = File(downloadsDir, "$safeName.m4a")
            FileOutputStream(outFile).use { out -> file.inputStream().use { it.copyTo(out) } }
        }
        true
    } catch (e: Exception) {
        false
    }
}

private fun saveAsSystemRingtone(context: Context, file: File, name: String): Boolean {
    val safeName = name.replace(Regex("[^a-zA-Z0-9 _-]"), "").ifBlank { "ringtone" }
    Log.d("RingR", "saveAsSystemRingtone: name=$safeName, file=$file, exists=${file.exists()}, len=${file.length()}")
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            // Remove any existing entries with same name
            val delSelection = "${MediaStore.Audio.Media.DISPLAY_NAME}=?"
            val delArgs = arrayOf("$safeName.m4a")
            val deleted = resolver.delete(collectionUri, delSelection, delArgs)
            Log.d("RingR", "saveAsSystemRingtone: deleted $deleted existing entries")

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeName.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.TITLE, safeName)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collectionUri, values)
            Log.d("RingR", "saveAsSystemRingtone: insert uri=$uri")
            if (uri == null) { Log.e("RingR", "insert returned null"); return false }
            val out = resolver.openOutputStream(uri)
            Log.d("RingR", "saveAsSystemRingtone: outputStream=$out")
            if (out == null) { Log.e("RingR", "openOutputStream returned null"); return false }
            out.use { outStream -> file.inputStream().use { it.copyTo(outStream) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            val updated = resolver.update(uri, values, null, null)
            Log.d("RingR", "saveAsSystemRingtone: updated=$updated rows")
        } else {
            @Suppress("DEPRECATION")
            val ringtonesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
            ringtonesDir.mkdirs()
            val outFile = File(ringtonesDir, "$safeName.m4a")
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
        Log.e("RingR", "saveAsSystemRingtone exception", e)
        false
    }
}

private fun setAsDefaultRingtone(context: Context, file: File, name: String) {
    val safeName = name.replace(Regex("[^a-zA-Z0-9 _-]"), "").ifBlank { "ringtone" }
    Log.d("RingR", "setAsDefaultRingtone: name=$safeName")
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME}=?"
        val args = arrayOf("$safeName.m4a")
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
        val outFile = File(ringtonesDir, "$safeName.m4a")
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
