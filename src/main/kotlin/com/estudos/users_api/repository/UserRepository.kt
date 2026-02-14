package com.estudos.users_api.repository

import com.estudos.users_api.model.User
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface UserRepository : ReactiveCrudRepository<User, String> {

    @Query("SELECT * FROM USERS WHERE NICK = :nick AND (:excludeId IS NULL OR ID <> :excludeId)")
    fun findByNickExcludingId(nick: String, excludeId: String?): Mono<User>
}
