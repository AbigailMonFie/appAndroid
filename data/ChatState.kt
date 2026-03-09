package com.fierro.mensajeria.data

data class ChatState(
    val partnerId: String = "",
    val isArchived: Boolean = false,
    val isPinned: Boolean = false
)
