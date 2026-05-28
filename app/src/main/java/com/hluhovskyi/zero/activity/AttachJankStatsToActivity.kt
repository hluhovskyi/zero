package com.hluhovskyi.zero.activity

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.d
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.Closeable
import kotlin.time.Duration.Companion.nanoseconds

private const val TAG = "AttachJankStatsToActivity"

class AttachJankStatsToActivity(
    private val activity: FragmentActivity,
    logger: Logger,
) : Attachable {

    private val logger = logger.withTag(TAG)

    private val jankFlow = callbackFlow<FrameData> {
        val stats = JankStats.createAndTrack(activity.window) {
            trySend(it)
        }
        stats.isTrackingEnabled = true
        awaitClose { stats.isTrackingEnabled = false }
    }

    override fun attach(): Closeable = Closeables.of {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                jankFlow
                    .filter { it.isJank }
                    .collect {
                        val millis = it.frameDurationUiNanos.nanoseconds.inWholeMilliseconds
                        logger.d("$millis ms, ${it.states}")
                    }
            }
        }
    }
}
