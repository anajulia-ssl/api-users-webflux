package com.estudos.users_api.dto

import jakarta.validation.constraints.*

data class StackRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 32)
    val name: String,

    @field:NotNull
    @field:Min(1)
    @field:Max(10)
    val level: Int
)