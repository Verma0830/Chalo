package com.chalo.driver.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    @SerialName("success") val success: Boolean,
    @SerialName("message") val message: String,
    @SerialName("data")    val data: T?,
    @SerialName("meta")    val meta: PaginationMetaDto? = null,
    @SerialName("code")    val code: String? = null,
)

@Serializable
data class PaginationMetaDto(
    @SerialName("page")       val page: Int,
    @SerialName("limit")      val limit: Int,
    @SerialName("total")      val total: Int,
    @SerialName("totalPages") val totalPages: Int,
)
