package com.filer.android

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onPickFiles: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initDiscovery(context)
        viewModel.startScan()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Transfer", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (state.isConnected) {
                ExtendedFloatingActionButton(
                    onClick = onPickFiles,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Select Files") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ServerCard(state)
            Spacer(Modifier.height(12.dp))

            if (state.files.isNotEmpty()) {
                Text("Uploads", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.overallProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.files) { file ->
                        FileCard(file)
                    }
                }
            }

            if (state.transfers.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("History", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.transfers) { item ->
                        HistoryItem(item)
                    }
                }
            }

            if (!state.isConnected && !state.isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("No server found", color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.startScan() }) {
                            Text("Scan Again")
                        }
                    }
                }
            }

            if (state.isScanning) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Scanning network...")
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(state: TransferState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isConnected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (state.isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (state.isConnected) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (state.isConnected) "Connected" else "Disconnected",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                if (state.isConnected) {
                    Text(state.serverUrl, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun FileCard(file: TransferFile) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FilePresent, contentDescription = null,
                    modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.fileName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatSize2(file.size), fontSize = 12.sp, color = Color.Gray)
                }
                Text(file.status, fontSize = 12.sp, color = when (file.status) {
                    "Completed" -> Color(0xFF2E7D32)
                    "Failed" -> Color(0xFFC62828)
                    else -> MaterialTheme.colorScheme.primary
                })
            }
            if (file.status == "Uploading..." || file.progress > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { file.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
                Text("${file.progress}%", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun HistoryItem(item: TransferHistory) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (item.status == "Success") Icons.Default.CheckCircleOutline else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (item.status == "Success") Color(0xFF2E7D32) else Color(0xFFC62828),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.fileName, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.size} - ${item.time}", fontSize = 11.sp, color = Color.Gray)
        }
        Text(item.status, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = if (item.status == "Success") Color(0xFF2E7D32) else Color(0xFFC62828))
    }
}

private fun formatSize2(bytes: Long): String {
    val sizes = arrayOf("B", "KB", "MB", "GB")
    var i = 0
    var d = bytes.toDouble()
    while (d >= 1024 && i < 3) { d /= 1024; i++ }
    return "%.1f %s".format(d, sizes[i])
}
