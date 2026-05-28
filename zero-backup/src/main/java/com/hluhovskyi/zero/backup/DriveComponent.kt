package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.auth.OAuthTokenProvider
import com.hluhovskyi.zero.http.HttpExecutor

/**
 * Manual DI factory for Drive-specific backup impls (sibling of the backend-agnostic
 * [BackupComponent]). A future backend would add its own sibling component.
 */
interface DriveComponent {

    interface Dependencies {
        val httpExecutor: HttpExecutor
        val oauthTokenProvider: OAuthTokenProvider
    }

    val backupClient: BackupClient
    // driveSnapshotParser added in Phase 5

    class Factory(
        private val dependencies: Dependencies,
        private val baseUrl: String,
    ) {
        fun create(): DriveComponent = DefaultDriveComponent(dependencies, baseUrl)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.googleapis.com"

        fun factory(
            dependencies: Dependencies,
            baseUrl: String = DEFAULT_BASE_URL,
        ): Factory = Factory(dependencies, baseUrl)
    }
}

internal class DefaultDriveComponent(
    dependencies: DriveComponent.Dependencies,
    private val baseUrl: String,
) : DriveComponent {

    private val envelopeSerializer = BackupEnvelopeSerializer()

    override val backupClient: BackupClient by lazy {
        DriveBackupClient(
            httpExecutor = dependencies.httpExecutor,
            oauthTokenProvider = dependencies.oauthTokenProvider,
            envelopeSerializer = envelopeSerializer,
            baseUrl = baseUrl,
        )
    }
}
