package io.github.rightward.pocketgameplay

internal object DeveloperConsole {
    const val ASSET_PATH_PREFIX = "/__pocket/"

    val TOGGLE_SCRIPT = """
        (function () {
            var state = window.__pocketGameplayEruda;
            if (!state) {
                state = {
                    initialized: false,
                    loading: false,
                    visible: false
                };
                window.__pocketGameplayEruda = state;
            }

            function toggleExistingConsole() {
                if (state.visible) {
                    window.eruda.hide();
                    state.visible = false;
                } else {
                    window.eruda.show('console');
                    state.visible = true;
                }
            }

            if (state.initialized && window.eruda) {
                toggleExistingConsole();
                return;
            }

            if (state.loading) {
                return;
            }

            state.loading = true;
            var script = document.createElement('script');
            script.src = '/__pocket/eruda.js';
            script.onload = function () {
                state.loading = false;
                try {
                    if (!window.eruda || typeof window.eruda.init !== 'function') {
                        throw new Error('Eruda did not expose its initialization API.');
                    }

                    window.eruda.init({
                        tool: ['console', 'elements', 'network', 'resources', 'sources', 'info', 'snippets'],
                        autoScale: true,
                        useShadowDom: true
                    });

                    var entryButton = window.eruda.get('entryBtn');
                    if (entryButton && typeof entryButton.hide === 'function') {
                        entryButton.hide();
                    }

                    state.initialized = true;
                    state.visible = true;
                    window.eruda.show('console');
                } catch (error) {
                    state.initialized = false;
                    state.visible = false;
                    console.error('Pocket Gameplay could not initialize Eruda:', error);
                }
            };

            script.onerror = function () {
                state.loading = false;
                console.error('Pocket Gameplay could not load the bundled Eruda developer console.');
            };

            (document.head || document.documentElement).appendChild(script);
        })();
    """.trimIndent()
}
