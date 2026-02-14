package com.estudos.users_api.model

import com.estudos.users_api.dto.StackResponse
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("STACKS")
data class Stack(
    @Id
    val id: String? = null,

    @Column("USER_ID")
    val userId: String?,

    val name: String,

    @Column("SKILL_LEVEL")
    val level: Int
)

fun Stack.toResponse() = StackResponse(
    name = name,
    level = level
)
