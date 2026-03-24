package com.fierro.mensajeria.data

data class Group(
    val id: String = "",
    val name: String = "",
    val members: List<String> = emptyList(),
    val adminId: String = "",
    val profilePicUrl: String? = null
)
