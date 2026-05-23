package com.hluhovskyi.zero.auth

import com.hluhovskyi.zero.security.SecureKeyValueStore

/** In-memory [SecureKeyValueStore] for tests. */
class FakeSecureKeyValueStore(initial: Map<String, String> = emptyMap()) : SecureKeyValueStore {

    private val store = initial.toMutableMap()

    override suspend fun get(key: String): String? = store[key]

    override suspend fun put(key: String, value: String) {
        store[key] = value
    }

    override suspend fun remove(key: String) {
        store.remove(key)
    }
}
