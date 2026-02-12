package com.estudos.users_api.validation.validator

import com.estudos.users_api.dto.StackRequest
import com.estudos.users_api.validation.annotation.UniqueStack
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class UniqueStackValidator : ConstraintValidator<UniqueStack, List<StackRequest>> {
    override fun isValid(value: List<StackRequest>?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val names = value.map { it.name.lowercase() }
        return names.size == names.toSet().size
    }
}
