package com.hluhovskyi.zero.resource

import android.content.Context
import com.hluhovskyi.zero.common.Buildable
import dagger.Provides
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ResourceResolverScope

@ResourceResolverScope
@dagger.Component(
    modules = [ResourceResolverComponent.Module::class],
    dependencies = [ResourceResolverComponent.Dependencies::class],
)
abstract class ResourceResolverComponent {

    abstract val resourceResolver: ResourceResolver

    interface Dependencies {
        val context: Context
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerResourceResolverComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ResourceResolverComponent> {

        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ResourceResolverScope
        internal fun uriResourceResolver(
            context: Context,
        ): UriResourceResolver = UriResourceResolver(context)

        @Provides
        @ResourceResolverScope
        internal fun compositeResourceResolver(
            uriResourceResolver: UriResourceResolver,
        ): ResourceResolver = CompositeResourceResolver(
            mapOf(UriRequest::class to uriResourceResolver),
        )
    }
}
