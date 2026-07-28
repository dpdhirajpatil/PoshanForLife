package com.poshanforlife.android.feature.practitioner.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.TransactionRepository
import com.poshanforlife.android.core.datastore.TokenDataStore
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.TransactionListItemDto
import com.poshanforlife.android.core.network.TransactionTotalsDto
import com.poshanforlife.android.core.network.UserDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TransactionListUiState {
    data object Loading : TransactionListUiState()
    data class Success(val transactions: List<TransactionListItemDto>, val summary: TransactionTotalsDto) : TransactionListUiState()
    data class Error(val message: String) : TransactionListUiState()
}

data class TransactionFilters(
    val practitionerId: String? = null,
    val practitionerName: String? = null,
    val catalogue: String? = null,
    val paymentType: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val tokenDataStore: TokenDataStore,
) : ViewModel() {

    private val _listState = MutableStateFlow<TransactionListUiState>(TransactionListUiState.Loading)
    val listState: StateFlow<TransactionListUiState> = _listState.asStateFlow()

    private val _filters = MutableStateFlow(TransactionFilters())
    val filters: StateFlow<TransactionFilters> = _filters.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** The practitioner filter is ADMIN-only server-side — DOCTOR callers are already hard-scoped. */
    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _practitioners = MutableStateFlow<List<UserDetailDto>>(emptyList())
    val practitioners: StateFlow<List<UserDetailDto>> = _practitioners.asStateFlow()

    private var listJob: Job? = null

    init {
        viewModelScope.launch {
            val isAdmin = tokenDataStore.currentUser().first()?.role == "ADMIN"
            _isAdmin.update { isAdmin }
            if (isAdmin) loadPractitioners()
        }
        refreshList()
    }

    private fun loadPractitioners() {
        viewModelScope.launch {
            val result = transactionRepository.listPractitioners()
            if (result is Result.Success) _practitioners.update { result.data }
        }
    }

    fun refreshList() {
        listJob?.cancel()
        listJob = viewModelScope.launch { performListLoad() }
    }

    fun onPractitionerFilterChange(id: String?, name: String?) {
        _filters.update { it.copy(practitionerId = id, practitionerName = name) }
        refreshList()
    }

    fun onCatalogueFilterChange(catalogue: String?) {
        _filters.update { it.copy(catalogue = catalogue) }
        refreshList()
    }

    fun onPaymentTypeFilterChange(paymentType: String?) {
        _filters.update { it.copy(paymentType = paymentType) }
        refreshList()
    }

    fun onDateRangeChange(dateFrom: String?, dateTo: String?) {
        _filters.update { it.copy(dateFrom = dateFrom, dateTo = dateTo) }
        refreshList()
    }

    private suspend fun performListLoad() {
        _isRefreshing.update { true }
        val f = _filters.value
        when (
            val result = transactionRepository.list(
                userId = f.practitionerId,
                catalogue = f.catalogue,
                paymentType = f.paymentType,
                dateFrom = f.dateFrom,
                dateTo = f.dateTo,
            )
        ) {
            is Result.Success -> _listState.update {
                TransactionListUiState.Success(result.data.transactions, result.data.summary)
            }
            is Result.Error -> _listState.update { TransactionListUiState.Error(result.message) }
            Result.Loading -> Unit
        }
        _isRefreshing.update { false }
    }
}
