package com.fierro.mensajeria.data

data class CallLog(
    val id: String = "",
    val partnerName: String = "",
    val type: String = "AUDIO", // "AUDIO" o "VIDEO"
    val timestamp: Long = 0,
    val isOutgoing: Boolean = true
)
