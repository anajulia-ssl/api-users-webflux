package com.estudos.users_api.controller

import com.estudos.users_api.dto.pagination.PageQuery
import com.estudos.users_api.dto.StackResponse
import com.estudos.users_api.dto.UserRequest
import com.estudos.users_api.dto.UserResponse
import com.estudos.users_api.dto.pagination.PageResponse
import com.estudos.users_api.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    @PostMapping
    fun create(@Valid @RequestBody body: UserRequest): Mono<ResponseEntity<UserResponse>> {
        return userService.create(body)
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }
    }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: String): Mono<ResponseEntity<UserResponse>> {
        return userService.findById(id)
            .map { ResponseEntity.ok(it) }
    }

    @GetMapping
    fun findAll(@Valid query: PageQuery, request: ServerHttpRequest): Mono<ResponseEntity<PageResponse<UserResponse>>> {
        return userService.findAll(query, request)
            .map { ResponseEntity.ok(it) }
    }

    @GetMapping("/{id}/stacks")
    fun findStacksByUserId(@PathVariable id: String): Mono<ResponseEntity<List<StackResponse>>> {
        return userService.findStacksByUserId(id)
            .collectList()
            .map { ResponseEntity.ok(it) }
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @Valid @RequestBody body: UserRequest): Mono<ResponseEntity<UserResponse>> {
        return userService.update(id, body)
            .map { ResponseEntity.ok(it) }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): Mono<ResponseEntity<Void>> {
        return userService.delete(id)
            .thenReturn(ResponseEntity.noContent().build())
    }

}