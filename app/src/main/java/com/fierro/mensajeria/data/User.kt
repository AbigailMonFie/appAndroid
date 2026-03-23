package com.fierro.mensajeria.data

import com.google.firebase.firestore.PropertyName

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val profilePicUrl: String? = null,
    val fcmToken: String? = null,
    val typingTo: String? = null,
    @get:PropertyName("online")
    @set:PropertyName("online")
    var online: Boolean = false,
    val lastSeen: Long = 0
)
