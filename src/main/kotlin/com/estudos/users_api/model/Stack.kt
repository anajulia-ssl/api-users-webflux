package com.estudos.users_api.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.util.UUID

@Table("stacks")
data class Stack(
    @Id
    val id: String? = null,

    val userId: String,
    val name: String,
    val level: Int
)