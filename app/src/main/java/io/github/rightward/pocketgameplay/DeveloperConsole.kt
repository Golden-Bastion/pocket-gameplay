package io.github.rightward.pocketgameplay

internal object DeveloperConsole {
    const val ASSET_PATH_PREFIX = "/__pocket/"

    /**
     * Injects Eruda once for the current page and returns an immediate state.
     * Async asset loading updates window.__pocketGameplayEruda.status, which the
     * native player polls so failures remain visible even when the page console is hidden.
     */
    val TOGGLE_SCRIPT = """
        (function () {
            var state = window.__pocketGameplayEruda;
            if (!state) {
                state = {
                    initialized: false,
                    loading: false,
                    visible: false,
                    status: 'idle'
                };
                window.__pocketGameplayEruda = state;
            }

            if (state.initialized && window.eruda) {
                if (state.visible) {
                    window.eruda.hide();
                    state.visible = false;
                    state.status = 'hidden';
                } else {
                    window.eruda.show();
                    window.eruda.show('console');
                    state.visible = true;
                    state.status = 'shown';
                }
                return state.status;
            }

            if (state.loading) {
                state.status = 'loading';
                return state.status;
            }

            state.loading = true;
            state.status = 'loading';

            var script = document.createElement('script');
            script.src = '/__pocket/eruda.js';

            var timeoutId = window.setTimeout(function () {
                if (!state.loading) return;
                state.loading = false;
                state.status = 'timeout';
                script.onload = null;
                script.onerror = null;
                if (script.parentNode) script.parentNode.removeChild(script);
            }, 12000);

            script.onload = function () {
                window.clearTimeout(timeoutId);
                state.loading = false;
                try {
                    if (!window.eruda || typeof window.eruda.init !== 'function') {
                        throw new Error('The bundled script did not expose Eruda.');
                    }

                    window.eruda.init({
                        tool: ['console', 'elements', 'network', 'resources', 'sources', 'info', 'snippets'],
                        autoScale: true,
                        useShadowDom: true
                    });

                    // Open the panel explicitly, then select the Console tool.
                    // Keep Eruda's floating entry button visible as an alternate entry point.
                    window.eruda.show();
                    window.eruda.show('console');

                    state.initialized = true;
                    state.visible = true;
                    state.status = 'shown';
                } catch (error) {
                    state.initialized = false;
                    state.visible = false;
                    state.status = 'init_error';
                }
            };

            script.onerror = function () {
                window.clearTimeout(timeoutId);
                state.loading = false;
                state.initialized = false;
                state.visible = false;
                state.status = 'load_error';
                if (script.parentNode) script.parentNode.removeChild(script);
            };

            try {
                var parent = document.head || document.documentElement;
                if (!parent) throw new Error('The page has no script container.');
                parent.appendChild(script);
            } catch (error) {
                window.clearTimeout(timeoutId);
                state.loading = false;
                state.status = 'load_error';
            }

            return state.status;
        })();
    """.trimIndent()

    val STATUS_SCRIPT = """
        (function () {
            var state = window.__pocketGameplayEruda;
            return state && typeof state.status === 'string' ? state.status : 'missing';
        })()
    """.trimIndent()
}
