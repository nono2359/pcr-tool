package cn.wthee.pcrtool.ui.spine

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.core.net.toUri
import cn.wthee.pcrtool.R
import cn.wthee.pcrtool.data.enums.AppThemeMode
import cn.wthee.pcrtool.data.enums.MainIconType
import cn.wthee.pcrtool.data.preferences.SettingPreferencesKeys
import cn.wthee.pcrtool.ui.dataStoreSetting
import cn.wthee.pcrtool.ui.components.MainSmallFab
import cn.wthee.pcrtool.ui.theme.PCRToolComposeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File

class SpineViewerActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var modelProvider: SpineModelProvider
    private val backButtonVisible = mutableStateOf(true)
    private val gifSaveReady = mutableStateOf(false)
    private val gifSaved = mutableStateOf(false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelProvider = SpineModelProvider(this)
        val themeMode = runBlocking {
            dataStoreSetting.data.first()[SettingPreferencesKeys.SP_THEME_MODE] ?: AppThemeMode.SYSTEM
        }
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val viewerDark = themeMode == AppThemeMode.NIGHT || (themeMode == AppThemeMode.SYSTEM && systemDark)
        window.decorView.setBackgroundColor(
            if (viewerDark) android.graphics.Color.rgb(15, 23, 42)
            else android.graphics.Color.rgb(248, 250, 252)
        )
        webView = WebView(this).apply {
            setBackgroundColor(if (viewerDark) android.graphics.Color.rgb(15, 23, 42) else android.graphics.Color.rgb(248, 250, 252))
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    if (uri.host != APP_HOST) return null
                    modelProvider.handle(uri)?.let { return it }
                    if (uri.path == "/app-font/genei_latego_p_v2.ttf") return WebResourceResponse("font/ttf", null, resources.openRawResource(R.font.genei_latego_p_v2))
                    if (uri.path?.startsWith("/assets/") == true) return assetResponse(uri.path!!.removePrefix("/assets/"))
                    return super.shouldInterceptRequest(view, request)
                }
            }
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            addJavascriptInterface(GifBridge(), "PcrToolAndroid")
        }
        val container = FrameLayout(this).apply {
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                ComposeView(this@SpineViewerActivity).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        PCRToolComposeTheme(darkTheme = viewerDark) {
                            if (backButtonVisible.value) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (gifSaveReady.value) {
                                        MainSmallFab(
                                            iconType = if (gifSaved.value) MainIconType.DOWNLOAD_DONE else MainIconType.DOWNLOAD,
                                            text = stringResource(
                                                if (gifSaved.value) R.string.saved else R.string.title_dialog_save_img
                                            ),
                                            onClick = { webView.evaluateJavascript("window.pcrToolSaveGif()", null) }
                                        )
                                    }
                                    MainSmallFab(
                                        iconType = MainIconType.BACK,
                                        onClick = { finish() }
                                    )
                                }
                            }
                        }
                    }
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.BOTTOM
                ).apply {
                    val margin = (16 * resources.displayMetrics.density).toInt()
                    marginEnd = margin
                    bottomMargin = margin
                }
            )
        }
        setContentView(container)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (webView.canGoBack()) webView.goBack() else finish() }
        })
        val source = intent.getStringExtra(EXTRA_SOURCE_URL).orEmpty().toUri()
        val query = buildList {
            source.getQueryParameter("unitId")?.let { add("unitId=$it") }
            source.getQueryParameter("enemyId")?.let { add("enemyId=$it") }
            source.getQueryParameter("type")?.let { add("type=$it") }
            add("theme=${AppThemeMode.queryValue(themeMode)}")
        }.joinToString("&")
        webView.loadUrl("https://$APP_HOST/assets/spine/index.html" + if (query.isEmpty()) "" else "?$query")
    }

    private fun assetResponse(path: String): WebResourceResponse = try {
        val mime = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"; "js" -> "application/javascript"; "css" -> "text/css"
            "json" -> "application/json"; "png" -> "image/png"; "atlas" -> "text/plain"
            else -> "application/octet-stream"
        }
        WebResourceResponse(mime, if (mime.startsWith("text/") || mime.contains("json") || mime.contains("javascript")) "UTF-8" else null, assets.open(path))
    } catch (error: Exception) {
        WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream((error.message ?: "not found").toByteArray()))
    }

    inner class GifBridge {
        @JavascriptInterface
        fun setBackButtonVisible(visible: Boolean) {
            runOnUiThread { backButtonVisible.value = visible }
        }

        @JavascriptInterface
        fun setGifState(ready: Boolean, saved: Boolean) {
            runOnUiThread {
                gifSaveReady.value = ready
                gifSaved.value = saved
            }
        }

        @JavascriptInterface
        fun isGifSaved(fileName: String): Boolean = gifExists(safeGifName(fileName))

        @JavascriptInterface
        fun showGifExists(fileName: String) {
            runOnUiThread {
                Toast.makeText(this@SpineViewerActivity, "/Pictures/pcr/${safeGifName(fileName)} は保存済みです", Toast.LENGTH_LONG).show()
            }
        }

        @JavascriptInterface
        fun saveGif(base64: String, fileName: String) {
            runCatching {
                val savedName = safeGifName(fileName)
                if (gifExists(savedName)) error("/Pictures/pcr/$savedName は保存済みです")
                val bytes = Base64.decode(base64.substringAfter(','), Base64.DEFAULT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, savedName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/pcr")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("保存先を作成できません")
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("保存先を開けません")
                } else {
                    val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "pcr")
                    directory.mkdirs()
                    File(directory, savedName).writeBytes(bytes)
                }
                savedName
            }.onSuccess { savedName ->
                runOnUiThread {
                    Toast.makeText(this@SpineViewerActivity, "/Pictures/pcr/$savedName に保存しました", Toast.LENGTH_LONG).show()
                    webView.evaluateJavascript("window.pcrToolGifSaved(${org.json.JSONObject.quote(savedName)})", null)
                }
            }.onFailure { error ->
                runOnUiThread {
                    Toast.makeText(this@SpineViewerActivity, "GIF保存に失敗しました: ${error.message}", Toast.LENGTH_LONG).show()
                    webView.evaluateJavascript(
                        "window.pcrToolGifSaveFailed(${org.json.JSONObject.quote(error.message ?: "保存できませんでした")})",
                        null
                    )
                }
            }
        }

        private fun safeGifName(requestedName: String): String =
            requestedName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "spine.gif" }

        private fun gifExists(name: String): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "pcr/$name"
                ).exists()
            }
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?"
            val args = arrayOf(name, Environment.DIRECTORY_PICTURES + "/pcr/")
            return contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { it.moveToFirst() } == true
        }
    }
    override fun onDestroy() { webView.stopLoading(); webView.destroy(); super.onDestroy() }

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val EXTRA_SOURCE_URL = "source_url"
        fun open(context: Context, sourceUrl: String) {
            context.startActivity(Intent(context, SpineViewerActivity::class.java).putExtra(EXTRA_SOURCE_URL, sourceUrl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
