package com.statussaver.vault.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.statussaver.vault.model.StatusItem
import com.statussaver.vault.repository.StatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatusViewModel(private val repository: StatusRepository) : ViewModel() {

    private val _images = MutableLiveData<List<StatusItem>>(emptyList())
    val images: LiveData<List<StatusItem>> = _images

    private val _videos = MutableLiveData<List<StatusItem>>(emptyList())
    val videos: LiveData<List<StatusItem>> = _videos

    private val _savedStatuses = MutableLiveData<List<StatusItem>>(emptyList())
    val savedStatuses: LiveData<List<StatusItem>> = _savedStatuses

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _saveEvent = MutableLiveData<SaveEvent?>()
    val saveEvent: LiveData<SaveEvent?> = _saveEvent

    private val _permissionRevoked = MutableLiveData(false)
    val permissionRevoked: LiveData<Boolean> = _permissionRevoked

    fun loadStatuses(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val all = withContext(Dispatchers.IO) { repository.loadStatuses(uri) }
                _images.value = all.filter { !it.isVideo }
                _videos.value = all.filter {  it.isVideo }
            } catch (e: SecurityException) {
                _permissionRevoked.value = true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSavedStatuses() {
        viewModelScope.launch {
            _isLoading.value = true
            val saved = withContext(Dispatchers.IO) { repository.getSavedStatuses() }
            _savedStatuses.value = saved
            _isLoading.value = false
        }
    }

    fun saveStatus(item: StatusItem) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                repository.saveStatus(item.uri, item.name, item.isVideo)
            }
            _saveEvent.value = SaveEvent(ok, item.name, item.isVideo)

            // Refresh lists so isSaved badge updates
            if (ok) {
                val uri = repository.getPersistedUri()
                if (uri != null) loadStatuses(uri)
                loadSavedStatuses()
            }
        }
    }

    fun consumeSaveEvent() {
        _saveEvent.value = null
    }

    fun resetPermissionRevoked() {
        _permissionRevoked.value = false
    }

    data class SaveEvent(val success: Boolean, val fileName: String, val isVideo: Boolean)
}