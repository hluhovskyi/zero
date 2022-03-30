package com.hluhovskyi.zero

import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.resource.ResourceResolver
import dagger.BindsInstance
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

    abstract val categoryRepository: CategoryRepository

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

        @BindsInstance
        fun importFileUri(uri: Uri): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ZenMoneyImportScope
        fun categoryRepository(
            importUri: Uri,
            idGenerator: IdGenerator,
            logger: Logger,
            resolver: ResourceResolver
        ): CategoryRepository = CsvCategoryRepository(
            importUri = importUri,
            idGenerator = idGenerator,
            logger = logger,
            resourceResolver = resolver
        )
    }
}
