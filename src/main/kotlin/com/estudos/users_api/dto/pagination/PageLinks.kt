package com.estudos.users_api.dto.pagination

data class PageLinks(
    val first: PageLink,
    val self: PageLink,
    val last: PageLink,
    val prev: PageLink? = null,
    val next: PageLink? = null
)
