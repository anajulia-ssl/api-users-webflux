package com.estudos.users_api.repository

import com.estudos.users_api.model.Stack
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface StackRepository : ReactiveCrudRepository<Stack, String> {
    fun findByUserId(userId: String?): Flux<Stack>
    fun deleteByUserId(userId: String?): Mono<Void>
}
