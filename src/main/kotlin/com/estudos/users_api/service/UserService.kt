package com.estudos.users_api.service

import com.estudos.users_api.dto.StackResponse
import com.estudos.users_api.dto.UserRequest
import com.estudos.users_api.dto.UserResponse
import com.estudos.users_api.dto.toModel
import com.estudos.users_api.exception.NickAlreadyExistsException
import com.estudos.users_api.exception.UserNotFoundException
import com.estudos.users_api.model.Stack
import com.estudos.users_api.model.User
import com.estudos.users_api.model.toResponse
import com.estudos.users_api.model.withStacks
import com.estudos.users_api.repository.UserRepository
import com.estudos.users_api.repository.UserStackRepository
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Service
class UserService(
    private val template: R2dbcEntityTemplate,
    private val userRepository: UserRepository,
    private val userStackRepository: UserStackRepository
) {

    private fun validate(nick: String?, excludeId: String? = null): Mono<Void> {
        if (nick.isNullOrBlank()) return Mono.empty()
        val norm = nick.trim()
        return userRepository.findByNickExcludingId(norm, excludeId)
            .flatMap<User> { Mono.error(NickAlreadyExistsException(norm)) }
            .then()
    }

    fun create(req: UserRequest): Mono<UserResponse> {
        val user = User(
            id = UUID.randomUUID().toString(),
            name = req.name.trim(),
            nick = req.nick?.trim(),
            birthDate = req.birthDate
        )

        return validate(user.nick)
            .then(Mono.defer { template.insert(User::class.java).using(user) })
            .flatMap { savedUser ->
                val stacks = req.stack.map { it.toModel(savedUser.id) }
                Flux.fromIterable(stacks)
                    .concatMap { s -> Mono.defer { template.insert(Stack::class.java).using(s) } }
                    .collectList()
                    .map { savedStacks -> savedUser.withStacks(savedStacks) }
            }
    }

    fun findById(id: String): Mono<UserResponse> =
        userRepository.findById(id)
            .switchIfEmpty(Mono.error(UserNotFoundException(id)))
            .flatMap { u ->
                userStackRepository.findByUserId(u.id).collectList()
                    .map { stacks -> u.withStacks(stacks) }
            }

    fun findAll(): Flux<UserResponse> =
        userRepository.findAll()
            .flatMap { u ->
                userStackRepository.findByUserId(u.id).collectList()
                    .map { stacks -> u.withStacks(stacks) }
            }

    fun update(id: String, req: UserRequest): Mono<UserResponse> =
        userRepository.findById(id)
            .switchIfEmpty(Mono.error(UserNotFoundException(id)))
            .flatMap { existing ->
                val toSave = existing.copy(
                    name = req.name.trim(),
                    nick = req.nick?.trim(),
                    birthDate = req.birthDate
                )
                validate(toSave.nick, excludeId = existing.id)
                    .then(userRepository.save(toSave))
            }
            .flatMap { u ->
                userStackRepository.deleteByUserId(u.id)
                    .thenMany(
                        Flux.fromIterable(req.stack.map { it.toModel(u.id) })
                            .concatMap { s -> Mono.defer { template.insert(Stack::class.java).using(s) } }
                    )
                    .collectList()
                    .map { stacks -> u.withStacks(stacks) }
            }

    fun delete(id: String): Mono<Void> =
        userRepository.findById(id)
            .switchIfEmpty(Mono.error(UserNotFoundException(id)))
            .flatMap { u ->
                userStackRepository.deleteByUserId(u.id!!)
                    .then(userRepository.deleteById(u.id))
            }

    fun findStacksByUserId(userId: String): Flux<StackResponse> =
        userRepository.existsById(userId)
            .flatMapMany { exists ->
                if (!exists) Flux.error(UserNotFoundException(userId))
                else userStackRepository.findByUserId(userId).map { it.toResponse() }
            }
}