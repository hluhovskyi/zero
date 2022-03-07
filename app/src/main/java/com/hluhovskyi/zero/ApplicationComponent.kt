package com.hluhovskyi.zero

import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ApplicationScope

@ApplicationScope
@dagger.Component(
    modules = [ApplicationComponent.Module::class],
    dependencies = [ApplicationComponent.Dependencies::class]
)
abstract class ApplicationComponent {

    interface Dependencies {

    }

    companion object {

        fun factory(): Factory = DaggerApplicationComponent.factory()
    }

    @dagger.Component.Factory
    interface Factory {

        fun create(
            dependencies: Dependencies
        ): ApplicationComponent
    }

    @dagger.Module
    object Module {

    }
}