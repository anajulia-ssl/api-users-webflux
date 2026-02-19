package com.estudos.users_api.dto.pagination

data class PageResult(
    val limit: Int,
    val offset: Int,
    val total: Long,
    val size: Int
)
