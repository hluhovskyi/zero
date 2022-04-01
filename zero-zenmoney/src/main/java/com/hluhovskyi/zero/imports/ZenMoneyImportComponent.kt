package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.resource.ResourceResolver
import dagger.Provides
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ZenMoneyImportScope

@ZenMoneyImportScope
@dagger.Component(
    modules = [ZenMoneyImportComponent.Module::class],
    dependencies = [ZenMoneyImportComponent.Dependencies::class],
)
abstract class ZenMoneyImportComponent {

    abstract val importSourceUseCase: ImportSourceUseCase

    interface Dependencies {
        val idGenerator: IdGenerator
        val logger: Logger
        val resourceResolver: ResourceResolver
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerZenMoneyImportComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ZenMoneyImportComponent> {

        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ZenMoneyImportScope
        fun importSourceUseCase(
            idGenerator: IdGenerator,
            logger: Logger,
            resolver: ResourceResolver
        ): ImportSourceUseCase = ZenMoneyImportSourceUseCase(
            resourceResolver = resolver,
            idGenerator = idGenerator,
            logger = logger,
        )
    }
}
