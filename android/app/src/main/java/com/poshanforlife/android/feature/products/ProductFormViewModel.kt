package com.poshanforlife.android.feature.products

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.ProductRepository
import com.poshanforlife.android.core.network.CreateProductRequest
import com.poshanforlife.android.core.network.ProductSegmentDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateProductRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

sealed class ProductFormSaveState {
    data object Idle : ProductFormSaveState()
    data object Saving : ProductFormSaveState()
    data object Saved : ProductFormSaveState()
    data class Error(val message: String) : ProductFormSaveState()
}

data class ProductFormUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val segments: List<ProductSegmentDto> = emptyList(),
    val segmentId: String? = null,
    val name: String = "",
    val description: String = "",
    val priceInr: String = "",
    val sku: String = "",
    val status: String = "draft",
    val images: List<String> = emptyList(),
    /** Non-null once this product exists server-side (editing an existing one, or just created) — image upload requires an id, per the backend's per-product upload endpoint. */
    val savedProductId: String? = null,
    val uploadingImage: Boolean = false,
    val saveState: ProductFormSaveState = ProductFormSaveState.Idle,
)

/**
 * Create when productId is absent from the route, edit when present — same
 * dual-purpose convention as CatalogueItemFormViewModel. Unlike catalogue's
 * cover image (uploaded to a type-level endpoint before the item exists),
 * a product's images can only be uploaded once it has an id — so on a
 * successful create this ViewModel flips itself into "editing" the new
 * product in place, rather than requiring a second navigation round trip
 * before photos can be added.
 */
@HiltViewModel
class ProductFormViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private var productId: String? = savedStateHandle["productId"]
    val isEditing: Boolean = productId != null

    private val _uiState = MutableStateFlow(ProductFormUiState())
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val segmentsResult = productRepository.listSegments()
            val segments = (segmentsResult as? Result.Success)?.data.orEmpty()

            val id = productId
            if (id == null) {
                _uiState.update {
                    it.copy(loading = false, segments = segments, segmentId = segments.firstOrNull()?.id)
                }
                return@launch
            }
            when (val result = productRepository.get(id)) {
                is Result.Success -> {
                    val product = result.data
                    _uiState.update {
                        it.copy(
                            loading = false,
                            segments = segments,
                            segmentId = product.segmentId,
                            name = product.name,
                            description = product.description.orEmpty(),
                            priceInr = product.priceInr?.toString().orEmpty(),
                            sku = product.sku.orEmpty(),
                            status = product.status,
                            images = product.images,
                            savedProductId = product.id,
                        )
                    }
                }
                is Result.Error -> _uiState.update { it.copy(loading = false, loadError = result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun onSegmentChange(value: String) = updateState { it.copy(segmentId = value) }
    fun onNameChange(value: String) = updateState { it.copy(name = value) }
    fun onDescriptionChange(value: String) = updateState { it.copy(description = value) }
    fun onPriceChange(value: String) = updateState { it.copy(priceInr = value) }
    fun onSkuChange(value: String) = updateState { it.copy(sku = value) }
    fun onStatusChange(value: String) = updateState { it.copy(status = value) }

    private fun updateState(transform: (ProductFormUiState) -> ProductFormUiState) {
        _uiState.update(transform)
    }

    fun onImagesPicked(uris: List<Uri>) {
        val id = productId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingImage = true) }
            for (uri in uris) {
                // Backend validates the multipart Content-Type against an exact allowlist
                // (image/jpeg|png|webp|gif) — must be the real resolved MIME type, not a
                // wildcard like "image/*", or every upload 422s regardless of file bytes.
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val file = withContext(Dispatchers.IO) { copyUriToCacheFile(uri, mimeType) } ?: continue
                when (val result = productRepository.uploadImage(id, file, mimeType)) {
                    is Result.Success -> _uiState.update { it.copy(images = result.data.images) }
                    is Result.Error -> Unit
                    Result.Loading -> Unit
                }
            }
            _uiState.update { it.copy(uploadingImage = false) }
        }
    }

    fun onRemoveImage(url: String) {
        val id = productId ?: return
        viewModelScope.launch {
            when (val result = productRepository.removeImage(id, url)) {
                is Result.Success -> _uiState.update { it.copy(images = result.data.images) }
                is Result.Error -> Unit
                Result.Loading -> Unit
            }
        }
    }

    private fun copyUriToCacheFile(uri: Uri, mimeType: String): File? = runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val file = File(context.cacheDir, "product_${System.currentTimeMillis()}_${(0..9999).random()}.$extension")
        input.use { inStream -> FileOutputStream(file).use { out -> inStream.copyTo(out) } }
        file
    }.getOrNull()

    fun save() {
        val state = _uiState.value
        val price = state.priceInr.toDoubleOrNull()
        if (state.segmentId == null || state.name.isBlank()) {
            _uiState.update { it.copy(saveState = ProductFormSaveState.Error("Pick a category and enter a name")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saveState = ProductFormSaveState.Saving) }
            val currentId = productId
            val result = if (currentId == null) {
                productRepository.create(
                    CreateProductRequest(
                        segmentId = state.segmentId,
                        name = state.name,
                        description = state.description.ifBlank { null },
                        priceInr = price,
                        sku = state.sku.ifBlank { null },
                        status = state.status,
                    ),
                )
            } else {
                productRepository.update(
                    currentId,
                    UpdateProductRequest(
                        segmentId = state.segmentId,
                        name = state.name,
                        description = state.description.ifBlank { null },
                        priceInr = price,
                        sku = state.sku.ifBlank { null },
                        status = state.status,
                    ),
                )
            }
            when (result) {
                is Result.Success -> {
                    productId = result.data.id
                    _uiState.update {
                        it.copy(saveState = ProductFormSaveState.Saved, savedProductId = result.data.id, images = result.data.images)
                    }
                }
                is Result.Error -> _uiState.update { it.copy(saveState = ProductFormSaveState.Error(result.message)) }
                Result.Loading -> Unit
            }
        }
    }
}
