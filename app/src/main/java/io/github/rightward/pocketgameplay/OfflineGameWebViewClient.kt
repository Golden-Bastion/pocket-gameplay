package io.github.rightward.pocketgameplay

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class OfflineGameWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val onRendererGone: () -> Unit
) : WebViewClientCompat() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse {
        val uri = request.url
        val isLocalGameUrl = uri.scheme == "https" &&
            uri.host == LOCAL_ASSET_HOST &&
            (uri.port == -1 || uri.port == 443) &&
            uri.encodedPath?.startsWith(GAME_PATH_PREFIX) == true

        return if (isLocalGameUrl) {
            assetLoader.shouldInterceptRequest(uri)
                ?: GamePathHandler.errorResponse(404, "Not Found")
        } else {
            GamePathHandler.errorResponse(403, "Offline content only")
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val uri: Uri = request.url
        return !(uri.scheme == "https" &&
            uri.host == LOCAL_ASSET_HOST &&
            (uri.port == -1 || uri.port == 443) &&
            uri.encodedPath?.startsWith(GAME_PATH_PREFIX) == true)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
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
