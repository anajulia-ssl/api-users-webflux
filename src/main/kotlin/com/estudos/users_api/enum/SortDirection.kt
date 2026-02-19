package com.estudos.users_api.enum

import org.springframework.data.domain.Sort

enum class SortDirection(val springDirection: Sort.Direction) {
    ASC(Sort.Direction.ASC),
    DESC(Sort.Direction.DESC);

    companion object {
        fun from(input: String): SortDirection {
            return entries.firstOrNull { it.name.equals(input, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid direction: $input")
        }
    }
}