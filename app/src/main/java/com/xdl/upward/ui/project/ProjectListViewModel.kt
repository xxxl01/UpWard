package com.xdl.upward.ui.project

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ProjectEntity
import com.xdl.upward.data.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow

class ProjectListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProjectRepository(
        AppDatabase.getInstance(application).projectDao()
    )

    val projects: Flow<List<ProjectEntity>> = repository.observeProjects()
}
