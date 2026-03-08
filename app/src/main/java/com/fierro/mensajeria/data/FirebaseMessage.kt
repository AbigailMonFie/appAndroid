package com.fierro.mensajeria.data

data class FirebaseMessage(
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = 0
)
