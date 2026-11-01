package com.xdl.upward.ui.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdl.upward.data.local.AiApiEntity
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.repository.AiApiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ApiSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val aiApiRepository = AiApiRepository(AppDatabase.getInstance(application).aiApiDao())

    val apis: Flow<List<AiApiEntity>> = aiApiRepository.observeAiApis()

    fun selectApi(id: Long) {
        viewModelScope.launch {
            aiApiRepository.selectApi(id)
        }
    }
}
