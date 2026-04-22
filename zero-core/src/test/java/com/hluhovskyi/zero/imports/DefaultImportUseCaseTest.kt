package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Image
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.icons.Icon
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.users.CurrentUserRepository
import com.hluhovskyi.zero.users.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultImportUseCaseTest {

    @Mock private lateinit var parser: SnapshotParser

    @Mock private lateinit var syncEngine: SyncEngine

    @Mock private lateinit var currentUserRepository: CurrentUserRepository

    @Mock private lateinit var iconRepository: IconRepository

    @Mock private lateinit var colorRepository: ColorRepository

    @Mock private lateinit var categoryRepository: CategoryRepository

    @Mock private lateinit var accountRepository: AccountRepository

    private val source = KnownSource.ZeroBackup
    private val userId = Id.Known("user-1")
    private val testUri = Uri("file://test.zero") as Uri.NonEmpty

    @Before
    fun setUp() {
        whenever(parser.source).thenReturn(source)
        whenever(currentUserRepository.query()).thenReturn(flowOf(User(id = userId)))
        lenient().`when`(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>())).thenReturn(flowOf(emptyList()))
        lenient().`when`(accountRepository.query(any<AccountRepository.Criteria>())).thenReturn(flowOf(emptyList()))
        lenient().`when`(iconRepository.query(any<IconRepository.Criteria<List<Icon>>>())).thenReturn(flowOf(emptyList()))
    }

    private fun createUseCase(scope: CoroutineScope) = DefaultImportUseCase(
        parsers = listOf(parser),
        syncEngine = syncEngine,
        currentUserRepository = currentUserRepository,
        iconRepository = iconRepository,
        colorRepository = colorRepository,
        categoryRepository = categoryRepository,
        accountRepository = accountRepository,
        onImportFinishedHandler = OnImportFinishedHandler.Noop,
        coroutineScope = scope,
    )

    @Test
    fun `SelectFile sets error state when parser throws`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.SourceSelection)
        assertNotNull((state as ImportUseCase.State.SourceSelection).error)
    }

    @Test
    fun `DismissError clears the error`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        useCase.perform(ImportUseCase.Action.DismissError)

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.SourceSelection)
        assertNull((state as ImportUseCase.State.SourceSelection).error)
    }

    @Test
    fun `Retry transitions to FilePicker`() = runTest {
        whenever(parser.parse(testUri)).thenThrow(RuntimeException("corrupt file"))

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        useCase.perform(ImportUseCase.Action.Retry)

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.FilePicker) { "Expected FilePicker but got $state" }
    }

    @Test
    fun `buildCategories uses existing category icon when name matches case-insensitively`() = runTest {
        val existingIconId = Id.Known("icon-999")
        val existingColorId = Id.Known("color-999")
        val existingCategory = CategoryRepository.Category(
            id = Id.Known("existing-cat-1"),
            parentCategoryId = Id.Unknown,
            name = "Food",
            iconId = existingIconId,
            colorId = existingColorId,
        )
        whenever(categoryRepository.query(any<CategoryRepository.Criteria<List<CategoryRepository.Category>>>()))
            .thenReturn(flowOf(listOf(existingCategory)))

        val matchedImage = Image(
            uri = Uri("file://food-icon.png") as Uri.NonEmpty,
            description = "food icon",
        )
        val matchedIcon = Icon(id = existingIconId, image = matchedImage)
        whenever(iconRepository.query(any<IconRepository.Criteria<List<Icon>>>()))
            .thenReturn(flowOf(listOf(matchedIcon)))

        whenever(colorRepository.schemeFor(existingColorId)).thenReturn(ColorScheme.Grey)

        val syncCategory = com.hluhovskyi.zero.sync.SyncCategory(
            id = Id.Known("import-cat-1"),
            name = "food",
            iconId = "icon-000",
            colorId = "color-000",
            parentCategoryId = null,
            creationDateTime = kotlinx.datetime.LocalDateTime(2024, 1, 1, 0, 0),
            updatedDateTime = kotlinx.datetime.LocalDateTime(2024, 1, 1, 0, 0),
            deletedAt = null,
        )
        val snapshot = com.hluhovskyi.zero.sync.SyncSnapshot(
            version = 1,
            userId = userId,
            exportedAt = kotlinx.datetime.LocalDateTime(2024, 1, 1, 0, 0),
            categories = listOf(syncCategory),
            accounts = emptyList(),
            transactions = emptyList(),
        )
        whenever(parser.parse(testUri)).thenReturn(snapshot)
        whenever(syncEngine.delta(snapshot, userId)).thenReturn(snapshot)

        val useCase = createUseCase(this)
        useCase.perform(ImportUseCase.Action.SelectSource(source))
        useCase.perform(ImportUseCase.Action.SelectFile(testUri))
        advanceUntilIdle()

        val state = useCase.state.first()
        assert(state is ImportUseCase.State.CategoriesReview) { "Expected CategoriesReview but got $state" }
        val categories = (state as ImportUseCase.State.CategoriesReview).categories
        assert(categories.size == 1)
        assert(categories[0].icon == matchedImage) {
            "Expected matched icon image from existing category but got ${categories[0].icon}"
        }
    }
}
