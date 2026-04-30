package com.chalo.customer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    @SerialName("id")        val id: String,
    @SerialName("type")      val type: String,
    @SerialName("title")     val title: String,
    @SerialName("body")      val body: String,
    @SerialName("isRead")    val isRead: Boolean,
    @SerialName("data")      val data: Map<String, String>?,
    @SerialName("createdAt") val createdAt: String,
)

@Serializable
data class UnreadCountDto(
    @SerialName("count") val count: Int,
)
