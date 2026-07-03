package com.xdl.upward.ui.navigation

object AppRoute {
    const val PROJECT_LIST = "project_list"
    const val PROJECT_EDIT = "project_edit?projectId={projectId}"
    const val PROJECT_DETAIL = "project_detail/{projectId}"
    const val DAILY_RECORD_LIST = "daily_record_list/{projectId}"
    const val DAILY_RECORD_EDIT = "daily_record_edit/{projectId}?recordId={recordId}"
    const val API_CONFIG_LIST = "api_config_list"
    const val API_CONFIG_EDIT = "api_config_edit?apiId={apiId}"

    fun projectEdit(projectId: Long = 0L): String = "project_edit?projectId=$projectId"

    fun projectDetail(projectId: Long): String = "project_detail/$projectId"

    fun dailyRecordList(projectId: Long): String = "daily_record_list/$projectId"

    fun dailyRecordEdit(projectId: Long, recordId: Long = 0L): String {
        return "daily_record_edit/$projectId?recordId=$recordId"
    }

    fun apiConfigEdit(apiId: Long = 0L): String = "api_config_edit?apiId=$apiId"
}
