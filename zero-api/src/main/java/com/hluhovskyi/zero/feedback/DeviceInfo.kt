package com.hluhovskyi.zero.feedback

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdkInt: Int,
    val versionName: String,
    val versionCode: Long,
)
