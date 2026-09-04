package com.example.facebookexcel

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView

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

        // Cho phép cookie ngay từ khi app khởi động.
        CookieManager.getInstance().setAcceptCookie(true)

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

    var graphApiToken by remember {
        mutableStateOf("")
    }

    var showSettings by remember {
        mutableStateOf(false)
    }

    var showFacebookLogin by remember {
        mutableStateOf(false)
    }

    val horizontal = rememberScrollState()

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    Text("Facebook Excel")
                },

                actions = {
                    IconButton(
                        onClick = {
                            showSettings = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            Row(
                modifier = Modifier
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


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(horizontal)
            ) {

                Column(
                    modifier = Modifier.width(
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


        // ============================================================
        // SETTINGS
        // ============================================================

        if (showSettings) {

            FacebookSettingsDialog(
                graphApiToken = graphApiToken,

                onGraphApiTokenChange = {
                    graphApiToken = it
                },

                onLoginClick = {
                    showFacebookLogin = true
                },

                onDismiss = {
                    showSettings = false
                }
            )
        }


        // ============================================================
        // FACEBOOK LOGIN
        // ============================================================

        if (showFacebookLogin) {

            FacebookLoginDialog(
                onDismiss = {
                    showFacebookLogin = false
                }
            )
        }


        // ============================================================
        // MESSAGE
        // ============================================================

        if (message != null) {

            LaunchedEffect(message) {
                delay(3000)
                vm.clearMessage()
            }

            ToastLikeMessage(
                text = message!!
            )
        }
    }
}


/* ==================================================================
   FACEBOOK SETTINGS
   ================================================================== */

@Composable
private fun FacebookSettingsDialog(
    graphApiToken: String,
    onGraphApiTokenChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onDismiss: () -> Unit
) {

    val context = LocalContext.current

    val loggedIn = remember {
        mutableStateOf(
            hasFacebookLoginSession()
        )
    }


    AlertDialog(

        onDismissRequest = onDismiss,

        title = {
            Text("Cài đặt Facebook")
        },

        text = {

            Column(
                modifier = Modifier.fillMaxWidth(),

                verticalArrangement = Arrangement.spacedBy(
                    14.dp
                )
            ) {

                // ==================================================
                // FACEBOOK LOGIN
                // ==================================================

                Text(
                    "Facebook Login",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                Text(
                    if (loggedIn.value) {
                        "● Đã có phiên đăng nhập Facebook"
                    } else {
                        "● Chưa đăng nhập Facebook"
                    },

                    fontSize = 13.sp
                )


                Button(
                    modifier = Modifier.fillMaxWidth(),

                    onClick = {
                        onLoginClick()
                    }
                ) {

                    Text(
                        if (loggedIn.value) {
                            "MỞ FACEBOOK LOGIN"
                        } else {
                            "ĐĂNG NHẬP FACEBOOK"
                        }
                    )
                }


                HorizontalDivider()


                // ==================================================
                // GRAPH API
                // ==================================================

                Text(
                    "Facebook Graph API",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                Text(
                    "Access Token",
                    fontSize = 13.sp
                )

                OutlinedTextField(

                    value = graphApiToken,

                    onValueChange = {
                        onGraphApiTokenChange(it)
                    },

                    modifier = Modifier.fillMaxWidth(),

                    singleLine = false,

                    minLines = 3,

                    placeholder = {
                        Text(
                            "Dán Graph API Access Token vào đây..."
                        )
                    }
                )


                Text(
                    "Token chỉ được giữ trong RAM của app và không ghi vào SQLite.",
                    fontSize = 11.sp
                )
            }
        },

        confirmButton = {

            Button(
                onClick = onDismiss
            ) {
                Text("OK")
            }
        }
    )
}


/* ==================================================================
   FACEBOOK LOGIN WEBVIEW
   ================================================================== */

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FacebookLoginDialog(
    onDismiss: () -> Unit
) {

    val context = LocalContext.current

    var webView by remember {
        mutableStateOf<WebView?>(null)
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var progress by remember {
        mutableStateOf(0)
    }

    var errorText by remember {
        mutableStateOf<String?>(null)
    }

    var currentUrl by remember {
        mutableStateOf("https://www.facebook.com/")
    }


    Dialog(

        onDismissRequest = {
            webView?.stopLoading()
            webView?.destroy()
            webView = null

            onDismiss()
        },

        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {

        Surface(

            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(8.dp),

            shape = MaterialTheme.shapes.large,

            tonalElevation = 8.dp
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                // ==================================================
                // HEADER
                // ==================================================

                Row(

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 8.dp,
                            vertical = 6.dp
                        ),

                    verticalAlignment = Alignment.CenterVertically
                ) {

                    IconButton(
                        onClick = {

                            val wv = webView

                            if (wv != null && wv.canGoBack()) {
                                wv.goBack()
                            }
                        }
                    ) {

                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }


                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            "Đăng nhập Facebook",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            if (loading) {
                                "Đang tải Facebook... $progress%"
                            } else {
                                currentUrl
                            },

                            fontSize = 10.sp,

                            maxLines = 1
                        )
                    }


                    IconButton(

                        onClick = {

                            errorText = null
                            loading = true

                            webView?.reload()
                        }
                    ) {

                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Làm mới"
                        )
                    }


                    IconButton(

                        onClick = {

                            webView?.stopLoading()
                            webView?.destroy()

                            webView = null

                            onDismiss()
                        }
                    ) {

                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Đóng"
                        )
                    }
                }


                // ==================================================
                // PROGRESS
                // ==================================================

                if (loading) {

                    LinearProgressIndicator(
                        progress = {
                            progress / 100f
                        },

                        modifier = Modifier.fillMaxWidth()
                    )
                }


                // ==================================================
                // ERROR
                // ==================================================

                if (errorText != null) {

                    Surface(
                        modifier = Modifier.fillMaxWidth(),

                        color = MaterialTheme.colorScheme.errorContainer
                    ) {

                        Column(
                            modifier = Modifier.padding(10.dp)
                        ) {

                            Text(
                                errorText!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )


                            Spacer(
                                Modifier.height(6.dp)
                            )


                            Button(
                                onClick = {

                                    errorText = null
                                    loading = true

                                    webView?.loadUrl(
                                        "https://www.facebook.com/"
                                    )
                                }
                            ) {

                                Text("THỬ LẠI")
                            }
                        }
                    }
                }


                // ==================================================
                // WEBVIEW
                // ==================================================

                AndroidView(

                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),

                    factory = {

                        // ==================================================
                        // COOKIE
                        // ==================================================

                        val cookieManager =
                            CookieManager.getInstance()

                        cookieManager.setAcceptCookie(true)


                        // ==================================================
                        // CREATE WEBVIEW
                        // ==================================================

                        WebView(context).apply {

                            webView = this


                            // ------------------------------------------------
                            // HARDWARE RENDERING
                            // ------------------------------------------------

                            setLayerType(
                                View.LAYER_TYPE_HARDWARE,
                                null
                            )


                            // ------------------------------------------------
                            // WEB SETTINGS
                            // ------------------------------------------------

                            settings.apply {

                                javaScriptEnabled = true

                                javaScriptCanOpenWindowsAutomatically =
                                    true

                                domStorageEnabled = true

                                databaseEnabled = true

                                loadsImagesAutomatically = true

                                allowFileAccess = true

                                allowContentAccess = true

                                builtInZoomControls = false

                                displayZoomControls = false

                                setSupportZoom(false)

                                mediaPlaybackRequiresUserGesture =
                                    false

                                setSupportMultipleWindows(true)


                                // ------------------------------------------------
                                // QUAN TRỌNG:
                                // KHÔNG dùng WebView UA có chữ "wv".
                                // ------------------------------------------------

                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; " +
                                    "SM-A528B) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) " +
                                    "Chrome/131.0.0.0 Mobile Safari/537.36"
                            }


                            // ------------------------------------------------
                            // THIRD PARTY COOKIE
                            // ------------------------------------------------

                            cookieManager
                                .setAcceptThirdPartyCookies(
                                    this,
                                    true
                                )


                            // ------------------------------------------------
                            // WEBVIEW CLIENT
                            // ------------------------------------------------

                            webViewClient =
                                object : WebViewClient() {

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {

                                        return false
                                    }


                                    @Deprecated("Deprecated in API 24")
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        url: String?
                                    ): Boolean {

                                        return false
                                    }


                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?
                                    ) {

                                        super.onPageStarted(
                                            view,
                                            url,
                                            favicon
                                        )

                                        loading = true

                                        errorText = null

                                        currentUrl =
                                            url ?: ""
                                    }


                                    override fun onPageFinished(
                                        view: WebView?,
                                        url: String?
                                    ) {

                                        super.onPageFinished(
                                            view,
                                            url
                                        )

                                        loading = false

                                        currentUrl =
                                            url ?: ""


                                        // Đồng bộ cookie.
                                        CookieManager
                                            .getInstance()
                                            .flush()
                                    }


                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {

                                        super.onReceivedError(
                                            view,
                                            request,
                                            error
                                        )

                                        if (
                                            request?.isForMainFrame == true
                                        ) {

                                            loading = false

                                            errorText =
                                                "Không tải được Facebook: " +
                                                (
                                                    error?.description
                                                        ?.toString()
                                                        ?: "Lỗi không xác định"
                                                )
                                        }
                                    }
                                }


                            // ------------------------------------------------
                            // CHROME CLIENT
                            // ------------------------------------------------

                            webChromeClient =
                                object : WebChromeClient() {

                                    override fun onProgressChanged(
                                        view: WebView?,
                                        newProgress: Int
                                    ) {

                                        super.onProgressChanged(
                                            view,
                                            newProgress
                                        )

                                        progress =
                                            newProgress

                                        loading =
                                            newProgress < 100
                                    }
                                }


                            // ------------------------------------------------
                            // LOAD FACEBOOK
                            // ------------------------------------------------

                            loadUrl(
                                "https://www.facebook.com/"
                            )
                        }
                    }
                )


                // ==================================================
                // FOOTER
                // ==================================================

                Surface(
                    shadowElevation = 4.dp
                ) {

                    Row(

                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),

                        horizontalArrangement =
                            Arrangement.End,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            "Đăng nhập xong → bấm ĐÓNG → LOAD Reel",
                            modifier = Modifier.weight(1f),
                            fontSize = 11.sp
                        )


                        Button(

                            onClick = {

                                // Quan trọng:
                                // flush cookie trước khi đóng.
                                CookieManager
                                    .getInstance()
                                    .flush()

                                webView?.stopLoading()

                                webView?.destroy()

                                webView = null

                                onDismiss()
                            }
                        ) {

                            Text("ĐÓNG")
                        }
                    }
                }
            }
        }
    }
}


/* ==================================================================
   FACEBOOK LOGIN COOKIE CHECK
   ================================================================== */

private fun hasFacebookLoginSession(): Boolean {

    return try {

        val cookies =
            CookieManager
                .getInstance()
                .getCookie(
                    "https://www.facebook.com/"
                )
                .orEmpty()

        val hasUser =
            cookies.contains("c_user=")

        val hasSession =
            cookies.contains("xs=")

        hasUser && hasSession

    } catch (_: Exception) {

        false
    }
}


/* ==================================================================
   TABLE HEADER
   ================================================================== */

@Composable
private fun HeaderRow() {

    Row(

        modifier = Modifier
            .height(48.dp)
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primaryContainer
            ),

        verticalAlignment =
            Alignment.CenterVertically
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


/* ==================================================================
   HEADER CELL
   ================================================================== */

@Composable
private fun HeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp
) {

    Box(

        modifier = Modifier
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


/* ==================================================================
   DATA ROW
   ================================================================== */

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

        modifier = Modifier
            .height(RowHeight)
            .fillMaxWidth()
    ) {

        TableTextField(
            value = item.link,
            onChange = onLink,
            width = ColLink
        )


        TableTextField(
            value = item.title,
            onChange = onTitle,
            width = ColTitle
        )


        TableTextField(
            value = item.time,
            onChange = onTime,
            width = ColTime
        )


        ImageCell(
            item.image
        )


        Box(

            modifier = Modifier
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
                    modifier = Modifier.size(28.dp),
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

            modifier = Modifier
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
                    contentDescription = "Xóa"
                )
            }
        }
    }
}


/* ==================================================================
   NEW ROW
   ================================================================== */

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

        modifier = Modifier
            .height(RowHeight)
            .fillMaxWidth()
    ) {

        Box(

            modifier = Modifier
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
                            ignoreCase = true
                        ) ||
                        it.contains(
                            "fb.watch/",
                            ignoreCase = true
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
                    Text("Dán link Facebook...")
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


/* ==================================================================
   TEXT FIELD
   ================================================================== */

@Composable
private fun TableTextField(

    value: String,

    onChange: (String) -> Unit,

    width: androidx.compose.ui.unit.Dp
) {

    Box(

        modifier = Modifier
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


/* ==================================================================
   IMAGE CELL
   ================================================================== */

@Composable
private fun ImageCell(
    url: String
) {

    Box(

        modifier = Modifier
            .width(ColImage)
            .fillMaxHeight()
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outline
            ),

        contentAlignment =
            Alignment.Center
    ) {

        if (url.isBlank()) {

            Text("—")

        } else {

            AsyncImage(

                model = url,

                contentDescription =
                    "Facebook image",

                modifier = Modifier
                    .padding(4.dp)
                    .size(68.dp)
                    .clip(
                        MaterialTheme.shapes.small
                    ),

                contentScale =
                    ContentScale.Crop
            )
        }
    }
}


/* ==================================================================
   MESSAGE
   ================================================================== */

@Composable
private fun ToastLikeMessage(
    text: String
) {

    Box(

        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 82.dp),

        contentAlignment =
            Alignment.BottomCenter
    ) {

        Surface(

            tonalElevation = 6.dp,

            shadowElevation = 6.dp,

            shape =
                MaterialTheme.shapes.medium
        ) {

            Text(

                text,

                modifier = Modifier.padding(
                    horizontal = 18.dp,
                    vertical = 12.dp
                ),

                fontSize = 13.sp
            )
        }
    }
}
