package com.estudos.users_api.dto

import jakarta.validation.constraints.*
import java.time.LocalDate

data class UserRequest(
    @field:NotBlank @field:Size(min = 3, max = 255)
    val name: String,

    @field:Size(min = 1, max = 255)
    val nick: String?,

    @field:NotNull
    val birthDate: LocalDate,

    @field:NotNull
    val stack: List<@NotNull StackRequest>
)