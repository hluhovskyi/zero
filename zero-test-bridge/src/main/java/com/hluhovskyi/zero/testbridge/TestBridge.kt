package com.hluhovskyi.zero.testbridge

interface TestBridge {
    suspend fun clearData()
    suspend fun seedDefaultSetup()
}
