package com.chalo.customer.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Generic wrapper that matches the backend's ApiResponse shape */
data class ApiResponse<T>(
    @SerializedName("success")    val success: Boolean,
    @SerializedName("message")    val message: String,
    @SerializedName("data")       val data: T?,
    @SerializedName("meta")       val meta: PaginationMetaDto?,
    @SerializedName("code")       val code: String?,
)

data class PaginationMetaDto(
    @SerializedName("page")       val page: Int,
    @SerializedName("limit")      val limit: Int,
    @SerializedName("total")      val total: Int,
    @SerializedName("totalPages") val totalPages: Int,
)
