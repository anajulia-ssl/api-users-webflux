package com.estudos.users_api.dto

import com.estudos.users_api.model.Stack
import com.estudos.users_api.model.User
import com.estudos.users_api.validation.annotation.UniqueStack
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.LocalDate
import java.util.UUID

data class UserRequest(
    @field:NotBlank(message = "name must not be blank")
    @field:Size(min = 3, max = 255, message = "name size must be between 3 and 255")
    val name: String,

    @field:Size(min = 1, max = 255, message = "nick size must be between 1 and 255")
    val nick: String?,

    @field:Past(message = "birth date must be a past date")
    val birthDate: LocalDate,

    @field:Size(min = 1, message = "stack must contain at least 1 element")
    @field:Size(max = 100, message = "stack must contain a maximum of 100 elements")
    @field:UniqueStack
    @field:Valid
    val stack: List<StackRequest>
)

fun UserRequest.toEntity(): User =
    User(
        id = UUID.randomUUID().toString(),
        name = name.trim(),
        nick = nick?.trim(),
        birthDate = birthDate
    )

fun UserRequest.toStacks(userId: String?): List<Stack> =
    stack.map { it.toModel(userId) }
