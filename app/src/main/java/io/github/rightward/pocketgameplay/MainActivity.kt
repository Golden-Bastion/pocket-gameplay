package io.github.rightward.pocketgameplay

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var pathHandler: GamePathHandler
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var worker: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())

    private var webView: WebView? = null
    private var isPlaying = false
    private var scanGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        content = FrameLayout(this)
        setContentView(content)

        worker = Executors.newSingleThreadExecutor()
        pathHandler = GamePathHandler(contentResolver)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/game/", pathHandler)
            .build()

        showHome()
    }

    private fun showHome(message: String? = null) {
        isPlaying = false
        content.removeAllViews()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(32), dp(28), dp(32))
            setBackgroundColor(Color.rgb(16, 17, 20))
        }

        page.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, matchWidthWrapContent())

        page.addView(TextView(this).apply {
            text = "Play HTML5 games stored on your device."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(28))
        }, matchWidthWrapContent())

        page.addView(Button(this).apply {
            text = getString(R.string.choose_game_folder)
            isAllCaps = false
            setOnClickListener { chooseGameFolder() }
        }, matchWidthWrapContent())

        if (!message.isNullOrBlank()) {
            page.addView(TextView(this).apply {
                text = message
                textSize = 14f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, 0)
            }, matchWidthWrapContent())
        }

        val scroll = ScrollView(this).apply {
            addView(
                page,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        content.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun chooseGameFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        startActivityForResult(intent, REQUEST_GAME_FOLDER)
    }

    @Deprecated("Uses the platform folder picker for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_GAME_FOLDER || resultCode != RESULT_OK) return

        val treeUri = data?.data ?: return
        val readGrant = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (readGrant == 0) {
            showHome("Android did not grant read access to that folder.")
            return
        }

        try {
            contentResolver.takePersistableUriPermission(treeUri, readGrant)
        } catch (_: SecurityException) {
            showHome("Could not retain access to the selected folder. Please choose it again.")
            return
        }

        scanGameFolder(treeUri)
    }

    private fun scanGameFolder(treeUri: Uri) {
        val generation = ++scanGeneration
        showHome("Scanning for HTML and HTM files…")

        worker.execute {
            val root = DocumentFile.fromTreeUri(this, treeUri)
            val entries = root?.let(GameEntryPointScanner::scan).orEmpty()

            mainHandler.post {
                if (generation != scanGeneration || isFinishing) return@post

                if (root == null) {
                    showHome("Android could not open that folder.")
                } else if (entries.isEmpty()) {
                    showHome("No .html or .htm files were found in that folder.")
                } else {
                    chooseEntryPoint(root, entries)
                }
            }
        }
    }

    private fun chooseEntryPoint(root: DocumentFile, entries: List<HtmlEntryPoint>) {
        if (entries.size == 1) {
            openGame(root, entries.first())
            return
        }

        val labels = entries.map { it.relativePath }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose the game's start page")
            .setItems(labels) { _, selected ->
                openGame(root, entries[selected])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openGame(root: DocumentFile, entryPoint: HtmlEntryPoint) {
        pathHandler.setGameRoot(root)
        showPlayer()

        val encodedPath = entryPoint.relativePath
            .split('/')
            .joinToString("/") { Uri.encode(it) }
        webView?.loadUrl(OfflineGameWebViewClient.GAME_BASE_URL + encodedPath)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showPlayer() {
        isPlaying = true
        content.removeAllViews()

        val player = webView ?: WebView(this).also { view ->
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)

                allowFileAccess = false
                allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
            }
            view.webChromeClient = WebChromeClient()
            view.webViewClient = OfflineGameWebViewClient(assetLoader) {
                mainHandler.post {
                    webView?.let { crashed ->
                        (crashed.parent as? ViewGroup)?.removeView(crashed)
                        crashed.destroy()
                    }
                    webView = null
                    showHome("The game renderer stopped unexpectedly.")
                }
            }
            view.setBackgroundColor(Color.BLACK)
            webView = view
        }

        (player.parent as? ViewGroup)?.removeView(player)
        content.addView(
            player,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val currentWebView = webView
        if (isPlaying && currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
        } else if (isPlaying) {
            showHome()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::worker.isInitialized) worker.shutdownNow()
        webView?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun matchWidthWrapContent() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_GAME_FOLDER = 41
    }
}
