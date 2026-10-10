package com.tabslify.core.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.prvt
import com.tabslify.quiethoursnotificationhelper.isLaptopConnected
import com.tabslify.quiethoursnotificationhelper.isLaptopConnectedFlow
import com.tabslify.quiethoursnotificationhelper.laptopIp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.cancellation.CancellationException

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("sync_prefs", MODE_PRIVATE)
        val syncActive = prefs.getBoolean("sync_active", false)
        val syncUntil = prefs.getLong("sync_until", 0L)
        if (syncActive && syncUntil > System.currentTimeMillis()) {
            isLaptopConnected = true
        }

        when (intent?.action) {
            Intent.ACTION_SEND -> handleSingleShare(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleShare(intent)
            else -> {
                Toast.makeText(this, getString(R.string.ungultiger_share_intent), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun handleSingleShare(intent: Intent) {
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (uri != null) {
            showConfirmationDialog(listOf(uri))
        } else {
            Toast.makeText(this, getString(R.string.keine_datei_gefunden), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun handleMultipleShare(intent: Intent) {
        val uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (!uris.isNullOrEmpty()) {
            showConfirmationDialog(uris)
        } else {
            Toast.makeText(this, getString(R.string.keine_dateien_gefunden), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showConfirmationDialog(uris: List<Uri>) {
        setContent {
            MaterialTheme {
                val laptopConnected by isLaptopConnectedFlow.collectAsState()
                SaveToPrivateStorageScreen(
                    fileCount = uris.size,
                    isLaptopConnected = laptopConnected,
                    onSaveLocally = { saveFilesToPrivateStorage(uris) },
                    onSendToLaptop = { sendImagesToLaptop(uris) },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun sendImagesToLaptop(uris: List<Uri>) {
        if (!prvt()) return
        setContent {
            MaterialTheme {
                ProcessingScreen(fileCount = uris.size)
            }
        }

        lifecycleScope.launch {
            var successCount = 0

            for (uri in uris) {
                try {
                    currentCoroutineContext().ensureActive()
                    val bytes = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: continue

                    val fileName = getFileNameFromUri(uri)
                    val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

                    val sent = withContext(Dispatchers.IO) {
                        trySendImageToLaptop(bytes, fileName, mimeType)
                    }

                    if (sent) successCount++
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }

            withContext(Dispatchers.Main) {
                val message = when {
                    successCount > 0 -> resources.getQuantityString(R.plurals.bilder_an_laptop_gesendet, successCount, successCount)
                    else -> getString(R.string.senden_fehlgeschlagen)
                }
                Toast.makeText(this@ShareActivity, message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun trySendImageToLaptop(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): Boolean {
        return !(laptopIp.isEmpty() || !prvt()) && try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(laptopIp, Config.IMAGE_SHARE_PORT), 3000)
                socket.getOutputStream().use { out ->
                    val header = "$fileName|$mimeType\n".toByteArray(Charsets.UTF_8)
                    out.write(header)
                    out.write(bytes)
                    out.flush()
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "unknown_file_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
        }
        return fileName
    }

    private fun getPrivateStorageDirectory(): File {
        val privateDir = File(filesDir, "shared_files")
        if (!privateDir.exists()) {
            privateDir.mkdirs()
        }
        return privateDir
    }

    private fun saveFilesToPrivateStorage(uris: List<Uri>) {
        setContent {
            MaterialTheme {
                ProcessingScreen(fileCount = uris.size)
            }
        }

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0

            try {
                val privateDir = getPrivateStorageDirectory()

                for (uri in uris) {
                    try {
                        currentCoroutineContext().ensureActive()
                        val fileName = getFileNameFromUri(uri)

                        val targetFile = getUniqueFile(privateDir, fileName)

                        val success = withContext(Dispatchers.IO) {
                            copyUriToFile(uri, targetFile)
                        }

                        if (success) {
                            successCount++
                            withContext(Dispatchers.IO) {
                                try {
                                    contentResolver.delete(uri, null, null)
                                } catch (_: Exception) {
                                }
                            }
                        } else {
                            failCount++
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    val message = when {
                        failCount == 0 && successCount > 0 -> resources.getQuantityString(R.plurals.dateien_gespeichert, successCount, successCount)
                        successCount == 0 -> getString(R.string.speichern_fehlgeschlagen)
                        else -> getString(R.string.erfolgreich_fehlgeschlagen, successCount, failCount)
                    }
                    Toast.makeText(this@ShareActivity, message, Toast.LENGTH_LONG).show()
                    finish()
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ShareActivity, getString(R.string.fehler_msg, e.message), Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
            }
        }
    }

    private fun getUniqueFile(directory: File, fileName: String): File {
        var file = File(directory, fileName)
        var counter = 1

        while (file.exists()) {
            val nameWithoutExt = fileName.substringBeforeLast(".")
            val extension =
                if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
            file = File(directory, "${nameWithoutExt}_$counter$extension")
            counter++
        }

        return file
    }

    private fun copyUriToFile(uri: Uri, targetFile: File): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                targetFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
fun SaveToPrivateStorageScreen(
    fileCount: Int,
    isLaptopConnected: Boolean,
    onSaveLocally: () -> Unit,
    onSendToLaptop: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.datei_speichern),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = pluralStringResource(R.plurals.dateien, fileCount, fileCount),
                    fontSize = 16.sp,
                    color = Color.LightGray
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = Color.Gray
                )

                if (prvt()) {
                    if (isLaptopConnected) {
                        Button(
                            onClick = onSendToLaptop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                stringResource(R.string.an_laptop_senden),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.laptop_nicht_verbunden), fontSize = 16.sp, color = Color.White)
                        }
                    }
                }

                Button(
                    onClick = onSaveLocally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.lokal_speichern), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.abbrechen), color = Color.Red, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun ProcessingScreen(fileCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2A2A)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = Color(0xFF4CAF50)
                )

                Text(
                    text = stringResource(R.string.speichere),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = pluralStringResource(R.plurals.dateien_werden_gespeichert, fileCount, fileCount),
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}