package com.estudos.users_api.exception

import com.estudos.users_api.dto.ErrorResponse
import jakarta.validation.ConstraintViolationException
import org.springframework.beans.TypeMismatchException
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.MethodNotAllowedException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.UnsupportedMediaTypeStatusException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException::class)
    fun userNotFound(ex: UserNotFoundException): ResponseEntity<List<ErrorResponse>> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(listOf(ErrorResponse("not_found_exception", ex.message ?: "Resource not found")))

    @ExceptionHandler(NickAlreadyExistsException::class)
    fun nickAlreadyExists(ex: NickAlreadyExistsException): ResponseEntity<List<ErrorResponse>> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(listOf(ErrorResponse("conflict_exception", ex.message ?: "Conflict")))

    @ExceptionHandler(InvalidStackException::class)
    fun businessError(ex: InvalidStackException): ResponseEntity<List<ErrorResponse>> =
        ResponseEntity
            .badRequest()
            .body(listOf(ErrorResponse("parameter_exception", ex.message ?: "Invalid parameters")))

    @ExceptionHandler(
        DecodingException::class,
        ServerWebInputException::class,
        HttpMessageNotReadableException::class
    )
    fun invalidJson(ex: Exception, exchange: ServerWebExchange): ResponseEntity<List<ErrorResponse>> {
        val raw = ex.cause?.message ?: ex.message ?: ""

        if (raw.contains("JsonParseException", true) ||
            raw.contains("JSON parse error", true) ||
            raw.contains("Unexpected character", true) ||
            raw.contains("Failed to read HTTP message", true)
        ) {
            return ResponseEntity.badRequest()
                .body(listOf(ErrorResponse("parameter_exception", "Request body is malformed")))
        }

        var root: Throwable = ex
        while (root.cause != null) root = root.cause!!
        val rootName = root.javaClass.name
        val rootMsg = (root.message ?: "") + " | raw=$raw"

        if (rootName.contains("DateTimeParseException")) {
            return ResponseEntity.badRequest()
                .body(listOf(ErrorResponse("validation_exception", "Invalid request")))
        }

        if (rootName.contains("InvalidFormatException") || rootName.contains("MismatchedInputException")) {
            if (rootMsg.contains("Cannot map `null` into type `int`", true)) {
                return ResponseEntity.badRequest()
                    .body(listOf(ErrorResponse("validation_exception", "stack item level must not be null")))
            }
            return ResponseEntity.badRequest()
                .body(listOf(ErrorResponse("validation_exception", "Invalid request")))
        }

        if (rootName.contains("NullPointerException") && raw.contains("Parameter specified as non-null is null")) {
            val param = raw.substringAfter("parameter ").substringBefore('\n').trim()
            val error = when {
                param.equals("birthDate", true) || param.equals("birth_date", true) ->
                    ErrorResponse("validation_exception", "birth date must not be null")
                param.equals("stack", true) ->
                    ErrorResponse("validation_exception", "stack must not be null")
                (param.equals("name", true) && raw.contains("StackRequest")) ->
                    ErrorResponse("validation_exception", "stack item name must not be null")
                (param.equals("level", true) && raw.contains("StackRequest")) ->
                    ErrorResponse("validation_exception", "stack item level must not be null")
                else ->
                    ErrorResponse("validation_exception", "Invalid request")
            }
            return ResponseEntity.badRequest().body(listOf(error))
        }

        return ResponseEntity.badRequest()
            .body(listOf(ErrorResponse("parameter_exception", "Request could not be processed")))
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun validationError(ex: WebExchangeBindException): ResponseEntity<List<ErrorResponse>> {
        val isPaginationError =
            (ex.bindingResult.target?.javaClass?.simpleName == "PageQuery")
                    || ex.bindingResult.fieldErrors.any { fe ->
                fe.field.equals("offset", ignoreCase = true) || fe.field.equals("limit", ignoreCase = true)
            }

        if (isPaginationError) {
            return ResponseEntity.badRequest()
                .body(listOf(ErrorResponse("invalid_pagination", "Invalid pagination parameters")))
        }

        val fieldErrors = ex.bindingResult.fieldErrors.map {
            ErrorResponse("validation_exception", it.defaultMessage ?: "Invalid field")
        }
        val globalErrors = ex.bindingResult.globalErrors.map {
            ErrorResponse("validation_exception", it.defaultMessage ?: "Invalid request")
        }
        val all = (fieldErrors + globalErrors).ifEmpty {
            listOf(ErrorResponse("validation_exception", "Invalid request"))
        }
        return ResponseEntity.badRequest().body(all)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraintViolation(ex: ConstraintViolationException): ResponseEntity<List<ErrorResponse>> {
        val isPaginationError = ex.constraintViolations.any { v ->
            val path = v.propertyPath?.toString() ?: ""
            path.endsWith(".offset") || path.endsWith(".limit") || path == "offset" || path == "limit"
        }

        if (isPaginationError) {
            return ResponseEntity.badRequest()
                .body(listOf(ErrorResponse("invalid_pagination", "Invalid pagination parameters")))
        }

        val errors = ex.constraintViolations.map {
            ErrorResponse("validation_exception", "${it.propertyPath}: ${it.message}")
        }.ifEmpty {
            listOf(ErrorResponse("validation_exception", "Invalid request parameters"))
        }
        return ResponseEntity.badRequest().body(errors)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(ex: IllegalArgumentException): ResponseEntity<List<ErrorResponse>> {
        val msg = ex.message ?: ""
        val isSortError = msg.contains("Field not allowed", true) ||
                msg.contains("Invalid direction", true)

        return if (isSortError) {
            ResponseEntity.badRequest()
                .body(listOf(ErrorResponse("invalid_sort", "Invalid sorting parameters")))
        } else {
            ResponseEntity.badRequest()
                .body(listOf(ErrorResponse("parameter_exception", msg.ifBlank { "Invalid parameters" })))
        }
    }

    @ExceptionHandler(TypeMismatchException::class)
    fun typeMismatch(ex: TypeMismatchException): ResponseEntity<List<ErrorResponse>> =
        ResponseEntity
            .badRequest()
            .body(
                listOf(
                    ErrorResponse(
                        "parameter_exception",
                        "Parameter '${ex.propertyName ?: "parameter"}' must be of type ${ex.requiredType?.simpleName ?: "type"}"
                    )
                )
            )

    @ExceptionHandler(MethodNotAllowedException::class)
    fun methodNotAllowed(): ResponseEntity<List<ErrorResponse>> =
        ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(listOf(ErrorResponse("method_not_allowed", "HTTP method not supported")))

    @ExceptionHandler(UnsupportedMediaTypeStatusException::class)
    fun mediaTypeNotSupported(): ResponseEntity<List<ErrorResponse>> =
        ResponseEntity
            .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(listOf(ErrorResponse("unsupported_media_type", "Content-Type not supported")))

    @ExceptionHandler(Exception::class)
    fun genericError(ex: Exception): ResponseEntity<List<ErrorResponse>> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(listOf(ErrorResponse("internal_exception", "Unexpected error occurred")))
    }
}