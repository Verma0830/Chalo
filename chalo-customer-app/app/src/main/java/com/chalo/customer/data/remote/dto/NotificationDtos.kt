package com.chalo.customer.data.remote.dto

import com.google.gson.annotations.SerializedName

data class NotificationDto(
    @SerializedName("id")        val id: String,
    @SerializedName("type")      val type: String,
    @SerializedName("title")     val title: String,
    @SerializedName("body")      val body: String,
    @SerializedName("isRead")    val isRead: Boolean,
    @SerializedName("data")      val data: Map<String, String>?,
    @SerializedName("createdAt") val createdAt: String,
)

data class UnreadCountDto(
    @SerializedName("count") val count: Int,
)
