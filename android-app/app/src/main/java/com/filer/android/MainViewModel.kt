package com.filer.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TransferFile(
    val uri: Uri,
    val fileName: String = "",
    val size: Long = 0,
    val progress: Int = 0,
    val status: String = "Pending"
)

data class TransferState(
    val serverUrl: String = "",
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val files: List<TransferFile> = emptyList(),
    val transfers: List<TransferHistory> = emptyList(),
    val overallProgress: Int = 0
)

data class TransferHistory(
    val fileName: String,
    val size: String,
    val status: String,
    val time: String
)

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private var serverDiscovery: ServerDiscovery? = null

    fun initDiscovery(context: Context) {
        serverDiscovery = ServerDiscovery(context)
    }

    fun startScan() {
        _state.value = _state.value.copy(isScanning = true)
        viewModelScope.launch {
            val url = serverDiscovery?.scan() ?: ""
            _state.value = _state.value.copy(
                serverUrl = url,
                isScanning = false,
                isConnected = url.isNotEmpty()
            )
        }
    }

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val resolved = uris.map { uri ->
                val (name, size) = resolveInfo(uri)
                TransferFile(uri = uri, fileName = name, size = size)
            }
            _state.value = _state.value.copy(
                files = _state.value.files + resolved
            )
            if (_state.value.files.isNotEmpty()) {
                uploadAll()
            }
        }
    }

    private suspend fun resolveInfo(uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
        try {
            val ctx = App.instance
            val cursor = ctx.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else "unknown"
                    val size = if (sizeIdx >= 0) it.getLong(sizeIdx) else 0L
                    return@withContext Pair(name, size)
                }
            }
        } catch (_: Exception) { }
        Pair("unknown", 0L)
    }

    private fun uploadAll() {
        viewModelScope.launch {
            val serverUrl = _state.value.serverUrl
            val files = _state.value.files
            for ((index, file) in files.withIndex()) {
                val updatedFiles = _state.value.files.toMutableList()
                updatedFiles[index] = file.copy(status = "Uploading...")
                _state.value = _state.value.copy(files = updatedFiles)

                val success = FileTransferService.upload(
                    url = "$serverUrl/upload",
                    fileUri = file.uri,
                    fileName = file.fileName,
                    onProgress = { progress ->
                        val p = _state.value.files.toMutableList()
                        p[index] = p[index].copy(progress = progress)
                        val overall = p.sumOf { it.progress } / p.size
                        _state.value = _state.value.copy(files = p, overallProgress = overall)
                    }
                )

                val p = _state.value.files.toMutableList()
                p[index] = p[index].copy(
                    status = if (success) "Completed" else "Failed",
                    progress = if (success) 100 else 0
                )
                val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val history = TransferHistory(
                    fileName = file.fileName,
                    size = formatSize(file.size),
                    status = if (success) "Success" else "Failed",
                    time = now
                )
                _state.value = _state.value.copy(
                    files = p,
                    transfers = listOf(history) + _state.value.transfers
                )
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        val sizes = arrayOf("B", "KB", "MB", "GB")
        var i = 0
        var d = bytes.toDouble()
        while (d >= 1024 && i < 3) { d /= 1024; i++ }
        return "%.1f %s".format(d, sizes[i])
    }
}
