package com.hluhovskyi.zero.activity

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Single reusable foreground-activity provider. Registers [Application.ActivityLifecycleCallbacks]
 * and holds a [WeakReference] to the resumed activity. Anything that needs an `Activity` context
 * (e.g. Credential Manager sign-in) reads it through [current].
 */
class CurrentActivityTracker(application: Application) : Application.ActivityLifecycleCallbacks {

    private var current: WeakReference<Activity>? = null

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun current(): Activity? = current?.get()

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
