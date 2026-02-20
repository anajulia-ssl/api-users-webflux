package com.estudos.users_api.service

import com.estudos.users_api.dto.pagination.PageQuery
import com.estudos.users_api.dto.StackResponse
import com.estudos.users_api.dto.UserRequest
import com.estudos.users_api.dto.UserResponse
import com.estudos.users_api.dto.pagination.PageResponse
import com.estudos.users_api.dto.pagination.Paginator
import com.estudos.users_api.dto.toEntity
import com.estudos.users_api.dto.pagination.toPageable
import com.estudos.users_api.dto.toStacks
import com.estudos.users_api.exception.NickAlreadyExistsException
import com.estudos.users_api.exception.UserNotFoundException
import com.estudos.users_api.model.Stack
import com.estudos.users_api.model.User
import com.estudos.users_api.model.toResponse
import com.estudos.users_api.model.withStacks
import com.estudos.users_api.repository.UserRepository
import com.estudos.users_api.repository.StackRepository
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class UserService(
    private val template: R2dbcEntityTemplate,
    private val userRepository: UserRepository,
    private val stackRepository: StackRepository
) {

    private fun validate(nick: String?, excludeId: String? = null): Mono<Void> {
        if (nick.isNullOrBlank()) return Mono.empty()
        val nickTrim = nick.trim()
        return userRepository.findByNickExcludingId(nickTrim, excludeId)
            .flatMap<User> { Mono.error(NickAlreadyExistsException(nickTrim)) }
            .then()
    }


    fun create(request: UserRequest): Mono<UserResponse> {
        val user = request.toEntity()

        return validate(user.nick)
            .then(Mono.defer { template.insert(User::class.java).using(user) })
            .flatMap { savedUser ->
                val stacks = request.toStacks(savedUser.id)
                Flux.fromIterable(stacks)
                    .concatMap { stack -> Mono.defer { template.insert(Stack::class.java).using(stack) } }
                    .collectList()
                    .map { savedStacks -> savedUser.withStacks(savedStacks) }
            }
    }


    fun findById(id: String): Mono<UserResponse> =
        userRepository.findById(id)
            .switchIfEmpty(Mono.error(UserNotFoundException(id)))
            .flatMap { user ->
                stackRepository.findByUserId(user.id).collectList()
                    .map { stacks -> user.withStacks(stacks) }
            }


    fun findAll(query: PageQuery, request: ServerHttpRequest): Mono<PageResponse<UserResponse>> {
        val pageable = query.toPageable(User::class)

        val content: Mono<List<UserResponse>> =
            userRepository.findAllBy(pageable)
                .flatMap { u ->
                    stackRepository.findByUserId(u.id).collectList()
                        .map { stacks -> u.withStacks(stacks) }
                }
                .collectList()

        val total = userRepository.count()

        return Paginator.build(
            query = query,
            contentMono = content,
            totalMono = total,
            request = request,
            mapper = { it }
        )
    }

    fun findStacksByUserId(userId: String): Flux<StackResponse> =
        userRepository.existsById(userId)
            .flatMapMany { exists ->
                if (!exists) Flux.error(UserNotFoundException(userId))
                else stackRepository.findByUserId(userId).map { it.toResponse() }
            }

    fun update(id: String, request: UserRequest): Mono<UserResponse> =
        userRepository.findById(id)
            .switchIfEmpty(Mono.error(UserNotFoundException(id)))
            .flatMap { existing ->
                val toSave = existing.copy(
                    name = request.name.trim(),
                    nick = request.nick?.trim(),
                    birthDate = request.birthDate
                )
                validate(toSave.nick, existing.id)
                    .then(userRepository.save(toSave))
            }
            .flatMap { u ->
                stackRepository.deleteByUserId(u.id)
                    .thenMany(
                        Flux.fromIterable(request.toStacks(u.id))
                            .concatMap { stack -> Mono.defer { template.insert(Stack::class.java).using(stack) } }
                    )
                    .collectList()
                    .map { stacks -> u.withStacks(stacks) }
            }



    fun delete(id: String): Mono<Void> =
        userRepository.findById(id)
            .switchIfEmpty(Mono.error(UserNotFoundException(id)))
            .flatMap { u ->
                stackRepository.deleteByUserId(u.id)
                    .then(userRepository.deleteById(u.id!!))
            }

}