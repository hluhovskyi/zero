package com.hluhovskyi.zero.transactions.edit

import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.DateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.io.Closeable

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class DefaultTransactionEditViewModelTest {

    @Mock private lateinit var amountFormatter: AmountFormatter

    @Mock private lateinit var dateFormatter: DateFormatter

    private val testDispatcher = UnconfinedTestDispatcher()
    private val date = LocalDateTime(2026, 6, 2, 11, 4)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `save hidden when form is unmodified`() = runTest {
        val vm = createViewModel(
            state = TransactionEditUseCase.State(date = date, isModified = false),
        )
        assertFalse(vm.state.first().isSaveVisible)
    }

    @Test
    fun `save shown once the form is modified`() = runTest {
        val vm = createViewModel(
            state = TransactionEditUseCase.State(date = date, isModified = true),
        )
        assertTrue(vm.state.first().isSaveVisible)
    }

    @Test
    fun `save shown in duplicate mode even when unmodified`() = runTest {
        val vm = createViewModel(
            state = TransactionEditUseCase.State(date = date, isModified = false),
            isDuplicateMode = true,
        )
        assertTrue(vm.state.first().isSaveVisible)
    }

    private fun createViewModel(
        state: TransactionEditUseCase.State,
        isEditMode: Boolean = true,
        isDuplicateMode: Boolean = false,
    ) = DefaultTransactionEditViewModel(
        useCase = FakeUseCase(flowOf(state)),
        isEditMode = isEditMode,
        isDuplicateMode = isDuplicateMode,
        amountFormatter = amountFormatter,
        dateFormatter = dateFormatter,
    )

    private class FakeUseCase(
        override val state: Flow<TransactionEditUseCase.State>,
    ) : TransactionEditUseCase {
        override fun perform(action: TransactionEditUseCase.Action) = Unit
        override fun attach(): Closeable = Closeables.empty()
    }
}
