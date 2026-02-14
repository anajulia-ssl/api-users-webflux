package com.estudos.users_api.model

import com.estudos.users_api.dto.UserResponse
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDate
import java.util.UUID

@Table("USERS")
data class User(
    @Id
    val id: String? = null,

    val name: String,
    val nick: String?,

    @Column("BIRTH_DATE")
    val birthDate: LocalDate
)

fun User.withStacks(stacks: List<Stack>): UserResponse =
    UserResponse(
        id = id!!,
        name = name,
        nick = nick,
        birthDate = birthDate,
        stack = stacks.map { it.toResponse() }
    )
