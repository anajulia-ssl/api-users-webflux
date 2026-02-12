package com.estudos.users_api.validation.annotation

import com.estudos.users_api.validation.validator.UniqueStackValidator
import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@MustBeDocumented
@Constraint(validatedBy = [UniqueStackValidator::class])
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class UniqueStack(
    val message: String = "stack cannot contain duplicate values",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
