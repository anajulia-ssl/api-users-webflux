package com.estudos.users_api.dto

import java.time.LocalDate

data class UserResponse(
    val id: String,
    val name: String,
    val nick: String?,
    val birthDate: LocalDate,
    val stack: List<StackResponse>
)