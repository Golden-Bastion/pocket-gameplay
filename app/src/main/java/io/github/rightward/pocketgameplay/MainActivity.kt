package io.github.rightward.pocketgameplay

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.database.Cursor
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private data class PendingGameDownload(
    val sourceUri: Uri,
    val fileName: String
)

class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var pathHandler: GamePathHandler
    private lateinit var worker: ExecutorService
    private lateinit var gamesDirectory: File
    private lateinit var zipImporter: ZipGameImporter
    private lateinit var libraryStore: GameLibraryStore

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var currentWebViewProfile: String? = null
    private var currentWebViewGameId: String? = null
    private var librarySortOrder = LibrarySortOrder.NAME
    private var pendingFolderReconnect: LibraryGame? = null
    private var pendingZipReplacement: LibraryGame? = null
    private var developerConsoleButton: Button? = null
    private var developerConsolePoll: Runnable? = null
    private var developerConsolePageReady = false
    private var playerControlsButton: Button? = null
    private var playerControlsPanel: LinearLayout? = null
    private var controlsExpanded = false
    private var isImmersiveFullscreen = false
    private var fullscreenCustomView: View? = null
    private var fullscreenCustomViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenPreviousUiVisibility: Int? = null
    private var fullscreenPreviousWindowFlag = false
    private var immersivePreviousUiVisibility: Int? = null
    private var platformBackCallback: Any? = null
    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null
    private var pendingDownload: PendingGameDownload? = null
    private var currentGameAssetHost: String? = null
    private var isPlaying = false
    private var scanGeneration = 0
    private val libraryExitConfirmation = LibraryExitConfirmation()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        content = FrameLayout(this)
        setContentView(content)
        content.setOnApplyWindowInsetsListener { _, insets ->
            val safeInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
            } else {
                null
            }
            val leftInset = safeInsets?.left ?: insets.systemWindowInsetLeft
            val topInset = safeInsets?.top ?: insets.systemWindowInsetTop
            val rightInset = safeInsets?.right ?: insets.systemWindowInsetRight
            updatePlayerControlInsets(leftInset, topInset, rightInset)
            insets
        }

        worker = Executors.newSingleThreadExecutor()
        gamesDirectory = File(filesDir, "games")
        zipImporter = ZipGameImporter(gamesDirectory)
        libraryStore = GameLibraryStore(File(filesDir, "library.properties"))
        cleanInterruptedImports()

        pathHandler = GamePathHandler(contentResolver)
        registerPlatformBackCallback()
        showHome()
    }

    private fun showHome(message: String? = null) {
        cancelDeveloperConsolePolling()
        developerConsolePageReady = false
        libraryExitConfirmation.reset()
        cancelPendingFileChooser()
        currentGameAssetHost = null
        hideWebFullscreen(notifyPage = false)
        exitImmersiveFullscreen()
        destroyWebView()
        isPlaying = false
        controlsExpanded = false
        developerConsoleButton = null
        playerControlsButton = null
        playerControlsPanel = null
        content.removeAllViews()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(20), dp(28), dp(20), dp(32))
            setBackgroundColor(getColor(R.color.pg_background))
        }

        val brandHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandMark = ImageView(this).apply {
            setImageResource(R.drawable.ic_pocket_gameplay_mark)
            contentDescription = getString(R.string.app_name)
        }
        brandHeader.addView(brandMark, LinearLayout.LayoutParams(dp(56), dp(56)).apply {
            marginEnd = dp(14)
        })
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        brandText.addView(TextView(this).apply {
            text = getString(R.string.home_eyebrow)
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.pg_accent))
        }, matchWidthWrapContent())
        brandText.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 27f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.pg_text_primary))
            includeFontPadding = false
        }, matchWidthWrapContent())
        brandHeader.addView(brandText, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        page.addView(brandHeader, matchWidthWrapContent())

        page.addView(TextView(this).apply {
            text = getString(R.string.home_tagline)
            textSize = 15f
            setTextColor(getColor(R.color.pg_text_secondary))
            setPadding(0, dp(14), 0, dp(22))
        }, matchWidthWrapContent())

        val openFolderButton = Button(
            this, null, 0, R.style.Widget_PocketGameplay_Button_Primary
        ).apply {
            text = getString(R.string.choose_game_folder)
            setOnClickListener { chooseGameFolder() }
        }
        page.addView(openFolderButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ).apply {
            bottomMargin = dp(10)
        })

        val importZipButton = Button(this).apply {
            text = getString(R.string.import_zip_game)
            setOnClickListener { chooseZipArchive() }
        }
        page.addView(importZipButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)
        ))

        if (!message.isNullOrBlank()) {
            page.addView(TextView(this).apply {
                text = message
                textSize = 14f
                setTextColor(getColor(R.color.pg_text_secondary))
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundResource(R.drawable.bg_message_note)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            })
        }

        page.addView(TextView(this).apply {
            text = getString(R.string.library_title)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.pg_text_primary))
            setPadding(0, dp(30), 0, dp(12))
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
            libraryStore.all()
        } catch (_: IOException) {
            page.addView(TextView(this).apply {
                text = getString(R.string.library_read_error)
                setTextColor(getColor(R.color.pg_text_secondary))
                textSize = 14f
            }, matchWidthWrapContent())
            return
        }

        if (games.isEmpty()) {
            page.addView(TextView(this).apply {
                text = getString(R.string.library_empty)
                setTextColor(getColor(R.color.pg_text_secondary))
                textSize = 14f
                setPadding(dp(16), dp(16), dp(16), dp(16))
                setBackgroundResource(R.drawable.bg_library_card)
            }, matchWidthWrapContent())
            return
        }

        val search = EditText(this).apply {
            hint = getString(R.string.library_search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setTextColor(getColor(R.color.pg_text_primary))
            setHintTextColor(getColor(R.color.pg_text_secondary))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(R.drawable.bg_input)
        }
        page.addView(search, matchWidthWrapContent())

        var renderGames: () -> Unit = {}
        val sortButton = Button(this).apply {
            isAllCaps = false
            text = if (librarySortOrder == LibrarySortOrder.NAME) "Sort: A–Z" else "Sort: Recently added"
            setOnClickListener {
                librarySortOrder = if (librarySortOrder == LibrarySortOrder.NAME) {
                    LibrarySortOrder.RECENTLY_ADDED
                } else {
                    LibrarySortOrder.NAME
                }
                text = if (librarySortOrder == LibrarySortOrder.NAME) {
                    "Sort: A–Z"
                } else {
                    "Sort: Recently added"
                }
                renderGames()
            }
        }
        page.addView(sortButton, matchWidthWrapContent())

        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        page.addView(results, matchWidthWrapContent())

        fun addAction(row: LinearLayout, label: String, action: () -> Unit) {
            row.addView(Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 12f
                minWidth = 0
                minHeight = dp(46)
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginEnd = dp(3)
            })
        }

        renderGames = render@{
            results.removeAllViews()
            val filtered = LibraryGameCatalog.filterAndSort(
                games,
                search.text?.toString().orEmpty(),
                librarySortOrder
            )
            if (filtered.isEmpty()) {
                results.addView(TextView(this).apply {
                    text = "No games match your search."
                    setTextColor(getColor(R.color.pg_text_secondary))
                    textSize = 14f
                    setPadding(0, dp(12), 0, dp(12))
                }, matchWidthWrapContent())
                return@render
            }

            filtered.forEach { game ->
                val problem = libraryGameProblem(game)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    setBackgroundResource(R.drawable.bg_library_card)
                }

                card.addView(TextView(this).apply {
                    text = game.title
                    textSize = 17f
                    setTextColor(getColor(R.color.pg_text_primary))
                }, matchWidthWrapContent())

                val storageLabel = if (game.storageType == LibraryStorageType.FOLDER) {
                    "Folder shortcut"
                } else {
                    "Imported ZIP copy"
                }
                card.addView(TextView(this).apply {
                    text = storageLabel + " · Start page: " + game.entryPoint
                    textSize = 12f
                    setTextColor(getColor(R.color.pg_text_secondary))
                    setPadding(0, dp(4), 0, dp(6))
                }, matchWidthWrapContent())

                if (problem != null) {
                    card.addView(TextView(this).apply {
                        text = problem
                        textSize = 12f
                        setTextColor(getColor(R.color.pg_warning))
                        setPadding(0, 0, 0, dp(8))
                    }, matchWidthWrapContent())
                }

                val primaryActions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                if (problem == null) {
                    addAction(primaryActions, "Play") { playLibraryGame(game) }
                } else if (game.storageType == LibraryStorageType.FOLDER) {
                    addAction(primaryActions, "Reconnect") { requestFolderReconnect(game) }
                }
                if (LibraryGameCatalog.supportsZipReplacement(game)) {
                    addAction(primaryActions, "Update ZIP") { requestZipReplacement(game) }
                }
                addAction(primaryActions, "Rename") { renameLibraryGame(game) }
                addAction(primaryActions, "Details") { showGameDetails(game) }
                card.addView(primaryActions, matchWidthWrapContent())

                val secondaryActions = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                val dataActionLabel = when {
                    game.browserProfile.isBlank() && supportsPerGameProfiles() -> "Isolate saves"
                    game.browserProfile.isNotBlank() && supportsPerGameProfiles() -> "Clear data"
                    else -> "Data reset unavailable"
                }
                addAction(secondaryActions, dataActionLabel) {
                    when {
                        !supportsPerGameProfiles() -> isolateGameSaves(game)
                        game.browserProfile.isBlank() -> isolateGameSaves(game)
                        else -> confirmClearGameData(game)
                    }
                }
                addAction(secondaryActions, "Remove") { confirmRemoveGame(game) }
                card.addView(secondaryActions, matchWidthWrapContent())

                results.addView(
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

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                renderGames()
            }
            override fun afterTextChanged(editable: Editable?) = Unit
        })
        renderGames()
    }

    private fun hasFolderReadGrant(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    private fun libraryGameProblem(game: LibraryGame): String? {
        return try {
            when (game.storageType) {
                LibraryStorageType.IMPORTED -> resolveGameRoot(game)
                LibraryStorageType.FOLDER -> {
                    if (!hasFolderReadGrant(Uri.parse(game.sourceUri))) {
                        return "Folder access was revoked. Reconnect this folder to play."
                    }
                }
                else -> throw IOException("Unsupported game storage type.")
            }
            null
        } catch (_: Exception) {
            if (game.storageType == LibraryStorageType.FOLDER) {
                "The folder or saved start page is unavailable. Reconnect to choose it again."
            } else {
                "The imported files are missing or incomplete. Replace the ZIP archive."
            }
        }
    }

    private fun renameLibraryGame(game: LibraryGame) {
        val titleField = EditText(this).apply {
            setText(game.title)
            setSelection(text.length)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("Rename game")
            .setView(titleField)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                try {
                    if (!libraryStore.rename(game.id, titleField.text.toString())) {
                        throw IOException("This library entry no longer exists.")
                    }
                    showHome()
                } catch (exception: IOException) {
                    showHome("Could not rename this game: " + (exception.message ?: "Invalid title."))
                }
            }
            .show()
    }

    private fun showGameDetails(game: LibraryGame) {
        val storageDescription = if (game.storageType == LibraryStorageType.FOLDER) {
            "Selected folder shortcut (original files remain in place)"
        } else {
            "Imported ZIP copy (stored inside Pocket Gameplay)"
        }
        val dataDescription = when {
            game.browserProfile.isNotBlank() && supportsPerGameProfiles() -> "Dedicated browser profile"
            game.browserProfile.isNotBlank() -> "Dedicated profile saved; current WebView cannot access it"
            supportsPerGameProfiles() -> "Legacy shared profile; can be isolated"
            else -> "Legacy shared profile; this WebView cannot isolate it"
        }
        AlertDialog.Builder(this)
            .setTitle(game.title)
            .setMessage(
                "Storage: $storageDescription\n\n" +
                    "Start page: " + game.entryPoint + "\n\n" +
                    "Save data: $dataDescription"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun requestFolderReconnect(game: LibraryGame) {
        pendingFolderReconnect = game
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        try {
            startActivityForResult(intent, REQUEST_FOLDER_RECONNECT)
        } catch (_: Exception) {
            pendingFolderReconnect = null
            showHome("No folder picker is available.")
        }
    }

    private fun reconnectFolderGame(game: LibraryGame, treeUri: Uri, grantFlags: Int) {
        val readGrant = grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (readGrant == 0) {
            showHome("Android did not grant read access to that folder.")
            return
        }
        try {
            contentResolver.takePersistableUriPermission(treeUri, readGrant)
        } catch (exception: Exception) {
            releaseFolderGrantIfUnused(treeUri)
            showHome("Could not retain access to that folder: " + (exception.message ?: "Please select it again."))
            return
        }

        worker.execute {
            try {
                val root = DocumentFile.fromTreeUri(this, treeUri)
                    ?: throw IOException("Android could not open the selected folder.")
                val existingEntryPoint = runCatching {
                    validateFolderEntryPoint(root, game.entryPoint)
                    game.entryPoint
                }.getOrNull()
                val alternatives = if (existingEntryPoint == null) {
                    GameEntryPointScanner.scan(root)
                } else {
                    emptyList()
                }

                mainHandler.post {
                    if (isFinishing || isDestroyed) {
                        releaseFolderGrantIfUnused(treeUri)
                    } else if (existingEntryPoint != null) {
                        finishFolderReconnect(game, treeUri, existingEntryPoint)
                    } else if (alternatives.isEmpty()) {
                        releaseFolderGrantIfUnused(treeUri)
                        showHome("No HTML or HTM start pages were found in that folder.")
                    } else if (alternatives.size == 1) {
                        finishFolderReconnect(game, treeUri, alternatives.first().relativePath)
                    } else {
                        chooseReconnectedFolderEntryPoint(game, treeUri, alternatives)
                    }
                }
            } catch (exception: Exception) {
                mainHandler.post {
                    releaseFolderGrantIfUnused(treeUri)
                    if (!isFinishing && !isDestroyed) {
                        showHome(exception.message ?: "Android could not open the selected folder.")
                    }
                }
            }
        }
    }

    private fun chooseReconnectedFolderEntryPoint(
        game: LibraryGame,
        treeUri: Uri,
        entries: List<HtmlEntryPoint>
    ) {
        AlertDialog.Builder(this)
            .setTitle("Choose the replacement start page")
            .setItems(entries.map { it.relativePath }.toTypedArray()) { _, selected ->
                finishFolderReconnect(game, treeUri, entries[selected].relativePath)
            }
            .setNegativeButton("Cancel") { _, _ ->
                releaseFolderGrantIfUnused(treeUri)
                showHome()
            }
            .setOnCancelListener {
                releaseFolderGrantIfUnused(treeUri)
            }
            .show()
    }

    private fun finishFolderReconnect(game: LibraryGame, treeUri: Uri, entryPoint: String) {
        try {
            val originalUri = Uri.parse(game.sourceUri)
            val retainedOrigin = game.originKey.ifBlank {
                GameOrigin.forFolderUri(game.sourceUri)
            }
            val updated = game.copy(
                sourceUri = treeUri.toString(),
                entryPoint = entryPoint,
                originKey = retainedOrigin
            )
            if (!libraryStore.update(updated)) {
                throw IOException("The library entry no longer exists.")
            }
            if (originalUri != treeUri) releaseFolderGrantIfUnused(originalUri)
            showHome("Folder access restored. This game's saved browser data was retained.")
        } catch (exception: Exception) {
            releaseFolderGrantIfUnused(treeUri)
            showHome("Could not restore folder access: " + (exception.message ?: "Unknown error."))
        }
    }

    private fun requestZipReplacement(game: LibraryGame) {
        pendingZipReplacement = game
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
        }
        try {
            startActivityForResult(intent, REQUEST_ZIP_REPLACEMENT)
        } catch (_: Exception) {
            pendingZipReplacement = null
            showHome("No file picker is available.")
        }
    }

    private fun replaceImportedGameArchive(game: LibraryGame, uri: Uri) {
        if (game.storageType != LibraryStorageType.IMPORTED) {
            showHome("Only imported ZIP games can be replaced this way.")
            return
        }
        val archiveName = displayNameFor(uri)
        showHome("Importing replacement for " + game.title + "…")

        worker.execute {
            var staged: StagedGame? = null
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("Android could not open the selected archive.")
                staged = zipImporter.extract(input, archiveName)
                val root = File(staged.stagingDirectory, staged.rootPath)
                val entries = GameEntryPointScanner.scan(DocumentFile.fromFile(root))
                if (entries.isEmpty()) {
                    throw IOException("The replacement archive does not contain an HTML or HTM file.")
                }

                val completedStage = staged
                mainHandler.post {
                    if (isFinishing || isDestroyed) {
                        completedStage.cleanup()
                    } else {
                        chooseZipReplacementEntryPoint(game, completedStage, entries)
                    }
                }
            } catch (exception: Exception) {
                staged?.cleanup()
                val reason = exception.message ?: "Unknown import error."
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) {
                        showHome("Replacement failed: " + reason)
                    }
                }
            }
        }
    }

    private fun chooseZipReplacementEntryPoint(
        game: LibraryGame,
        staged: StagedGame,
        entries: List<HtmlEntryPoint>
    ) {
        if (entries.size == 1) {
            finishZipReplacement(game, staged, entries.first())
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose the replacement game's start page")
            .setItems(entries.map { it.relativePath }.toTypedArray()) { _, selected ->
                finishZipReplacement(game, staged, entries[selected])
            }
            .setNegativeButton("Cancel") { _, _ -> staged.cleanup() }
            .create()
        dialog.setOnCancelListener { staged.cleanup() }
        dialog.show()
    }

    private fun finishZipReplacement(
        game: LibraryGame,
        staged: StagedGame,
        entryPoint: HtmlEntryPoint
    ) {
        val base = gamesDirectory.canonicalFile
        val destination = File(base, game.directoryName).canonicalFile
        if (!destination.path.startsWith(base.path + File.separator) ||
            game.directoryName != game.id
        ) {
            staged.cleanup()
            showHome("The imported game's storage identifier is invalid.")
            return
        }

        val backup = File(base, ".backup-" + game.id + "-" + System.nanoTime())
        var movedOriginal = false
        var publishedReplacement = false
        try {
            if (currentWebViewGameId == game.id) destroyWebView()
            if (destination.exists()) {
                if (!destination.renameTo(backup)) {
                    throw IOException("Could not protect the previous imported files before replacement.")
                }
                movedOriginal = true
            }

            staged.publish(gamesDirectory, game.directoryName)
            publishedReplacement = true
            val updated = game.copy(
                rootPath = staged.rootPath,
                entryPoint = entryPoint.relativePath
            )
            if (!libraryStore.update(updated)) {
                throw IOException("The library entry no longer exists.")
            }

            if (movedOriginal) backup.deleteRecursively()
            showHome("Game files replaced. The library identity and isolated save data were retained.")
        } catch (exception: Exception) {
            val newDirectory = File(base, game.directoryName)
            if (publishedReplacement && newDirectory.exists()) newDirectory.deleteRecursively()
            val restored = !movedOriginal || !backup.exists() || backup.renameTo(destination)
            staged.cleanup()
            val detail = if (restored) {
                exception.message ?: "Unknown replacement error."
            } else {
                "Replacement failed and the previous files remain in " + backup.name + "."
            }
            showHome("Could not replace the game: " + detail)
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
        when (requestCode) {
            REQUEST_GAME_FILE_PICKER -> {
                finishGameFileChooser(resultCode, data)
                return
            }
            REQUEST_GAME_DOWNLOAD -> {
                finishGameDownload(resultCode, data)
                return
            }
            REQUEST_FOLDER_RECONNECT -> {
                val game = pendingFolderReconnect
                pendingFolderReconnect = null
                if (resultCode == RESULT_OK && game != null && data?.data != null) {
                    reconnectFolderGame(game, data.data!!, data.flags)
                }
                return
            }
            REQUEST_ZIP_REPLACEMENT -> {
                val game = pendingZipReplacement
                pendingZipReplacement = null
                if (resultCode == RESULT_OK && game != null && data?.data != null) {
                    replaceImportedGameArchive(game, data.data!!)
                }
                return
            }
        }

        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
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
            val existingGame = libraryStore.all().firstOrNull {
                it.storageType == LibraryStorageType.FOLDER &&
                    it.sourceUri == uriValue &&
                    it.entryPoint == entryPoint.relativePath
            }
            val game = existingGame ?: LibraryGame(
                id = FolderGameId.create(uriValue, entryPoint.relativePath),
                title = root.name?.takeIf { it.isNotBlank() } ?: "Folder game",
                directoryName = "",
                rootPath = "",
                entryPoint = entryPoint.relativePath,
                importedAtEpochMillis = System.currentTimeMillis(),
                storageType = LibraryStorageType.FOLDER,
                sourceUri = uriValue,
                originKey = originKey,
                browserProfile = if (supportsPerGameProfiles()) {
                    GameBrowserProfile.forGameId(FolderGameId.create(uriValue, entryPoint.relativePath))
                } else {
                    ""
                }
            )
            if (existingGame == null) libraryStore.add(game)
            openGame(root, entryPoint, game)
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
                importedAtEpochMillis = System.currentTimeMillis(),
                originKey = staged.id,
                browserProfile = if (supportsPerGameProfiles()) {
                    GameBrowserProfile.forGameId(staged.id)
                } else {
                    ""
                }
            )
            libraryStore.add(game)

            val root = resolveGameRoot(game)
            openGame(DocumentFile.fromFile(root), HtmlEntryPoint(game.entryPoint), game)
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
                    openGame(DocumentFile.fromFile(root), HtmlEntryPoint(game.entryPoint), game)
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
                    openGame(root, HtmlEntryPoint(game.entryPoint), game)
                }
                else -> throw IOException("This library item has an unsupported storage type.")
            }
        } catch (exception: Exception) {
            if (game.storageType == LibraryStorageType.FOLDER) {
                AlertDialog.Builder(this)
                    .setTitle("Folder access needs attention")
                    .setMessage(
                        exception.message ?: "The selected folder or saved start page is unavailable."
                    )
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("Remove shortcut") { _, _ -> removeLibraryGame(game) }
                    .setPositiveButton("Reconnect") { _, _ -> requestFolderReconnect(game) }
                    .show()
            } else {
                showHome(exception.message ?: "This imported game is unavailable. Replace its ZIP archive or remove it.")
            }
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

    private fun supportsPerGameProfiles(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }

    private fun isolateGameSaves(game: LibraryGame) {
        if (!supportsPerGameProfiles()) {
            AlertDialog.Builder(this)
                .setTitle("Per-game saves are unavailable")
                .setMessage(
                    "Update Android System WebView to a version that supports isolated profiles. " +
                        "This device currently cannot safely separate or clear one game's browser data."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }

        if (game.browserProfile.isNotBlank()) {
            confirmClearGameData(game)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Isolate saves for " + game.title + "?")
            .setMessage(
                "This game still uses the older shared WebView profile. Pocket Gameplay will " +
                    "switch it to a new private profile. Existing saves in the shared profile " +
                    "will not be copied or deleted, and this game will start with fresh data."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Isolate saves") { _, _ ->
                val retainedOrigin = game.originKey.ifBlank {
                    if (game.storageType == LibraryStorageType.FOLDER) {
                        GameOrigin.forFolderUri(game.sourceUri)
                    } else {
                        game.id
                    }
                }
                try {
                    val updated = game.copy(
                        originKey = retainedOrigin,
                        browserProfile = GameBrowserProfile.forGameId(game.id)
                    )
                    if (!libraryStore.update(updated)) {
                        throw IOException("The library entry no longer exists.")
                    }
                    showHome("This game now has an isolated save profile. Its old shared saves were left untouched.")
                } catch (exception: IOException) {
                    showHome("Could not isolate this game's saves: " + (exception.message ?: "Unknown error."))
                }
            }
            .show()
    }

    private fun confirmClearGameData(game: LibraryGame) {
        if (game.browserProfile.isBlank()) {
            isolateGameSaves(game)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Clear data for " + game.title + "?")
            .setMessage(
                "This permanently clears this game's local saves, site storage, cookies, " +
                    "cache, IndexedDB and service workers. Other games' profiles are not affected."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear data") { _, _ ->
                clearGameData(game)
            }
            .show()
    }

    private fun clearGameData(game: LibraryGame) {
        if (!supportsPerGameProfiles()) {
            showHome("Per-game data clearing is not supported by this System WebView.")
            return
        }

        try {
            if (currentWebViewGameId == game.id) destroyWebView()
            ProfileStore.getInstance().deleteProfile(game.browserProfile)
            showHome("A data reset was requested. The game's profile will be recreated the next time it is played.")
        } catch (exception: Exception) {
            showHome("Could not clear this game's data: " + (exception.message ?: "Unknown error."))
        }
    }

    /** Deletes only a named profile. Legacy games share the default profile, which is never deleted. */
    private fun deleteBrowserProfile(game: LibraryGame) {
        if (game.browserProfile.isBlank() || !supportsPerGameProfiles()) return
        if (currentWebViewProfile == game.browserProfile) destroyWebView()

        try {
            ProfileStore.getInstance().deleteProfile(game.browserProfile)
        } catch (exception: Exception) {
            throw IOException("Could not delete this game's browser data.", exception)
        }
    }

    private fun destroyWebView() {
        cancelDeveloperConsolePolling()
        developerConsolePageReady = false
        webView?.let { view ->
            view.stopLoading()
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        webView = null
        currentWebViewProfile = null
        currentWebViewGameId = null
        if (::pathHandler.isInitialized) pathHandler.clearGameRoot()
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
            if (currentWebViewGameId == game.id) destroyWebView()
            deleteBrowserProfile(game)
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
        val storedGames = try {
            libraryStore.all().filter { it.storageType == LibraryStorageType.IMPORTED }
        } catch (_: IOException) {
            null
        }
        val storedIds = storedGames?.map { it.directoryName }?.toSet()

        storedGames?.forEach { game ->
            val backupPrefix = ".backup-" + game.directoryName + "-"
            val backups = gamesDirectory.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith(backupPrefix) }
                .orEmpty()
                .sortedByDescending { it.lastModified() }
            if (backups.isNotEmpty()) {
                val destination = File(gamesDirectory, game.directoryName)
                val currentIsValid = runCatching { resolveGameRoot(game) }.isSuccess
                if (currentIsValid) {
                    backups.forEach { it.deleteRecursively() }
                } else {
                    val backup = backups.first()
                    if (destination.exists()) destination.deleteRecursively()
                    if (backup.renameTo(destination)) {
                        backups.filterNot { it == backup }.forEach { it.deleteRecursively() }
                    }
                }
            }
        }

        gamesDirectory.listFiles()?.forEach { directory ->
            if (directory.name.startsWith(".staging-")) {
                directory.deleteRecursively()
            } else if (directory.name.startsWith(".backup-")) {
                // Keep a backup if startup restoration could not safely move it into place.
            } else if (storedIds != null && directory.name !in storedIds) {
                directory.deleteRecursively()
            }
        }
    }

    private fun openGame(root: DocumentFile, entryPoint: HtmlEntryPoint, game: LibraryGame) {
        developerConsolePageReady = false
        cancelDeveloperConsolePolling()
        cancelPendingFileChooser()
        webView?.stopLoading()

        val originKey = game.originKey.takeIf { it.isNotBlank() } ?: when (game.storageType) {
            LibraryStorageType.FOLDER -> GameOrigin.forFolderUri(game.sourceUri)
            else -> game.id
        }
        val assetHost = GameOrigin.domainFor(originKey)
        currentGameAssetHost = assetHost
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(assetHost)
            .addPathHandler(
                DeveloperConsole.ASSET_PATH_PREFIX,
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()
        showPlayer(assetLoader, assetHost, game.browserProfile)
        currentWebViewGameId = game.id
        pathHandler.setGameRoot(root)

        val encodedPath = entryPoint.relativePath
            .split('/')
            .joinToString("/") { Uri.encode(it) }
        webView?.loadUrl("https://" + assetHost + OfflineGameWebViewClient.GAME_PATH_PREFIX + encodedPath)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showPlayer(
        assetLoader: WebViewAssetLoader,
        assetHost: String,
        profileName: String
    ) {
        isPlaying = true
        content.removeAllViews()

        val selectedProfile = profileName.takeIf {
            it.isNotBlank() && supportsPerGameProfiles()
        }
        if (webView != null && currentWebViewProfile != selectedProfile) {
            destroyWebView()
        }

        val player = webView ?: WebView(this).also { view ->
            if (selectedProfile != null) {
                WebViewCompat.setProfile(view, selectedProfile)
            }
            currentWebViewProfile = selectedProfile
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

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: WebChromeClient.FileChooserParams
                ): Boolean {
                    return launchGameFileChooser(filePathCallback, fileChooserParams)
                }
            }
            view.setBackgroundColor(Color.BLACK)
            webView = view
        }

        player.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            beginGameDownload(url, contentDisposition, mimeType)
        }

        player.webViewClient = OfflineGameWebViewClient(
            assetLoader = assetLoader,
            gamePathHandler = pathHandler,
            assetHost = assetHost,
            onPageStarted = {
                mainHandler.post {
                    if (webView === player) {
                        cancelPendingFileChooser()
                        cancelDeveloperConsolePolling()
                        developerConsolePageReady = false
                        developerConsoleButton?.apply {
                            text = getString(R.string.developer_console)
                            isEnabled = false
                        }
                    }
                }
            },
            onPageFinished = {
                mainHandler.post {
                    if (webView === player) {
                        developerConsolePageReady = true
                        developerConsoleButton?.apply {
                            text = getString(R.string.developer_console)
                            isEnabled = true
                        }
                    }
                }
            },
            onRendererGone = {
                mainHandler.post {
                    if (webView === player) {
                        destroyWebView()
                        showHome("The game renderer stopped unexpectedly.")
                    }
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

        configureGameSwipeGestureHandling(player)
        createPlayerControls(player)
    }

    /**
     * Lets games receive precise horizontal edge swipes without disabling system Back
     * across the whole screen. Android accepts at most 200dp of vertical exclusion per edge.
     */
    private fun configureGameSwipeGestureHandling(player: WebView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        fun updateExclusionRects() {
            val width = player.width
            val height = player.height
            if (width <= 0 || height <= dp(64)) {
                player.systemGestureExclusionRects = emptyList()
                return
            }

            val edgeWidth = minOf(dp(24), width / 2)
            val exclusionHeight = minOf(dp(200), height - dp(64))
            val top = (height - exclusionHeight) / 2
            val bottom = top + exclusionHeight

            player.systemGestureExclusionRects = listOf(
                Rect(0, top, edgeWidth, bottom),
                Rect(width - edgeWidth, top, width, bottom)
            )
        }

        player.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateExclusionRects()
        }
        player.post { updateExclusionRects() }
    }

    private fun handleBackNavigation() {
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
            PlayerBackAction.EXIT_ACTIVITY -> {
                if (libraryExitConfirmation.shouldExit(SystemClock.elapsedRealtime())) {
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        R.string.press_back_again_to_exit,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            handleBackNavigation()
        } else {
            super.onBackPressed()
        }
    }

    private fun registerPlatformBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { handleBackNavigation() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback
            )
            platformBackCallback = callback
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
        content.requestApplyInsets()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (platformBackCallback as? OnBackInvokedCallback)?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
        }
        platformBackCallback = null
        cancelPendingFileChooser()
        pendingDownload = null
        hideWebFullscreen(notifyPage = false)
        exitImmersiveFullscreen()
        if (::worker.isInitialized) worker.shutdownNow()
        destroyWebView()
        developerConsoleButton = null
        playerControlsButton = null
        playerControlsPanel = null
        super.onDestroy()
    }

    private fun launchGameFileChooser(
        callback: ValueCallback<Array<Uri>>,
        parameters: WebChromeClient.FileChooserParams
    ): Boolean {
        if (parameters.mode != WebChromeClient.FileChooserParams.MODE_OPEN &&
            parameters.mode != WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        ) {
            callback.onReceiveValue(null)
            return true
        }

        cancelPendingFileChooser()
        pendingFileChooser = callback

        val acceptedTypes = GameFileAcceptTypes.normalize(parameters.acceptTypes.toList())

        val multiple = parameters.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (acceptedTypes.size == 1) acceptedTypes.first() else "*/*"
            if (acceptedTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, acceptedTypes.toTypedArray())
            }
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            startActivityForResult(intent, REQUEST_GAME_FILE_PICKER)
            true
        } catch (_: Exception) {
            pendingFileChooser = null
            callback.onReceiveValue(null)
            Toast.makeText(this, "No document picker is available.", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun finishGameFileChooser(resultCode: Int, data: Intent?) {
        val callback = pendingFileChooser ?: return
        pendingFileChooser = null

        if (resultCode != RESULT_OK || data == null) {
            callback.onReceiveValue(null)
            return
        }

        val selectedUris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let(selectedUris::add)
            }
        }
        data.data?.let(selectedUris::add)

        val readableUris = selectedUris.distinct().filter { uri ->
            uri.scheme == "content" && runCatching {
                contentResolver.openInputStream(uri)?.use { true } == true
            }.getOrDefault(false)
        }

        callback.onReceiveValue(readableUris.takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    private fun cancelPendingFileChooser() {
        pendingFileChooser?.onReceiveValue(null)
        pendingFileChooser = null
    }

    private fun beginGameDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val assetHost = currentGameAssetHost
        if (assetHost == null) {
            Toast.makeText(this, "No game is currently open.", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadUri = Uri.parse(url)
        val sourcePath = LocalGameDownloadPath.resolve(
            scheme = downloadUri.scheme,
            host = downloadUri.host,
            port = downloadUri.port,
            path = downloadUri.path,
            expectedHost = assetHost
        )
        if (sourcePath == null) {
            Toast.makeText(
                this,
                "Only downloads of files inside the current game folder are supported.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val source = pathHandler.resolveDocument(sourcePath)
        if (source == null) {
            Toast.makeText(this, "The requested game file could not be found.", Toast.LENGTH_SHORT).show()
            return
        }

        val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            .ifBlank { source.name ?: "download" }
        val safeName = guessedName
            .substringAfterLast('/')
            .filter { it != '\\' && it != '\u0000' && it != '\r' && it != '\n' }
            .take(120)
            .takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: "download"
        val candidateMimeType = mimeType?.substringBefore(';')?.trim()
        val safeMimeType = candidateMimeType?.takeIf { candidate ->
            candidate.count { it == '/' } == 1 &&
                candidate.none { it.isWhitespace() }
        } ?: GameAssetMimeTypes.forFileName(safeName)

        pendingDownload = PendingGameDownload(source.uri, safeName)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = safeMimeType
            putExtra(Intent.EXTRA_TITLE, safeName)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivityForResult(intent, REQUEST_GAME_DOWNLOAD)
        } catch (_: Exception) {
            pendingDownload = null
            Toast.makeText(this, "No location is available to save this file.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishGameDownload(resultCode: Int, data: Intent?) {
        val download = pendingDownload
        pendingDownload = null
        val destinationUri = data?.data

        if (resultCode != RESULT_OK || download == null || destinationUri == null) {
            return
        }
        if (destinationUri.scheme != "content") {
            Toast.makeText(this, "The selected save location is not supported.", Toast.LENGTH_SHORT).show()
            return
        }

        worker.execute {
            try {
                val input = contentResolver.openInputStream(download.sourceUri)
                    ?: throw IOException("Could not read the selected game file.")
                val output = contentResolver.openOutputStream(destinationUri, "w")
                    ?: throw IOException("Could not open the selected save location.")

                input.use { source ->
                    output.use { target ->
                        source.copyTo(target)
                    }
                }
                mainHandler.post {
                    Toast.makeText(this, "Saved " + download.fileName, Toast.LENGTH_SHORT).show()
                }
            } catch (exception: Exception) {
                val message = exception.message ?: "The file could not be saved."
                mainHandler.post {
                    Toast.makeText(this, "Download failed: " + message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun toggleDeveloperConsole(player: WebView) {
        val button = developerConsoleButton ?: return
        if (webView !== player || !developerConsolePageReady) return

        cancelDeveloperConsolePolling()
        button.isEnabled = false
        player.evaluateJavascript(DeveloperConsole.TOGGLE_SCRIPT) { result ->
            if (webView !== player || developerConsoleButton !== button || !developerConsolePageReady) return@evaluateJavascript

            when (developerConsoleStatus(result)) {
                "loading" -> {
                    applyDeveloperConsoleStatus("loading")
                    pollDeveloperConsoleStatus(player)
                }
                "ready" -> applyDeveloperConsoleStatus("ready")
                "load_error" -> applyDeveloperConsoleStatus("load_error")
                "init_error" -> applyDeveloperConsoleStatus("init_error")
                "timeout" -> applyDeveloperConsoleStatus("timeout")
                else -> applyDeveloperConsoleStatus("execution_error")
            }
        }
    }

    private fun pollDeveloperConsoleStatus(player: WebView) {
        cancelDeveloperConsolePolling()
        var attemptsRemaining = DEVELOPER_CONSOLE_POLL_ATTEMPTS
        lateinit var poll: Runnable
        poll = Runnable {
            if (webView !== player || developerConsoleButton == null || !developerConsolePageReady) {
                developerConsolePoll = null
                return@Runnable
            }

            player.evaluateJavascript(DeveloperConsole.STATUS_SCRIPT) { result ->
                if (webView !== player || developerConsoleButton == null || !developerConsolePageReady) {
                    developerConsolePoll = null
                    return@evaluateJavascript
                }

                val status = developerConsoleStatus(result)
                if (status == "loading" && attemptsRemaining > 0) {
                    attemptsRemaining--
                    mainHandler.postDelayed(poll, DEVELOPER_CONSOLE_POLL_INTERVAL_MS)
                } else {
                    developerConsolePoll = null
                    applyDeveloperConsoleStatus(
                        if (status == "loading" || status == null) "timeout" else status
                    )
                }
            }
        }
        developerConsolePoll = poll
        mainHandler.post(poll)
    }

    private fun cancelDeveloperConsolePolling() {
        developerConsolePoll?.let { mainHandler.removeCallbacks(it) }
        developerConsolePoll = null
    }

    private fun developerConsoleStatus(result: String?): String? {
        return result?.trim()?.removeSurrounding("\"")
    }

    private fun applyDeveloperConsoleStatus(status: String) {
        val button = developerConsoleButton ?: return
        when (status) {
            "loading" -> {
                button.text = getString(R.string.developer_console_loading)
                button.isEnabled = false
            }
            "ready" -> {
                button.text = getString(R.string.developer_console_ready)
                button.isEnabled = false
            }
            else -> {
                button.text = getString(R.string.developer_console)
                button.isEnabled = developerConsolePageReady
                val message = when (status) {
                    "load_error" ->
                        "Dev console asset could not load. The bundled Eruda file or local asset route may be missing."
                    "init_error" ->
                        "Eruda loaded but could not initialize. Reload the game and try again."
                    "timeout" ->
                        "Dev console timed out loading its local asset. Reload the game and try again."
                    else ->
                        "Dev console did not respond. Reload the game and try again."
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
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
            setBackgroundResource(R.drawable.bg_library_card)
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
            toggleDeveloperConsole(player)
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
        content.requestApplyInsets()
    }

    private fun updatePlayerControlInsets(leftInset: Int, topInset: Int, rightInset: Int) {
        val startInset = if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            rightInset
        } else {
            leftInset
        }
        playerControlsButton?.let { button ->
            (button.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.topMargin = dp(12) + topInset
                params.marginStart = dp(12) + startInset
                button.layoutParams = params
            }
        }
        playerControlsPanel?.let { panel ->
            (panel.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.topMargin = dp(64) + topInset
                params.marginStart = dp(12) + startInset
                panel.layoutParams = params
            }
        }
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
        private const val REQUEST_GAME_FILE_PICKER = 43
        private const val REQUEST_GAME_DOWNLOAD = 44
        private const val REQUEST_FOLDER_RECONNECT = 45
        private const val REQUEST_ZIP_REPLACEMENT = 46
        private const val DEVELOPER_CONSOLE_POLL_ATTEMPTS = 80
        private const val DEVELOPER_CONSOLE_POLL_INTERVAL_MS = 200L
    }
}
