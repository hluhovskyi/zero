package com.hluhovskyi.zero.activity

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Closeables
import java.io.Closeable
import java.lang.ref.WeakReference

/**
 * Tracks the foreground activity. Implements `() -> Activity?` so it can be bound directly across
 * module boundaries (e.g. `zero-auth`); [attach] registers/unregisters the lifecycle callbacks.
 */
class CurrentActivityTracker(
    private val application: Application,
) : Application.ActivityLifecycleCallbacks,
    () -> Activity?,
    Attachable {

    private var currentRef: WeakReference<Activity>? = null

    override fun invoke(): Activity? = currentRef?.get()

    override fun attach(): Closeable {
        application.registerActivityLifecycleCallbacks(this)
        val callbacks = this
        return Closeables.from { application.unregisterActivityLifecycleCallbacks(callbacks) }
    }

    override fun onActivityResumed(activity: Activity) {
        currentRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentRef?.get() === activity) currentRef = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
