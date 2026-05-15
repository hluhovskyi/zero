package com.hluhovskyi.zero

import com.hluhovskyi.zero.testbridge.DatabaseTestBridge
import com.hluhovskyi.zero.testbridge.TestBridgeContainer

internal object TestBridgeContainerFactory {

    fun create(component: ApplicationComponent): TestBridgeContainer {
        val db = component.databaseComponent
        return TestBridgeContainer(
            database = DatabaseTestBridge(
                cleanupJob = db.cleanupJob,
                currentUserRepository = db.currentUserRepository,
                accountRepository = db.accountRepository,
                categoryRepository = db.categoryRepository,
                transactionRepository = db.transactionRepository,
            ),
        )
    }
}
