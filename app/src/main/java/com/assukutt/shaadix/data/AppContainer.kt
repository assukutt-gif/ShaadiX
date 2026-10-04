package com.assukutt.shaadix.data

import android.content.Context

/** Small application container: one place wires the API, token vault and repositories. */
class AppContainer(context: Context) {
    val preferences = PreferencesStore(context)
    val tokens = SecureTokenStore(context)
    val backend = ShaadiXBackendRepository(ShaadiXApiFactory(tokens).api, tokens)
}

