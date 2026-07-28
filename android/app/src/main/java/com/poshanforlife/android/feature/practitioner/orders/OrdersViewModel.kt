package com.poshanforlife.android.feature.practitioner.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.OrderRepository
import com.poshanforlife.android.core.network.OrderDetailDto
import com.poshanforlife.android.core.network.OrderListItemDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateOrderRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L

sealed class OrderListUiState {
    data object Loading : OrderListUiState()
    data class Success(val orders: List<OrderListItemDto>) : OrderListUiState()
    data class Error(val message: String) : OrderListUiState()
}

sealed class OrderDetailUiState {
    data object Loading : OrderDetailUiState()
    data class Success(val order: OrderDetailDto) : OrderDetailUiState()
    data class Error(val message: String) : OrderDetailUiState()
}

sealed class MarkAsPaidState {
    data object Idle : MarkAsPaidState()
    data object Saving : MarkAsPaidState()
    data class Error(val message: String) : MarkAsPaidState()
}

data class OrderFilters(
    val status: String? = null,
    val paymentStatus: String? = null,
    val search: String = "",
    val dateFrom: String? = null,
    val dateTo: String? = null,
)

/**
 * Backs both OrdersScreen and OrderDetailScreen (dual-purpose, keyed on
 * SavedStateHandle["orderId"] presence — same convention as DocumentsViewModel).
 */
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orderId: String? = savedStateHandle["orderId"]

    // ---- list ----------------------------------------------------------

    private val _listState = MutableStateFlow<OrderListUiState>(OrderListUiState.Loading)
    val listState: StateFlow<OrderListUiState> = _listState.asStateFlow()

    private val _filters = MutableStateFlow(OrderFilters())
    val filters: StateFlow<OrderFilters> = _filters.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var listJob: Job? = null

    // ---- detail ----------------------------------------------------------

    private val _detailState = MutableStateFlow<OrderDetailUiState>(OrderDetailUiState.Loading)
    val detailState: StateFlow<OrderDetailUiState> = _detailState.asStateFlow()

    private val _markAsPaidState = MutableStateFlow<MarkAsPaidState>(MarkAsPaidState.Idle)
    val markAsPaidState: StateFlow<MarkAsPaidState> = _markAsPaidState.asStateFlow()

    init {
        if (orderId == null) refreshList() else loadDetail()
    }

    fun refreshList() {
        listJob?.cancel()
        listJob = viewModelScope.launch { performListLoad() }
    }

    fun onStatusFilterChange(status: String?) {
        _filters.update { it.copy(status = status) }
        refreshList()
    }

    fun onPaymentStatusFilterChange(paymentStatus: String?) {
        _filters.update { it.copy(paymentStatus = paymentStatus) }
        refreshList()
    }

    fun onDateRangeChange(dateFrom: String?, dateTo: String?) {
        _filters.update { it.copy(dateFrom = dateFrom, dateTo = dateTo) }
        refreshList()
    }

    fun onSearchChange(query: String) {
        _filters.update { it.copy(search = query) }
        listJob?.cancel()
        listJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performListLoad()
        }
    }

    private suspend fun performListLoad() {
        _isRefreshing.update { true }
        val f = _filters.value
        when (
            val result = orderRepository.list(
                status = f.status,
                paymentStatus = f.paymentStatus,
                search = f.search.ifBlank { null },
                dateFrom = f.dateFrom,
                dateTo = f.dateTo,
            )
        ) {
            is Result.Success -> _listState.update { OrderListUiState.Success(result.data) }
            is Result.Error -> _listState.update { OrderListUiState.Error(result.message) }
            Result.Loading -> Unit
        }
        _isRefreshing.update { false }
    }

    fun loadDetail() {
        val id = checkNotNull(orderId)
        viewModelScope.launch {
            _detailState.update { OrderDetailUiState.Loading }
            when (val result = orderRepository.getDetail(id)) {
                is Result.Success -> _detailState.update { OrderDetailUiState.Success(result.data) }
                is Result.Error -> _detailState.update { OrderDetailUiState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }

    /** Confirmed by the caller's own dialog (notes a transaction + invoice will be generated) before this is called. */
    fun confirmMarkAsPaid() {
        val id = checkNotNull(orderId)
        viewModelScope.launch {
            _markAsPaidState.update { MarkAsPaidState.Saving }
            when (val result = orderRepository.update(id, UpdateOrderRequest(paymentStatus = "paid"))) {
                is Result.Success -> {
                    _detailState.update { OrderDetailUiState.Success(result.data) }
                    _markAsPaidState.update { MarkAsPaidState.Idle }
                }
                is Result.Error -> _markAsPaidState.update { MarkAsPaidState.Error(result.message) }
                Result.Loading -> Unit
            }
        }
    }
}
