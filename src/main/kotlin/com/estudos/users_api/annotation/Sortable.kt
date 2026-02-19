package com.estudos.users_api.annotation

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Sortable(
    val external: String = ""
)