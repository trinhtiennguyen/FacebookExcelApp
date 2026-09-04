package com.example.facebookexcel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

private val ColLink = 330.dp
private val ColTitle = 300.dp
private val ColTime = 210.dp
private val ColImage = 150.dp
private val ColLoad = 100.dp
private val ColDelete = 70.dp
private val RowHeight = 82.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FacebookExcelScreen()
            }
        }
    }
}

@Composable
fun FacebookExcelScreen(vm: MainViewModel = viewModel()) {
    val items by vm.items.collectAsState()
    val loadingIds by vm.loadingIds.collectAsState()
    val message by vm.message.collectAsState()
    var token by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    val horizontal = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Facebook Excel") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = { vm.saveAll() }) {
                        Text("SAVE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nhập link Facebook vào dòng cuối → tự thêm dòng mới",
                    modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.addEmptyRow() }) {
                    Text("+ Dòng")
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(horizontal)
            ) {
                Column(Modifier.width(ColLink + ColTitle + ColTime + ColImage + ColLoad + ColDelete)) {
                    HeaderRow()
                    LazyColumn {
                        items(items, key = { it.id }) { item ->
                            DataRow(
                                item = item,
                                loading = item.id in loadingIds,
                                onLink = { vm.update(item, link = it) },
                                onTitle = { vm.update(item, title = it) },
                                onTime = { vm.update(item, time = it) },
                                onLoad = { vm.loadFacebook(item, token) },
                                onDelete = { vm.delete(item) }
                            )
                        }

                        item {
                            NewRow(
                                onAdd = { link ->
                                    vm.addEmptyRow()
                                    // The last empty row is intentionally available.
                                    // User can paste a URL into any row.
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Facebook Graph API") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Access Token")
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            minLines = 3
                        )
                        Text(
                            "Token chỉ dùng trong RAM của app và không được ghi vào SQLite.",
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { showSettings = false }) {
                        Text("OK")
                    }
                }
            )
        }

        message?.let {
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(2500)
                vm.clearMessage()
            }
            SnackbarHost(
                hostState = remember { SnackbarHostState() },
                modifier = Modifier.padding(bottom = 70.dp)
            )
            ToastLikeMessage(it)
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(
        Modifier
            .height(48.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("LINK", ColLink)
        HeaderCell("TIÊU ĐỀ", ColTitle)
        HeaderCell("THỜI GIAN", ColTime)
        HeaderCell("ẢNH", ColImage)
        HeaderCell("LOAD", ColLoad)
        HeaderCell("", ColDelete)
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .border(0.5.dp, MaterialTheme.colorScheme.outline),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun DataRow(
    item: com.example.facebookexcel.data.FacebookItem,
    loading: Boolean,
    onLink: (String) -> Unit,
    onTitle: (String) -> Unit,
    onTime: (String) -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .height(RowHeight)
            .fillMaxWidth()
    ) {
        TableTextField(item.link, onLink, ColLink)
        TableTextField(item.title, onTitle, ColTitle)
        TableTextField(item.time, onTime, ColTime)
        ImageCell(item.image)
        Box(
            Modifier
                .width(ColLoad)
                .fillMaxHeight()
                .border(0.5.dp, MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            } else {
                Button(
                    onClick = onLoad,
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) { Text("LOAD") }
            }
        }
        Box(
            Modifier
                .width(ColDelete)
                .fillMaxHeight()
                .border(0.5.dp, MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete")
            }
        }
    }
}

@Composable
private fun NewRow(onAdd: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(false) }

    Row(
        Modifier.height(RowHeight).fillMaxWidth()
    ) {
        Box(
            Modifier
                .width(ColLink)
                .fillMaxHeight()
                .border(0.5.dp, MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    val isFacebook =
                        it.contains("facebook.com/", true) ||
                        it.contains("fb.watch/", true)

                    if (isFacebook && !added) {
                        added = true
                        onAdd(it.trim())
                        value = ""
                    }
                },
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text("Dán link Facebook...") },
                singleLine = true
            )
        }
        HeaderCell("", ColTitle)
        HeaderCell("", ColTime)
        HeaderCell("", ColImage)
        HeaderCell("", ColLoad)
        HeaderCell("", ColDelete)
    }
}

@Composable
private fun TableTextField(
    value: String,
    onChange: (String) -> Unit,
    width: androidx.compose.ui.unit.Dp
) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .border(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
        )
    }
}

@Composable
private fun ImageCell(url: String) {
    Box(
        Modifier
            .width(ColImage)
            .fillMaxHeight()
            .border(0.5.dp, MaterialTheme.colorScheme.outline),
        contentAlignment = Alignment.Center
    ) {
        if (url.isBlank()) {
            Text("—")
        } else {
            AsyncImage(
                model = url,
                contentDescription = "Facebook image",
                modifier = Modifier
                    .padding(4.dp)
                    .size(68.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ToastLikeMessage(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = 82.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text,
                Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                fontSize = 13.sp
            )
        }
    }
}
