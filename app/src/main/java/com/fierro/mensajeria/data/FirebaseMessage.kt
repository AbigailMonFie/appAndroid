package com.fierro.mensajeria.data

import com.google.firebase.firestore.DocumentId

data class FirebaseMessage(
    @DocumentId
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = 0,
    val read: Boolean = false,
    val reactions: Map<String, String> = emptyMap()
)
