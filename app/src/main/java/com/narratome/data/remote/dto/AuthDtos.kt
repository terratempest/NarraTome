package com.narratome.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponseDto(
    val user: UserDto? = null,
)

@Serializable
data class UserDto(
    val id: String? = null,
    val username: String? = null,
    val token: String? = null,
)

@Serializable
data class PingResponseDto(
    val success: Boolean? = null,
)

@Serializable
data class StatusResponseDto(
    @SerialName("isInit") val isInit: Boolean? = null,
)
