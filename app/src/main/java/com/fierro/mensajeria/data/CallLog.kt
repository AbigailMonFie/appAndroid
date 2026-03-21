package com.fierro.mensajeria.data

import com.google.firebase.firestore.PropertyName

data class CallLog(
    val id: String = "",
    val partnerName: String = "",
    val type: String = "AUDIO", // "AUDIO" o "VIDEO"
    val timestamp: Long = 0,
    @get:PropertyName("outgoing")
    @set:PropertyName("outgoing")
    var isOutgoing: Boolean = true
)
