package com.hluhovskyi.zero.testbridge

interface DatabaseTestBridge {
    suspend fun clearData()
    suspend fun seedDefaultSetup()
}
