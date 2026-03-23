package com.fierro.mensajeria.data

data class CallInfo(
    val callerId: String = "",
    val callerName: String = "",
    val callerProfilePicUrl: String? = null,
    val receiverId: String = "",
    val type: String = "AUDIO", // "AUDIO" o "VIDEO"
    val status: String = "IDLE" // "IDLE", "RINGING", "ONGOING", "ENDED"
)
