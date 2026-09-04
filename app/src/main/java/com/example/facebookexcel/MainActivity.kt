package com.example.facebookexcel

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.viewinterop.AndroidView
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookExcelScreen(
    vm: MainViewModel = viewModel()
) {
    val items by vm.items.collectAsState()
    val loadingIds by vm.loadingIds.collectAsState()
    val message by vm.message.collectAsState()

    /*
     * Graph API token:
     * - Chỉ giữ trong RAM.
     * - Không ghi vào SQLite.
     */
    var graphApiToken by remember {
        mutableStateOf("")
    }

    var showSettings by remember {
        mutableStateOf(false)
    }

    var showFacebookLogin by remember {
        mutableStateOf(false)
    }

    /*
     * Trạng thái phiên Facebook.
     *
     * Không đọc hoặc hiển thị giá trị cookie.
     * Chỉ kiểm tra xem CookieManager có cookie đăng nhập
     * phổ biến của Facebook hay chưa.
     */
    var facebookLoggedIn by remember {
        mutableStateOf(false)
    }

    val horizontal = rememberScrollState()

    /*
     * Khi mở Cài đặt, kiểm tra lại trạng thái Facebook Login.
     */
    LaunchedEffect(showSettings) {
        if (showSettings) {
            facebookLoggedIn = hasFacebookLoginSession()
        }
    }

    /*
     * Khi đóng màn hình Login, kiểm tra lại cookie.
     */
    LaunchedEffect(showFacebookLogin) {
        if (!showFacebookLogin) {
            facebookLoggedIn = hasFacebookLoginSession()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Facebook Excel")
                },
                actions = {
                    IconButton(
                        onClick = {
                            facebookLoggedIn = hasFacebookLoginSession()
                            showSettings = true
                        }
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Cài đặt"
                        )
                    }
                }
            )
        },

        bottomBar = {
            Surface(
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            vm.saveAll()
                        }
                    ) {
                        Text(
                            "SAVE",
                            fontWeight = FontWeight.Bold
                        )
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

            /*
             * Thanh hướng dẫn.
             */
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(
                        horizontal = 10.dp,
                        vertical = 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Nhập link Facebook vào dòng cuối → tự thêm dòng mới",
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = {
                        vm.addEmptyRow()
                    }
                ) {
                    Text("+ Dòng")
                }
            }

            /*
             * Bảng dữ liệu.
             */
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(horizontal)
            ) {
                Column(
                    Modifier.width(
                        ColLink +
                            ColTitle +
                            ColTime +
                            ColImage +
                            ColLoad +
                            ColDelete
                    )
                ) {

                    HeaderRow()

                    LazyColumn {

                        items(
                            items,
                            key = { it.id }
                        ) { item ->

                            DataRow(
                                item = item,
                                loading = item.id in loadingIds,

                                onLink = {
                                    vm.update(
                                        item,
                                        link = it
                                    )
                                },

                                onTitle = {
                                    vm.update(
                                        item,
                                        title = it
                                    )
                                },

                                onTime = {
                                    vm.update(
                                        item,
                                        time = it
                                    )
                                },

                                onLoad = {
                                    vm.loadFacebook(
                                        item,
                                        graphApiToken
                                    )
                                },

                                onDelete = {
                                    vm.delete(item)
                                }
                            )
                        }

                        /*
                         * Dòng nhập link mới.
                         */
                        item {
                            NewRow(
                                onAdd = { link ->
                                    vm.addLinkRow(link)
                                }
                            )
                        }
                    }
                }
            }
        }

        /*
         * =========================================================
         * CÀI ĐẶT
         * =========================================================
         *
         * Facebook Login và Graph API hoàn toàn tách riêng.
         */
        if (showSettings) {

            AlertDialog(
                onDismissRequest = {
                    showSettings = false
                },

                title = {
                    Text("Cài đặt Facebook")
                },

                text = {

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        /*
                         * =================================================
                         * FACEBOOK LOGIN
                         * =================================================
                         */
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.medium
                        ) {

                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {

                                Text(
                                    "Facebook Login",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )

                                if (facebookLoggedIn) {

                                    Text(
                                        "Trạng thái: Đã đăng nhập ✓",
                                        fontSize = 14.sp
                                    )

                                    Text(
                                        "Phiên đăng nhập này được dùng khi LOAD Reel bằng WebView.",
                                        fontSize = 12.sp
                                    )

                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            clearFacebookLoginSession()
                                            facebookLoggedIn = false
                                        }
                                    ) {
                                        Text("XÓA PHIÊN ĐĂNG NHẬP")
                                    }

                                } else {

                                    Text(
                                        "Trạng thái: Chưa đăng nhập",
                                        fontSize = 14.sp
                                    )

                                    Text(
                                        "Đăng nhập Facebook trong app để LOAD các Reel/share link cần phiên đăng nhập.",
                                        fontSize = 12.sp
                                    )

                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            showSettings = false
                                            showFacebookLogin = true
                                        }
                                    ) {
                                        Text("ĐĂNG NHẬP FACEBOOK")
                                    }
                                }
                            }
                        }

                        /*
                         * =================================================
                         * GRAPH API
                         * =================================================
                         */
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.medium
                        ) {

                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {

                                Text(
                                    "Facebook Graph API",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )

                                Text(
                                    "Access Token (tùy chọn)",
                                    fontSize = 14.sp
                                )

                                OutlinedTextField(
                                    value = graphApiToken,
                                    onValueChange = {
                                        graphApiToken = it
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    minLines = 3,
                                    placeholder = {
                                        Text("Nhập Graph API Access Token...")
                                    }
                                )

                                Text(
                                    "Token chỉ được giữ trong RAM của app và không được ghi vào SQLite.",
                                    fontSize = 12.sp
                                )
                            }
                        }

                        /*
                         * Giải thích rõ hai cơ chế.
                         */
                        Text(
                            "Lưu ý: Facebook Login và Graph API Access Token là hai cơ chế riêng biệt. Để LOAD Reel từ tài khoản Facebook của mày, ưu tiên đăng nhập Facebook trong app.",
                            fontSize = 12.sp
                        )
                    }
                },

                confirmButton = {

                    Button(
                        onClick = {
                            showSettings = false
                        }
                    ) {
                        Text("ĐÓNG")
                    }
                }
            )
        }

        /*
         * =========================================================
         * FACEBOOK LOGIN WEBVIEW
         * =========================================================
         */
        if (showFacebookLogin) {

            AlertDialog(
                onDismissRequest = {
                    showFacebookLogin = false
                },

                title = {
                    Text("Đăng nhập Facebook")
                },

                text = {

                    Column(
                        Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "Đăng nhập tài khoản Facebook của mày trong cửa sổ bên dưới."
                        )

                        Spacer(
                            Modifier.height(6.dp)
                        )

                        Text(
                            "Sau khi đăng nhập thành công, bấm Đóng rồi quay lại bảng và LOAD Reel.",
                            fontSize = 12.sp
                        )

                        Spacer(
                            Modifier.height(8.dp)
                        )

                        AndroidView(
                            factory = { context ->

                                CookieManager
                                    .getInstance()
                                    .setAcceptCookie(true)

                                WebView(context).apply {

                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true

                                    settings.userAgentString =
                                        FACEBOOK_MOBILE_UA

                                    webViewClient =
                                        object : WebViewClient() {

                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                url: String?
                                            ): Boolean {
                                                return false
                                            }
                                        }

                                    loadUrl(
                                        "https://m.facebook.com/"
                                    )
                                }
                            },

                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                        )
                    }
                },

                confirmButton = {

                    Button(
                        onClick = {
                            facebookLoggedIn =
                                hasFacebookLoginSession()

                            showFacebookLogin = false
                        }
                    ) {
                        Text("ĐÓNG")
                    }
                }
            )
        }

        /*
         * =========================================================
         * MESSAGE
         * =========================================================
         */
        message?.let { msg ->

            LaunchedEffect(msg) {

                kotlinx.coroutines.delay(2500)

                vm.clearMessage()
            }

            ToastLikeMessage(msg)
        }
    }
}

/**
 * Kiểm tra sơ bộ xem CookieManager có phiên Facebook hay chưa.
 *
 * Không đọc/hiển thị giá trị cookie.
 */
private fun hasFacebookLoginSession(): Boolean {

    val cookieFacebook =
        CookieManager
            .getInstance()
            .getCookie("https://www.facebook.com")
            .orEmpty()

    val cookieMobile =
        CookieManager
            .getInstance()
            .getCookie("https://m.facebook.com")
            .orEmpty()

    val cookies =
        "$cookieFacebook;$cookieMobile"

    return cookies.contains("c_user=") &&
        cookies.contains("xs=")
}

/**
 * Xóa cookie Facebook khỏi phiên WebView.
 */
private fun clearFacebookLoginSession() {

    val cookieManager =
        CookieManager.getInstance()

    cookieManager.removeAllCookies(null)
    cookieManager.flush()
}

/**
 * User-Agent dùng chung cho Facebook Login và LOAD WebView.
 */
private const val FACEBOOK_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) " +
        "AppleWebKit/537.36 " +
        "Chrome/131.0 Mobile Safari/537.36"


@Composable
private fun HeaderRow() {

    Row(
        Modifier
            .height(48.dp)
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primaryContainer
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        HeaderCell(
            "LINK",
            ColLink
        )

        HeaderCell(
            "TIÊU ĐỀ",
            ColTitle
        )

        HeaderCell(
            "THỜI GIAN",
            ColTime
        )

        HeaderCell(
            "ẢNH",
            ColImage
        )

        HeaderCell(
            "LOAD",
            ColLoad
        )

        HeaderCell(
            "",
            ColDelete
        )
    }
}


@Composable
private fun HeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp
) {

    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outline
            ),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
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

        TableTextField(
            item.link,
            onLink,
            ColLink
        )

        TableTextField(
            item.title,
            onTitle,
            ColTitle
        )

        TableTextField(
            item.time,
            onTime,
            ColTime
        )

        ImageCell(
            item.image
        )

        Box(
            Modifier
                .width(ColLoad)
                .fillMaxHeight()
                .border(
                    0.5.dp,
                    MaterialTheme.colorScheme.outline
                ),
            contentAlignment = Alignment.Center
        ) {

            if (loading) {

                CircularProgressIndicator(
                    Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )

            } else {

                Button(
                    onClick = onLoad,
                    contentPadding =
                        PaddingValues(
                            horizontal = 10.dp
                        )
                ) {
                    Text("LOAD")
                }
            }
        }

        Box(
            Modifier
                .width(ColDelete)
                .fillMaxHeight()
                .border(
                    0.5.dp,
                    MaterialTheme.colorScheme.outline
                ),
            contentAlignment = Alignment.Center
        ) {

            IconButton(
                onClick = onDelete
            ) {

                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete"
                )
            }
        }
    }
}


@Composable
private fun NewRow(
    onAdd: (String) -> Unit
) {

    var value by remember {
        mutableStateOf("")
    }

    var added by remember {
        mutableStateOf(false)
    }

    Row(
        Modifier
            .height(RowHeight)
            .fillMaxWidth()
    ) {

        Box(
            Modifier
                .width(ColLink)
                .fillMaxHeight()
                .border(
                    0.5.dp,
                    MaterialTheme.colorScheme.outline
                ),
            contentAlignment = Alignment.Center
        ) {

            OutlinedTextField(
                value = value,

                onValueChange = {

                    value = it

                    val isFacebook =
                        it.contains(
                            "facebook.com/",
                            true
                        ) ||
                        it.contains(
                            "fb.watch/",
                            true
                        )

                    if (
                        isFacebook &&
                        !added
                    ) {

                        added = true

                        onAdd(
                            it.trim()
                        )

                        value = ""
                    }
                },

                modifier = Modifier.fillMaxSize(),

                placeholder = {
                    Text(
                        "Dán link Facebook..."
                    )
                },

                singleLine = true
            )
        }

        HeaderCell(
            "",
            ColTitle
        )

        HeaderCell(
            "",
            ColTime
        )

        HeaderCell(
            "",
            ColImage
        )

        HeaderCell(
            "",
            ColLoad
        )

        HeaderCell(
            "",
            ColDelete
        )
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
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outline
            )
    ) {

        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxSize(),
            singleLine = true,
            textStyle =
                LocalTextStyle.current.copy(
                    fontSize = 13.sp
                )
        )
    }
}


@Composable
private fun ImageCell(
    url: String
) {

    Box(
        Modifier
            .width(ColImage)
            .fillMaxHeight()
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outline
            ),
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
                    .clip(
                        MaterialTheme.shapes.small
                    ),
                contentScale = ContentScale.Crop
            )
        }
    }
}


@Composable
private fun ToastLikeMessage(
    text: String
) {

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
                Modifier.padding(
                    horizontal = 18.dp,
                    vertical = 12.dp
                ),
                fontSize = 13.sp
            )
        }
    }
}
