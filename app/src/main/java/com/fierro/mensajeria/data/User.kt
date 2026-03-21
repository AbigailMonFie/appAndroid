package com.fierro.mensajeria.data

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val profilePicUrl: String? = null,
    val fcmToken: String? = null
)
