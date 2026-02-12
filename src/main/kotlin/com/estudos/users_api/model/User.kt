package com.estudos.users_api.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDate
import java.util.UUID

@Table("users")
data class User(
    @Id
    val id: String? = null,

    val name: String,
    val nick: String?,
    val birthDate: LocalDate
)