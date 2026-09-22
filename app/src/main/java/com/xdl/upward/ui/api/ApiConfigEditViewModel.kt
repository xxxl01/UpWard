package com.xdl.upward.ui.api

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.data.repository.AiApiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ApiConfigEditViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AiApiRepository(
        AppDatabase.getInstance(application).aiApiDao()
    )

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _model = MutableStateFlow("")
    val model: StateFlow<String> = _model.asStateFlow()

    private val _temperature = MutableStateFlow("0.7")
    val temperature: StateFlow<String> = _temperature.asStateFlow()

    private val _supportImage = MutableStateFlow(false)
    val supportImage: StateFlow<Boolean> = _supportImage.asStateFlow()

    private val _visionApiId = MutableStateFlow(0L)
    val visionApiId: StateFlow<Long> = _visionApiId.asStateFlow()

    // 可选的视觉辅助模型列表：所有支持图片输入的其他 API
    private val _visionApiOptions = MutableStateFlow<List<AiApiEntity>>(emptyList())
    val visionApiOptions: StateFlow<List<AiApiEntity>> = _visionApiOptions.asStateFlow()

    private val _selected = MutableStateFlow(false)
    val selected: StateFlow<Boolean> = _selected.asStateFlow()

    private var loadedApiId = 0L

    init {
        viewModelScope.launch {
            repository.observeAiApis().collect { apis ->
                _visionApiOptions.value = apis.filter { it.id != loadedApiId && it.supportImage }
            }
        }
    }

    fun loadApi(apiId: Long) {
        if (apiId == 0L || loadedApiId == apiId) return
        loadedApiId = apiId
        viewModelScope.launch {
            val api = repository.getById(apiId) ?: return@launch
            _baseUrl.value = api.baseUrl
            _apiKey.value = api.apiKey
            _model.value = api.model
            _temperature.value = api.temperature.toString()
            _supportImage.value = api.supportImage
            _visionApiId.value = api.visionApiId
            _selected.value = api.selected
        }
    }

    fun updateBaseUrl(value: String) {
        _baseUrl.value = value
    }

    fun updateApiKey(value: String) {
        _apiKey.value = value
    }

    fun updateModel(value: String) {
        _model.value = value
    }

    fun updateTemperature(value: String) {
        _temperature.value = value
    }

    fun toggleSelected() {
        _selected.value = !_selected.value
    }

    fun toggleSupportImage() {
        _supportImage.value = !_supportImage.value
    }

    fun updateSupportImage(value: Boolean) {
        _supportImage.value = value
        if (value) {
            // 自己已支持图片时，不再需要视觉辅助模型
            _visionApiId.value = 0L
        }
    }

    fun updateVisionApiId(value: Long) {
        _visionApiId.value = value
    }

    fun save(apiId: Long, onSaved: () -> Unit) {
        val finalBaseUrl = _baseUrl.value.trim()
        val finalApiKey = _apiKey.value.trim()
        val finalModel = _model.value.trim()
        val finalTemperature = _temperature.value.trim().toDoubleOrNull() ?: return
        if (finalBaseUrl.isEmpty() || finalModel.isEmpty()) return

        viewModelScope.launch {
            repository.saveApi(apiId, finalBaseUrl, finalApiKey, finalModel, finalTemperature, _supportImage.value, _visionApiId.value, _selected.value)
            onSaved()
        }
    }
}
