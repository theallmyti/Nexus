package com.pcmonitor.app.data.model

data class AppUpdateInfo(
    val updateAvailable: Boolean = false,
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val isMandatory: Boolean = false
)
