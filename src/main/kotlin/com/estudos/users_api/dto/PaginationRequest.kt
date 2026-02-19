package com.estudos.users_api.dto

import com.estudos.users_api.annotation.Sortable
import com.estudos.users_api.enum.SortDirection
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

data class PageQuery(
    @field:Min(0) val offset: Int = 0,
    @field:Min(1) val limit: Int = 20,
    val sort: String? = null
)

private fun <T : Any> getAliases(entityClass: KClass<T>): Map<String, String> =
    entityClass.memberProperties.mapNotNull { prop ->
        prop.annotations.filterIsInstance<Sortable>().firstOrNull()?.let { ann ->
            val external = ann.external.ifBlank { prop.name }
            external to prop.name
        }
    }.toMap()

fun <T : Any> PageQuery.toPageable(entityClass: KClass<T>): Pageable {
    val page = offset / limit
    val aliases = getAliases(entityClass)

    if (sort.isNullOrBlank()) {
        val defaultProp = aliases["name"] ?: aliases.values.firstOrNull() ?: "id"
        return PageRequest.of(page, limit, Sort.by(Sort.Order.asc(defaultProp)))
    }

    val orders = sort.split(",").map { item ->
        val (external, direction) = item.split(":", limit = 2)
        val internal = aliases[external]
            ?: throw IllegalArgumentException("Field not allowed: $external")
        val dir = SortDirection.from(direction)
        Sort.Order(dir.springDirection, internal)
    }

    return PageRequest.of(page, limit, Sort.by(orders))
}