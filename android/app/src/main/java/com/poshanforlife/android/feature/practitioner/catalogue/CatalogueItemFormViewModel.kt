package com.poshanforlife.android.feature.practitioner.catalogue

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.CatalogueRepository
import com.poshanforlife.android.core.network.CatalogueType
import com.poshanforlife.android.core.network.CreateCatalogueItemRequest
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateCatalogueItemRequest
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

sealed class CatalogueFormSaveState {
    data object Idle : CatalogueFormSaveState()
    data object Saving : CatalogueFormSaveState()
    data class Success(val id: String) : CatalogueFormSaveState()
    data class Error(val message: String) : CatalogueFormSaveState()
}

data class CatalogueFormUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val name: String = "",
    val serviceCode: String = "",
    val priceInr: String = "",
    val description: String = "",
    val coverImageUrl: String? = null,
    val status: String = "draft",
    val durationWeeks: String = "",
    val durationMinutes: String = "",
    val durationDays: String = "",
    val goalDescription: String = "",
    val uploadingImage: Boolean = false,
    val saveState: CatalogueFormSaveState = CatalogueFormSaveState.Idle,
)

/** Create when itemId is absent from the route, edit when present — same dual-purpose convention as the rest of the app. */
@HiltViewModel
class CatalogueItemFormViewModel @Inject constructor(
    private val catalogueRepository: CatalogueRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val type: CatalogueType = CatalogueType.fromPathSegment(checkNotNull(savedStateHandle["type"]))
    private val itemId: String? = savedStateHandle["itemId"]
    val isEditing: Boolean get() = itemId != null

    private val _uiState = MutableStateFlow(CatalogueFormUiState())
    val uiState: StateFlow<CatalogueFormUiState> = _uiState.asStateFlow()

    init {
        if (itemId != null) load() else _uiState.update { it.copy(loading = false) }
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = catalogueRepository.get(type.pathSegment, itemId!!)) {
                is Result.Success -> {
                    val item = result.data
                    _uiState.update {
                        it.copy(
                            loading = false,
                            name = item.name,
                            serviceCode = item.serviceCode.orEmpty(),
                            priceInr = item.priceInr?.toString().orEmpty(),
                            description = item.description.orEmpty(),
                            coverImageUrl = item.coverImageUrl,
                            status = item.status,
                            durationWeeks = item.durationWeeks?.toString().orEmpty(),
                            durationMinutes = item.durationMinutes?.toString().orEmpty(),
                            durationDays = item.durationDays?.toString().orEmpty(),
                            goalDescription = item.goalDescription.orEmpty(),
                        )
                    }
                }
                is Result.Error -> _uiState.update { it.copy(loading = false, loadError = result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun onNameChange(value: String) = updateState { it.copy(name = value) }
    fun onServiceCodeChange(value: String) = updateState { it.copy(serviceCode = value) }
    fun onPriceChange(value: String) = updateState { it.copy(priceInr = value) }
    fun onDescriptionChange(value: String) = updateState { it.copy(description = value) }
    fun onStatusChange(value: String) = updateState { it.copy(status = value) }
    fun onDurationWeeksChange(value: String) = updateState { it.copy(durationWeeks = value) }
    fun onDurationMinutesChange(value: String) = updateState { it.copy(durationMinutes = value) }
    fun onDurationDaysChange(value: String) = updateState { it.copy(durationDays = value) }
    fun onGoalDescriptionChange(value: String) = updateState { it.copy(goalDescription = value) }

    private fun updateState(transform: (CatalogueFormUiState) -> CatalogueFormUiState) {
        _uiState.update(transform)
    }

    fun onCoverImagePicked(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploadingImage = true) }
            // Backend validates the multipart Content-Type against an exact allowlist
            // (image/jpeg|png|webp|gif) — must be the real resolved MIME type, not a
            // wildcard like "image/*", or the upload 422s regardless of file bytes.
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val file = withContext(Dispatchers.IO) { copyUriToCacheFile(uri, mimeType) }
            if (file == null) {
                _uiState.update { it.copy(uploadingImage = false) }
                return@launch
            }
            when (val result = catalogueRepository.uploadCoverImage(type.pathSegment, file, mimeType)) {
                is Result.Success -> _uiState.update { it.copy(uploadingImage = false, coverImageUrl = result.data) }
                is Result.Error -> _uiState.update { it.copy(uploadingImage = false) }
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
        val file = File(context.cacheDir, "cover_${System.currentTimeMillis()}.$extension")
        input.use { inStream -> FileOutputStream(file).use { out -> inStream.copyTo(out) } }
        file
    }.getOrNull()

    fun save() {
        val state = _uiState.value
        val price = state.priceInr.toDoubleOrNull()
        if (state.name.isBlank() || state.serviceCode.isBlank() || price == null) {
            _uiState.update {
                it.copy(saveState = CatalogueFormSaveState.Error("Fill in name, service code, and a valid price"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saveState = CatalogueFormSaveState.Saving) }
            val durationWeeks = if (type == CatalogueType.PROGRAMME) state.durationWeeks.toIntOrNull() else null
            val durationMinutes = if (type == CatalogueType.SESSION) state.durationMinutes.toIntOrNull() else null
            val durationDays = if (type == CatalogueType.CHALLENGE) state.durationDays.toIntOrNull() else null
            val goalDescription = if (type == CatalogueType.CHALLENGE) state.goalDescription else null

            val result = if (isEditing) {
                catalogueRepository.update(
                    type.pathSegment,
                    itemId!!,
                    UpdateCatalogueItemRequest(
                        name = state.name,
                        serviceCode = state.serviceCode,
                        priceInr = price,
                        description = state.description,
                        coverImageUrl = state.coverImageUrl,
                        status = state.status,
                        durationWeeks = durationWeeks,
                        durationMinutes = durationMinutes,
                        durationDays = durationDays,
                        goalDescription = goalDescription,
                    ),
                )
            } else {
                catalogueRepository.create(
                    type.pathSegment,
                    CreateCatalogueItemRequest(
                        name = state.name,
                        serviceCode = state.serviceCode,
                        type = type.wireLabel,
                        priceInr = price,
                        description = state.description,
                        coverImageUrl = state.coverImageUrl,
                        status = state.status,
                        durationWeeks = durationWeeks,
                        durationMinutes = durationMinutes,
                        durationDays = durationDays,
                        goalDescription = goalDescription,
                    ),
                )
            }
            when (result) {
                is Result.Success -> _uiState.update { it.copy(saveState = CatalogueFormSaveState.Success(result.data.id)) }
                is Result.Error -> _uiState.update { it.copy(saveState = CatalogueFormSaveState.Error(result.message)) }
                Result.Loading -> Unit
            }
        }
    }
}
