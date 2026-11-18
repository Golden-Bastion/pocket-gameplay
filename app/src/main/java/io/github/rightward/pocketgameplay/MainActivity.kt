package io.github.rightward.pocketgameplay

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var pathHandler: GamePathHandler
    private lateinit var worker: ExecutorService
    private lateinit var gamesDirectory: File
    private lateinit var zipImporter: ZipGameImporter
    private lateinit var libraryStore: GameLibraryStore

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var developerConsoleButton: Button? = null
    private var playerControlsButton: Button? = null
    private var playerControlsPanel: LinearLayout? = null
    private var controlsExpanded = false
    private var isImmersiveFullscreen = false
    private var fullscreenCustomView: View? = null
    private var fullscreenCustomViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenPreviousUiVisibility: Int? = null
    private var fullscreenPreviousWindowFlag = false
    private var immersivePreviousUiVisibility: Int? = null
    private var isPlaying = false
    private var scanGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        content = FrameLayout(this)
        setContentView(content)

        worker = Executors.newSingleThreadExecutor()
        gamesDirectory = File(filesDir, "games")
        zipImporter = ZipGameImporter(gamesDirectory)
        libraryStore = GameLibraryStore(File(filesDir, "library.properties"))
        cleanInterruptedImports()

        pathHandler = GamePathHandler(contentResolver)
        showHome()
    }

    private fun showHome(message: String? = null) {
        hideWebFullscreen(notifyPage = false)
        exitImmersiveFullscreen()
        isPlaying = false
        controlsExpanded = false
        developerConsoleButton = null
        playerControlsButton = null
        playerControlsPanel = null
        content.removeAllViews()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
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
            setPadding(0, dp(12), 0, dp(24))
        }, matchWidthWrapContent())

        page.addView(Button(this).apply {
            text = getString(R.string.choose_game_folder)
            isAllCaps = false
            setOnClickListener { chooseGameFolder() }
        }, matchWidthWrapContent())

        page.addView(Button(this).apply {
            text = getString(R.string.import_zip_game)
            isAllCaps = false
            setOnClickListener { chooseZipArchive() }
        }, matchWidthWrapContent())

        if (!message.isNullOrBlank()) {
            page.addView(TextView(this).apply {
                text = message
                textSize = 14f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, dp(4))
            }, matchWidthWrapContent())
        }

        page.addView(TextView(this).apply {
            text = getString(R.string.library_title)
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, dp(28), 0, dp(12))
        }, matchWidthWrapContent())

        addLibraryItems(page)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                page,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
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

    private fun addLibraryItems(page: LinearLayout) {
        val games = try {
            libraryStore.all().sortedBy { it.title.lowercase() }
        } catch (_: IOException) {
            page.addView(TextView(this).apply {
                text = getString(R.string.library_read_error)
                setTextColor(Color.LTGRAY)
                textSize = 14f
            }, matchWidthWrapContent())
            return
        }

        if (games.isEmpty()) {
            page.addView(TextView(this).apply {
                text = getString(R.string.library_empty)
                setTextColor(Color.LTGRAY)
                textSize = 14f
            }, matchWidthWrapContent())
            return
        }

        games.forEach { game ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(Color.rgb(31, 33, 38))
            }

            card.addView(TextView(this).apply {
                text = game.title
                textSize = 17f
                setTextColor(Color.WHITE)
            }, matchWidthWrapContent())

            card.addView(TextView(this).apply {
                text = game.entryPoint
                textSize = 12f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(4), 0, dp(8))
            }, matchWidthWrapContent())

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            actions.addView(Button(this).apply {
                text = getString(R.string.play_game)
                isAllCaps = false
                setOnClickListener { playLibraryGame(game) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            actions.addView(Button(this).apply {
                text = getString(R.string.remove_game)
                isAllCaps = false
                setOnClickListener { confirmRemoveGame(game) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))

            card.addView(actions, matchWidthWrapContent())
            page.addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            )
        }
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

    private fun chooseZipArchive() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
        }
        startActivityForResult(intent, REQUEST_GAME_ARCHIVE)
    }

    @Deprecated("Uses platform document pickers for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return

        val uri = data.data ?: return
        when (requestCode) {
            REQUEST_GAME_FOLDER -> acceptGameFolder(uri, data.flags)
            REQUEST_GAME_ARCHIVE -> importZipArchive(uri)
        }
    }

    private fun acceptGameFolder(treeUri: Uri, grantFlags: Int) {
        val readGrant = grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
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
        val originKey = GameOrigin.forFolderUri(treeUri.toString())
        showHome("Scanning for HTML and HTM files…")

        worker.execute {
            val root = DocumentFile.fromTreeUri(this, treeUri)
            val entries = root?.let(GameEntryPointScanner::scan).orEmpty()

            mainHandler.post {
                if (generation != scanGeneration || isFinishing || isDestroyed) return@post

                if (root == null) {
                    releaseFolderGrantIfUnused(treeUri)
                    showHome("Android could not open that folder.")
                } else if (entries.isEmpty()) {
                    releaseFolderGrantIfUnused(treeUri)
                    showHome("No .html or .htm files were found in that folder.")
                } else {
                    chooseFolderEntryPoint(root, entries, originKey, treeUri)
                }
            }
        }
    }

    private fun chooseFolderEntryPoint(
        root: DocumentFile,
        entries: List<HtmlEntryPoint>,
        originKey: String,
        treeUri: Uri
    ) {
        if (entries.size == 1) {
            saveFolderGame(root, entries.first(), treeUri, originKey)
            return
        }

        val labels = entries.map { it.relativePath }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose the game's start page")
            .setItems(labels) { _, selected ->
                saveFolderGame(root, entries[selected], treeUri, originKey)
            }
            .setNegativeButton("Cancel") { _, _ -> releaseFolderGrantIfUnused(treeUri) }
            .setOnCancelListener { releaseFolderGrantIfUnused(treeUri) }
            .show()
    }

    private fun saveFolderGame(
        root: DocumentFile,
        entryPoint: HtmlEntryPoint,
        treeUri: Uri,
        originKey: String
    ) {
        try {
            validateFolderEntryPoint(root, entryPoint.relativePath)
            val uriValue = treeUri.toString()
            val game = LibraryGame(
                id = FolderGameId.create(uriValue, entryPoint.relativePath),
                title = root.name?.takeIf { it.isNotBlank() } ?: "Folder game",
                directoryName = "",
                rootPath = "",
                entryPoint = entryPoint.relativePath,
                importedAtEpochMillis = System.currentTimeMillis(),
                storageType = LibraryStorageType.FOLDER,
                sourceUri = uriValue
            )
            libraryStore.add(game)
            openGame(root, entryPoint, originKey)
        } catch (exception: Exception) {
            releaseFolderGrantIfUnused(treeUri)
            showHome("Could not save this folder game: " + (exception.message ?: "Unknown error."))
        }
    }

    private fun importZipArchive(uri: Uri) {
        val archiveName = displayNameFor(uri)
        showHome("Importing " + archiveName + "…")

        worker.execute {
            var staged: StagedGame? = null
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("Android could not open the selected archive.")
                staged = zipImporter.extract(input, archiveName)

                val root = File(staged.stagingDirectory, staged.rootPath)
                val entries = GameEntryPointScanner.scan(DocumentFile.fromFile(root))
                if (entries.isEmpty()) {
                    throw IOException("The archive does not contain an HTML or HTM file.")
                }

                val completedStage = staged
                mainHandler.post {
                    if (isFinishing || isDestroyed) {
                        completedStage.cleanup()
                    } else {
                        chooseZipEntryPoint(completedStage, entries)
                    }
                }
            } catch (exception: Exception) {
                staged?.cleanup()
                val reason = exception.message ?: "Unknown import error."
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) showHome("Import failed: " + reason)
                }
            }
        }
    }

    private fun displayNameFor(uri: Uri): String {
        return try {
            val cursor: Cursor? = contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty().ifBlank { "Imported Game.zip" }
        } catch (_: Exception) {
            uri.lastPathSegment.orEmpty().ifBlank { "Imported Game.zip" }
        }
    }

    private fun chooseZipEntryPoint(staged: StagedGame, entries: List<HtmlEntryPoint>) {
        if (entries.size == 1) {
            finishZipImport(staged, entries.first())
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose the game's start page")
            .setItems(entries.map { it.relativePath }.toTypedArray()) { _, selected ->
                finishZipImport(staged, entries[selected])
            }
            .setNegativeButton("Cancel") { _, _ -> staged.cleanup() }
            .create()

        dialog.setOnCancelListener { staged.cleanup() }
        dialog.show()
    }

    private fun finishZipImport(staged: StagedGame, entryPoint: HtmlEntryPoint) {
        var publishedDirectory: File? = null
        try {
            publishedDirectory = staged.publish(gamesDirectory)
            val game = LibraryGame(
                id = staged.id,
                title = staged.displayName,
                directoryName = staged.id,
                rootPath = staged.rootPath,
                entryPoint = entryPoint.relativePath,
                importedAtEpochMillis = System.currentTimeMillis()
            )
            libraryStore.add(game)

            val root = resolveGameRoot(game)
            openGame(DocumentFile.fromFile(root), HtmlEntryPoint(game.entryPoint), game.id)
        } catch (exception: Exception) {
            runCatching { libraryStore.remove(staged.id) }
            publishedDirectory?.deleteRecursively()
            staged.cleanup()
            showHome("Could not add the game to your library: " + (exception.message ?: "Unknown error."))
        }
    }

    private fun playLibraryGame(game: LibraryGame) {
        try {
            when (game.storageType) {
                LibraryStorageType.IMPORTED -> {
                    val root = resolveGameRoot(game)
                    openGame(DocumentFile.fromFile(root), HtmlEntryPoint(game.entryPoint), game.id)
                }
                LibraryStorageType.FOLDER -> {
                    val treeUri = Uri.parse(game.sourceUri)
                    val hasReadGrant = contentResolver.persistedUriPermissions.any {
                        it.uri == treeUri && it.isReadPermission
                    }
                    if (!hasReadGrant) {
                        throw IOException("Android no longer grants access to this folder. Remove it and open the folder again.")
                    }

                    val root = DocumentFile.fromTreeUri(this, treeUri)
                        ?: throw IOException("The selected folder is no longer available.")
                    validateFolderEntryPoint(root, game.entryPoint)
                    openGame(root, HtmlEntryPoint(game.entryPoint), GameOrigin.forFolderUri(game.sourceUri))
                }
                else -> throw IOException("This library item has an unsupported storage type.")
            }
        } catch (exception: IOException) {
            showHome(exception.message ?: "This game is unavailable. Remove it and add it again.")
        }
    }

    private fun validateFolderEntryPoint(root: DocumentFile, relativePath: String) {
        val segments = RelativeGamePath.parse(relativePath)
            ?: throw IOException("The saved HTML entry-point path is invalid.")
        var document = root

        for ((index, segment) in segments.withIndex()) {
            document = document.findFile(segment)
                ?: throw IOException("The saved HTML entry point could not be found.")
            if (index < segments.lastIndex && !document.isDirectory) {
                throw IOException("The saved HTML entry-point path is invalid.")
            }
        }

        if (!document.isFile || !document.canRead()) {
            throw IOException("The saved HTML entry point is unavailable.")
        }
    }

    private fun resolveGameRoot(game: LibraryGame): File {
        if (game.directoryName.isBlank() ||
            game.directoryName == "." ||
            game.directoryName == ".." ||
            File(game.directoryName).name != game.directoryName ||
            game.directoryName != game.id
        ) {
            throw IOException("Invalid library storage directory.")
        }

        val base = gamesDirectory.canonicalFile
        val container = File(base, game.directoryName).canonicalFile
        if (!container.path.startsWith(base.path + File.separator) || !container.isDirectory) {
            throw IOException("The imported game directory is unavailable.")
        }

        val root = if (game.rootPath.isBlank()) {
            container
        } else {
            val segments = RelativeGamePath.parse(game.rootPath)
                ?: throw IOException("Invalid game root path.")
            segments.fold(container) { current, segment -> File(current, segment) }.canonicalFile
        }

        if ((root != container && !root.path.startsWith(container.path + File.separator)) ||
            !root.isDirectory
        ) {
            throw IOException("The imported game root is unavailable.")
        }

        val entrySegments = RelativeGamePath.parse(game.entryPoint)
            ?: throw IOException("Invalid game entry-point path.")
        val entryFile = entrySegments.fold(root) { current, segment -> File(current, segment) }.canonicalFile
        if (!entryFile.path.startsWith(root.path + File.separator) || !entryFile.isFile) {
            throw IOException("The saved game entry point is unavailable.")
        }

        return root
    }

    private fun confirmRemoveGame(game: LibraryGame) {
        AlertDialog.Builder(this)
            .setTitle("Remove " + game.title + "?")
            .setMessage(
                if (game.storageType == LibraryStorageType.FOLDER) {
                    "This removes the library shortcut only. Your original folder and files will not be deleted."
                } else {
                    "This deletes the imported copy stored by Pocket Gameplay."
                }
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ -> removeLibraryGame(game) }
            .show()
    }

    private fun removeLibraryGame(game: LibraryGame) {
        try {
            when (game.storageType) {
                LibraryStorageType.FOLDER -> removeFolderShortcut(game)
                LibraryStorageType.IMPORTED -> removeImportedGame(game)
                else -> throw IOException("Unsupported library item type.")
            }
        } catch (exception: IOException) {
            showHome("Could not remove this game: " + (exception.message ?: "Invalid storage path."))
        }
    }

    private fun removeFolderShortcut(game: LibraryGame) {
        if (game.sourceUri.isBlank()) {
            throw IOException("The saved folder reference is missing.")
        }

        libraryStore.remove(game.id)
        val uriStillUsed = libraryStore.all().any {
            it.storageType == LibraryStorageType.FOLDER && it.sourceUri == game.sourceUri
        }

        if (!uriStillUsed) releaseFolderGrantIfUnused(Uri.parse(game.sourceUri))
        showHome()
    }

    private fun releaseFolderGrantIfUnused(treeUri: Uri) {
        val stillUsed = runCatching { libraryStore.all() }.getOrNull()?.any {
            it.storageType == LibraryStorageType.FOLDER && it.sourceUri == treeUri.toString()
        } ?: true

        if (!stillUsed) {
            try {
                contentResolver.releasePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // The permission may already have been revoked by Android or the user.
            }
        }
    }

    private fun removeImportedGame(game: LibraryGame) {
        if (game.directoryName != game.id ||
            game.directoryName.isBlank() ||
            File(game.directoryName).name != game.directoryName
        ) {
            throw IOException("Invalid game storage identifier.")
        }

        val base = gamesDirectory.canonicalFile
        val directory = File(base, game.directoryName).canonicalFile
        if (!directory.path.startsWith(base.path + File.separator)) {
            throw IOException("Invalid game storage path.")
        }

        libraryStore.remove(game.id)
        if (directory.exists() && !directory.deleteRecursively()) {
            showHome("The library item was removed, but some stored files could not be deleted.")
        } else {
            showHome()
        }
    }

    private fun cleanInterruptedImports() {
        val storedIds = try {
            libraryStore.all()
                .filter { it.storageType == LibraryStorageType.IMPORTED }
                .map { it.directoryName }
                .toSet()
        } catch (_: IOException) {
            null
        }

        gamesDirectory.listFiles()?.forEach { directory ->
            if (directory.name.startsWith(".staging-")) {
                directory.deleteRecursively()
            } else if (storedIds != null && directory.name !in storedIds) {
                directory.deleteRecursively()
            }
        }
    }

    private fun openGame(root: DocumentFile, entryPoint: HtmlEntryPoint, originKey: String) {
        webView?.stopLoading()
        pathHandler.setGameRoot(root)

        val assetHost = GameOrigin.domainFor(originKey)
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(assetHost)
            .addPathHandler("/game/", pathHandler)
            .addPathHandler(
                DeveloperConsole.ASSET_PATH_PREFIX,
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()
        showPlayer(assetLoader, assetHost)

        val encodedPath = entryPoint.relativePath
            .split('/')
            .joinToString("/") { Uri.encode(it) }
        webView?.loadUrl("https://" + assetHost + OfflineGameWebViewClient.GAME_PATH_PREFIX + encodedPath)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showPlayer(assetLoader: WebViewAssetLoader, assetHost: String) {
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
            view.webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(
                    view: View,
                    callback: WebChromeClient.CustomViewCallback
                ) {
                    showWebFullscreen(view, callback)
                }

                override fun onHideCustomView() {
                    hideWebFullscreen(notifyPage = false)
                }
            }
            view.setBackgroundColor(Color.BLACK)
            webView = view
        }

        player.webViewClient = OfflineGameWebViewClient(
            assetLoader = assetLoader,
            gamePathHandler = pathHandler,
            assetHost = assetHost,
            onPageStarted = {
                mainHandler.post {
                    if (webView === player) developerConsoleButton?.isEnabled = false
                }
            },
            onPageFinished = {
                mainHandler.post {
                    if (webView === player) developerConsoleButton?.isEnabled = true
                }
            },
            onRendererGone = {
                mainHandler.post {
                    webView?.let { crashed ->
                        (crashed.parent as? ViewGroup)?.removeView(crashed)
                        crashed.destroy()
                    }
                    webView = null
                    showHome("The game renderer stopped unexpectedly.")
                }
            }
        )

        (player.parent as? ViewGroup)?.removeView(player)
        content.addView(
            player,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        createPlayerControls(player)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when (
            PlayerBackResolver.resolve(
                PlayerBackState(
                    controlsExpanded = controlsExpanded,
                    webFullscreen = fullscreenCustomView != null,
                    immersiveFullscreen = isImmersiveFullscreen,
                    canNavigateBack = webView?.canGoBack() == true,
                    isPlaying = isPlaying
                )
            )
        ) {
            PlayerBackAction.CLOSE_CONTROLS -> setPlayerControlsExpanded(false)
            PlayerBackAction.EXIT_WEB_FULLSCREEN -> hideWebFullscreen(notifyPage = true)
            PlayerBackAction.EXIT_IMMERSIVE_FULLSCREEN -> exitImmersiveFullscreen()
            PlayerBackAction.NAVIGATE_GAME_HISTORY -> webView?.goBack()
            PlayerBackAction.RETURN_TO_LIBRARY -> returnToLibrary()
            PlayerBackAction.EXIT_ACTIVITY -> super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        content.requestLayout()
        webView?.apply {
            requestLayout()
            invalidate()
        }
        if (isImmersiveFullscreen || fullscreenCustomView != null) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = immersiveSystemUiFlags()
        }
    }

    override fun onDestroy() {
        hideWebFullscreen(notifyPage = false)
        exitImmersiveFullscreen()
        if (::worker.isInitialized) worker.shutdownNow()
        webView?.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        webView = null
        developerConsoleButton = null
        playerControlsButton = null
        playerControlsPanel = null
        super.onDestroy()
    }

    private fun createPlayerControls(player: WebView) {
        val controlsButton = Button(this).apply {
            text = getString(R.string.player_controls)
            contentDescription = getString(R.string.player_controls_description)
            isAllCaps = false
            elevation = dp(8).toFloat()
            setOnClickListener { setPlayerControlsExpanded(!controlsExpanded) }
        }
        playerControlsButton = controlsButton
        content.addView(
            controlsButton,
            FrameLayout.LayoutParams(dp(112), dp(46), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(12)
                marginStart = dp(12)
            }
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.argb(238, 24, 26, 31))
            elevation = dp(12).toFloat()
            visibility = View.GONE
        }

        fun addAction(label: Int, action: () -> Unit): Button {
            val button = Button(this).apply {
                text = getString(label)
                isAllCaps = false
                textSize = 13f
                minHeight = dp(44)
                setOnClickListener { action() }
            }
            panel.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(46)
                ).apply {
                    bottomMargin = dp(4)
                }
            )
            return button
        }

        addAction(R.string.return_to_library) { returnToLibrary() }
        addAction(R.string.reload_game) {
            setPlayerControlsExpanded(false)
            developerConsoleButton?.isEnabled = false
            player.reload()
        }
        addAction(R.string.toggle_fullscreen) { enterImmersiveFullscreen() }

        developerConsoleButton = addAction(R.string.developer_console) {
            player.evaluateJavascript(DeveloperConsole.TOGGLE_SCRIPT, null)
        }.apply {
            contentDescription = getString(R.string.developer_console_description)
            isEnabled = false
        }

        addAction(R.string.close_controls) { setPlayerControlsExpanded(false) }

        playerControlsPanel = panel
        content.addView(
            panel,
            FrameLayout.LayoutParams(dp(164), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = dp(64)
                marginStart = dp(12)
            }
        )
    }

    private fun setPlayerControlsExpanded(expanded: Boolean) {
        controlsExpanded = expanded
        playerControlsPanel?.visibility = if (expanded) View.VISIBLE else View.GONE
        playerControlsButton?.text = if (expanded) {
            getString(R.string.player_controls) + " ▲"
        } else {
            getString(R.string.player_controls) + " ▼"
        }
    }

    private fun returnToLibrary() {
        hideWebFullscreen(notifyPage = true)
        exitImmersiveFullscreen()
        webView?.stopLoading()
        showHome()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveFullscreen() {
        if (isImmersiveFullscreen || fullscreenCustomView != null) return
        setPlayerControlsExpanded(false)
        immersivePreviousUiVisibility = window.decorView.systemUiVisibility
        isImmersiveFullscreen = true
        playerControlsButton?.visibility = View.GONE
        window.decorView.systemUiVisibility = immersiveSystemUiFlags()
    }

    @Suppress("DEPRECATION")
    private fun exitImmersiveFullscreen() {
        if (!isImmersiveFullscreen) return
        isImmersiveFullscreen = false
        immersivePreviousUiVisibility?.let {
            window.decorView.systemUiVisibility = it
        }
        immersivePreviousUiVisibility = null
        playerControlsButton?.visibility = if (isPlaying && fullscreenCustomView == null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    @Suppress("DEPRECATION")
    private fun showWebFullscreen(
        view: View,
        callback: WebChromeClient.CustomViewCallback
    ) {
        if (fullscreenCustomView != null) {
            callback.onCustomViewHidden()
            return
        }

        setPlayerControlsExpanded(false)
        playerControlsButton?.visibility = View.GONE

        fullscreenPreviousUiVisibility = window.decorView.systemUiVisibility
        fullscreenPreviousWindowFlag =
            window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0
        fullscreenCustomView = view
        fullscreenCustomViewCallback = callback

        (view.parent as? ViewGroup)?.removeView(view)
        content.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = immersiveSystemUiFlags()
    }

    @Suppress("DEPRECATION")
    private fun hideWebFullscreen(notifyPage: Boolean) {
        val view = fullscreenCustomView ?: return
        val callback = fullscreenCustomViewCallback
        fullscreenCustomView = null
        fullscreenCustomViewCallback = null
        (view.parent as? ViewGroup)?.removeView(view)

        if (fullscreenPreviousWindowFlag) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        fullscreenPreviousWindowFlag = false
        fullscreenPreviousUiVisibility?.let {
            window.decorView.systemUiVisibility = it
        }
        fullscreenPreviousUiVisibility = null

        playerControlsButton?.visibility = if (isPlaying && !isImmersiveFullscreen) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (notifyPage) callback?.onCustomViewHidden()
    }

    @Suppress("DEPRECATION")
    private fun immersiveSystemUiFlags(): Int {
        return View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    private fun matchWidthWrapContent() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_GAME_FOLDER = 41
        private const val REQUEST_GAME_ARCHIVE = 42
    }
}
