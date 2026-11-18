package io.github.rightward.pocketgameplay

internal object DeveloperConsole {
    const val ASSET_PATH_PREFIX = "/__pocket/"

    /**
     * Loads the bundled Eruda script using the same minimal sequence as its
     * documented bookmarklet: append the script, then call eruda.init().
     * Eruda owns its floating entry button and panel interactions.
     */
    val TOGGLE_SCRIPT = """
        (function () {
            var state = window.__pocketGameplayEruda;
            if (!state) {
                state = {
                    initialized: false,
                    loading: false,
                    status: 'idle'
                };
                window.__pocketGameplayEruda = state;
            }

            if (state.initialized && window.eruda) {
                state.status = 'ready';
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

                    // Match Eruda's bookmarklet: eruda.init() with default behavior.
                    window.eruda.init();

                    state.initialized = true;
                    state.status = 'ready';
                } catch (error) {
                    state.initialized = false;
                    state.status = 'init_error';
                }
            };

            script.onerror = function () {
                window.clearTimeout(timeoutId);
                state.loading = false;
                state.initialized = false;
                state.status = 'load_error';
                if (script.parentNode) script.parentNode.removeChild(script);
            };

            try {
                if (!document.body) throw new Error('The page body is not available.');
                // The bookmarklet appends the script to document.body.
                document.body.appendChild(script);
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
