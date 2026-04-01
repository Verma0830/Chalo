package com.chalo.driver.presentation.screens.kyc

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chalo.driver.domain.repository.DriverRepository
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class DocumentSubmitUiState(
    val licenseNumber: String = "",
    val rcNumber: String = "",
    val aadharNumber: String = "",
    val vehicleNumber: String = "",
    val vehicleModel: String = "",
    // Upload URLs (set after Firebase Storage upload)
    val licenseUrl: String? = null,
    val rcUrl: String? = null,
    val aadharUrl: String? = null,
    val bikePhotoUrl: String? = null,
    // File names for UI
    val licenseFileName: String? = null,
    val rcFileName: String? = null,
    val aadharFileName: String? = null,
    val bikePhotoFileName: String? = null,
    // Upload states
    val isUploadingLicense: Boolean = false,
    val isUploadingRc: Boolean = false,
    val isUploadingAadhar: Boolean = false,
    val isUploadingBikePhoto: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = licenseNumber.isNotBlank() && licenseUrl != null &&
                rcNumber.isNotBlank() && rcUrl != null &&
                aadharNumber.length == 12 && aadharUrl != null &&
                vehicleNumber.isNotBlank() && vehicleModel.isNotBlank() &&
                !isSubmitting && !isUploadingLicense && !isUploadingRc &&
                !isUploadingAadhar && !isUploadingBikePhoto
}

sealed class DocumentSubmitEvent {
    object Submitted : DocumentSubmitEvent()
}

@HiltViewModel
class DocumentSubmitViewModel @Inject constructor(
    private val driverRepository: DriverRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val storage = FirebaseStorage.getInstance()

    private val _uiState = MutableStateFlow(DocumentSubmitUiState())
    val uiState: StateFlow<DocumentSubmitUiState> = _uiState.asStateFlow()

    private val _events = Channel<DocumentSubmitEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onLicenseNumberChanged(v: String) {
        _uiState.update { it.copy(licenseNumber = v, errorMessage = null) }
    }
    fun onRcNumberChanged(v: String) {
        _uiState.update { it.copy(rcNumber = v, errorMessage = null) }
    }
    fun onAadharNumberChanged(v: String) {
        val digits = v.filter { it.isDigit() }.take(12)
        _uiState.update { it.copy(aadharNumber = digits, errorMessage = null) }
    }
    fun onVehicleNumberChanged(v: String) {
        _uiState.update { it.copy(vehicleNumber = v, errorMessage = null) }
    }
    fun onVehicleModelChanged(v: String) {
        _uiState.update { it.copy(vehicleModel = v, errorMessage = null) }
    }

    fun onLicenseImageSelected(uri: Uri) {
        _uiState.update { it.copy(isUploadingLicense = true) }
        uploadDocument(uri, "license") { url, name ->
            _uiState.update { it.copy(licenseUrl = url, licenseFileName = name, isUploadingLicense = false) }
        }
    }

    fun onRcImageSelected(uri: Uri) {
        _uiState.update { it.copy(isUploadingRc = true) }
        uploadDocument(uri, "rc") { url, name ->
            _uiState.update { it.copy(rcUrl = url, rcFileName = name, isUploadingRc = false) }
        }
    }

    fun onAadharImageSelected(uri: Uri) {
        _uiState.update { it.copy(isUploadingAadhar = true) }
        uploadDocument(uri, "aadhar") { url, name ->
            _uiState.update { it.copy(aadharUrl = url, aadharFileName = name, isUploadingAadhar = false) }
        }
    }

    fun onBikePhotoSelected(uri: Uri) {
        _uiState.update { it.copy(isUploadingBikePhoto = true) }
        uploadDocument(uri, "bike_photo") { url, name ->
            _uiState.update { it.copy(bikePhotoUrl = url, bikePhotoFileName = name, isUploadingBikePhoto = false) }
        }
    }

    private fun uploadDocument(uri: Uri, folder: String, onComplete: (String, String) -> Unit) {
        viewModelScope.launch {
            try {
                val fileName = "${UUID.randomUUID()}.jpg"
                val ref = storage.reference.child("driver_docs/$folder/$fileName")
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()
                onComplete(downloadUrl, fileName)
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload $folder document")
                _uiState.update { it.copy(
                    errorMessage = "Failed to upload document: ${e.message}",
                    isUploadingLicense = false,
                    isUploadingRc = false,
                    isUploadingAadhar = false,
                    isUploadingBikePhoto = false,
                ) }
            }
        }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            driverRepository.submitDocuments(
                licenseNumber = state.licenseNumber.trim(),
                licenseUrl = state.licenseUrl!!,
                rcNumber = state.rcNumber.trim(),
                rcUrl = state.rcUrl!!,
                aadharNumber = state.aadharNumber,
                aadharUrl = state.aadharUrl!!,
                vehicleNumber = state.vehicleNumber.trim(),
                vehicleModel = state.vehicleModel.trim(),
                bikePhotoUrl = state.bikePhotoUrl,
            )
                .onSuccess { _events.send(DocumentSubmitEvent.Submitted) }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
            _uiState.update { it.copy(isSubmitting = false) }
        }
    }
}
