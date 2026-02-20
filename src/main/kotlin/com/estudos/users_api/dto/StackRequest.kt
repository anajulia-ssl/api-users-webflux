package com.estudos.users_api.dto

import com.estudos.users_api.model.Stack
import jakarta.validation.constraints.*
import org.hibernate.validator.constraints.Range
import java.util.UUID

data class StackRequest(
    @field:NotBlank(message = "stack item name must not be blank")
    @field:Size(max = 32, message = "stack item name size must be less than or equal to 32")
    val name: String,

    @field:Range(min = 1, max = 10, message = "stack item level must be between 1 and 10")
    val level: Int
)

fun StackRequest.toModel(userId: String?) =
    Stack(
        id = UUID.randomUUID().toString(),
        userId = userId,
        name = name.trim(),
        level = level
    )