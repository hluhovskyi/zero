package com.hluhovskyi.zero

import java.lang.IllegalStateException

interface HasApplicationComponent {

    val applicationComponent: ApplicationComponent
}

internal fun Any.requireApplicationComponent(): ApplicationComponent = if (this is HasApplicationComponent) {
    applicationComponent
} else {
    throw IllegalStateException("$this is expected to implement HasApplicationComponent")
}
