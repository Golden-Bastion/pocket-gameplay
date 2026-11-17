package io.github.rightward.pocketgameplay

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class OfflineGameWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val gamePathHandler: GamePathHandler,
    private val onRendererGone: () -> Unit
) : WebViewClientCompat() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse {
        val uri = request.url
        val isLocalAssetHost = uri.scheme == "https" &&
            uri.host == LOCAL_ASSET_HOST &&
            (uri.port == -1 || uri.port == 443)

        if (!isLocalAssetHost) {
            return GamePathHandler.errorResponse(403, "Offline content only")
        }

        val encodedPath = uri.encodedPath ?: return GamePathHandler.errorResponse(404, "Not Found")

        // The /game/ route is the selected entry-point URL. Other paths on this reserved
        // local origin represent root-relative assets such as /assets/sprite.png.
        return if (encodedPath.startsWith(GAME_PATH_PREFIX)) {
            assetLoader.shouldInterceptRequest(uri)
                ?: GamePathHandler.errorResponse(404, "Not Found")
        } else {
            gamePathHandler.handle(encodedPath.removePrefix("/"))
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val uri: Uri = request.url
        return !(uri.scheme == "https" &&
            uri.host == LOCAL_ASSET_HOST &&
            (uri.port == -1 || uri.port == 443))
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: android.webkit.RenderProcessGoneDetail
    ): Boolean {
        onRendererGone()
        return true
    }

    companion object {
        const val LOCAL_ASSET_HOST = "appassets.androidplatform.net"
        const val GAME_PATH_PREFIX = "/game/"
        const val GAME_BASE_URL = "https://$LOCAL_ASSET_HOST$GAME_PATH_PREFIX"
    }
}
