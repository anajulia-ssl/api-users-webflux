package com.estudos.users_api.controller

import com.estudos.users_api.dto.UserRequest
import com.estudos.users_api.dto.UserResponse
import com.estudos.users_api.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    @PostMapping
    fun create(
        @Valid @RequestBody body: UserRequest,
        uriBuilder: UriComponentsBuilder
    ): Mono<ResponseEntity<UserResponse>> =
        userService.create(body)
            .map { resp ->
                val location = uriBuilder.path("/users/{id}").buildAndExpand(resp.id).toUri()
                ResponseEntity.created(location).body(resp)
            }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: String): Mono<UserResponse> =
        userService.findById(id)

    @GetMapping
    fun findAll(): Flux<UserResponse> =
        userService.findAll()

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @Valid @RequestBody body: UserRequest): Mono<UserResponse> =
        userService.update(id, body)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String): Mono<Void> =
        userService.delete(id)
}
