package com.estudos.users_api.model

import com.estudos.users_api.annotation.Sortable
import com.estudos.users_api.dto.UserResponse
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDate

@Table("USERS")
data class User(
    @Id
    val id: String? = null,

    @Sortable
    val name: String,

    @Sortable
    val nick: String?,

    @Column("BIRTH_DATE")
    @Sortable(external = "birth_date")
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
