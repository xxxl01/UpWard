package com.xdl.upward.data.repository

import com.xdl.upward.data.local.ProjectDao
import com.xdl.upward.data.local.ProjectEntity
import kotlinx.coroutines.flow.Flow
import java.time.OffsetDateTime

class ProjectRepository(
    private val projectDao: ProjectDao
) {
    fun observeProjects(): Flow<List<ProjectEntity>> = projectDao.observeProjects()

    suspend fun getProjects(): List<ProjectEntity> = projectDao.getProjects()

    fun observeProject(id: Long): Flow<ProjectEntity?> = projectDao.observeProject(id)

    suspend fun getProject(id: Long): ProjectEntity? = projectDao.getProject(id)

    suspend fun setNextRequiredMessagePeriod(id: Long, startTime: String, endTime: String) {
        projectDao.updateNextRequiredMessagePeriod(id, startTime, endTime)
    }

    suspend fun saveProject(
        id: Long,
        name: String,
        systemPrompt: String,
        dailyRecordPrompt: String,
        checkInMode: String
    ): Long {
        val now = OffsetDateTime.now().toString()
        return if (id == 0L) {
            projectDao.insert(
                ProjectEntity(
                    name = name,
                    systemPrompt = systemPrompt,
                    dailyRecordPrompt = dailyRecordPrompt,
                    checkInMode = checkInMode,
                    createdAt = now
                )
            )
        } else {
            val oldProject = projectDao.getProject(id) ?: return 0L
            projectDao.update(
                oldProject.copy(
                    name = name,
                    systemPrompt = systemPrompt,
                    dailyRecordPrompt = dailyRecordPrompt
                )
            )
            id
        }
    }
}
