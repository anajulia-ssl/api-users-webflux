package com.estudos.users_api.service

import com.estudos.users_api.dto.StackRequest
import com.estudos.users_api.dto.UserRequest
import com.estudos.users_api.exception.NickAlreadyExistsException
import com.estudos.users_api.exception.UserNotFoundException
import com.estudos.users_api.model.Stack
import com.estudos.users_api.model.User
import com.estudos.users_api.repository.UserRepository
import com.estudos.users_api.repository.UserStackRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Answers
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.any
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDate

class UserServiceTest {

    private val template: R2dbcEntityTemplate = mock(R2dbcEntityTemplate::class.java, Answers.RETURNS_DEEP_STUBS)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val userStackRepository: UserStackRepository = mock(UserStackRepository::class.java)

    private val service = UserService(template, userRepository, userStackRepository)

    @Test
    fun `should create when nick is unique`() {
        val req = UserRequest(
            name = "Test",
            nick = " john ",
            birthDate = LocalDate.parse("1990-01-01"),
            stack = listOf(StackRequest("Kotlin", 5))
        )

        `when`(userRepository.findByNickExcludingId("john", null)).thenReturn(Mono.empty())

        // eco do objeto passado (User)
        `when`(template.insert(User::class.java).using(any(User::class.java))).thenAnswer { inv ->
            Mono.just(inv.arguments[0] as User)
        }
        // eco do objeto passado (Stack)
        `when`(template.insert(Stack::class.java).using(any(Stack::class.java))).thenAnswer { inv ->
            Mono.just(inv.arguments[0] as Stack)
        }

        StepVerifier.create(service.create(req))
            .assertNext { user ->
                assertEquals("Test", user.name)
                assertEquals("john", user.nick)
                assertEquals(LocalDate.parse("1990-01-01"), user.birthDate)
                assertEquals(1, user.stack.size)
                assertEquals("Kotlin", user.stack[0].name)
                assertEquals(5, user.stack[0].level)
                assertNotNull(user.id)
            }
            .verifyComplete()

        verify(userRepository).findByNickExcludingId("john", null)
        verify(template).insert(User::class.java)
        verify(template).insert(Stack::class.java)
        verifyNoMoreInteractions(userRepository, userStackRepository)
    }

    @Test
    fun `should throw NickAlreadyExistsException when nick already exists`() {
        val req = UserRequest("Test", "john", LocalDate.now(), emptyList())

        `when`(userRepository.findByNickExcludingId("john", null))
            .thenReturn(Mono.just(User(id = "u1", name = "X", nick = "john", birthDate = LocalDate.now())))

        StepVerifier.create(service.create(req))
            .expectError(NickAlreadyExistsException::class.java)
            .verify()

        // não realiza inserts nem save
        verify(template, never()).insert(User::class.java)
        verify(template, never()).insert(Stack::class.java)
        verify(userRepository, never()).save(any(User::class.java))
        verify(userStackRepository, never()).saveAll(anyList())
    }

    @Test
    fun `should find user by id`() {
        val user = User(id = "u1", name = "Ana", nick = "ana", birthDate = LocalDate.parse("1988-11-30"))

        `when`(userRepository.findById("u1")).thenReturn(Mono.just(user))
        `when`(userStackRepository.findByUserId("u1"))
            .thenReturn(Flux.just(Stack(userId = "u1", name = "Kotlin", level = 5)))

        StepVerifier.create(service.findById("u1"))
            .assertNext {
                assertEquals("u1", it.id)
                assertEquals("Ana", it.name)
                assertEquals("ana", it.nick)
                assertEquals(1, it.stack.size)
                assertEquals("Kotlin", it.stack[0].name)
                assertEquals(5, it.stack[0].level)
            }
            .verifyComplete()
    }

    @Test
    fun `should throw UserNotFoundException when user not found by id`() {
        `when`(userRepository.findById("missing")).thenReturn(Mono.empty())

        StepVerifier.create(service.findById("missing"))
            .expectError(UserNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `should return list of users`() {
        val u1 = User(id = "1", name = "A", nick = "a", birthDate = LocalDate.parse("1990-01-01"))
        val u2 = User(id = "2", name = "B", nick = "b", birthDate = LocalDate.parse("1993-05-10"))

        `when`(userRepository.findAll()).thenReturn(Flux.just(u1, u2))
        `when`(userStackRepository.findByUserId(anyString())).thenReturn(Flux.empty())

        StepVerifier.create(service.findAll().collectList())
            .assertNext { list ->
                assertEquals(2, list.size)
                assertEquals("A", list[0].name)
                assertEquals("B", list[1].name)
                assertTrue(list[0].stack.isEmpty())
                assertTrue(list[1].stack.isEmpty())
            }
            .verifyComplete()
    }

    @Test
    fun `should return empty list when no users exist`() {
        `when`(userRepository.findAll()).thenReturn(Flux.empty())

        StepVerifier.create(service.findAll().collectList())
            .assertNext { assertTrue(it.isEmpty()) }
            .verifyComplete()
    }

    @Test
    fun `should update user when exists`() {
        val existing = User(id = "u1", name = "Old", nick = "old", birthDate = LocalDate.parse("1991-04-12"))
        val req = UserRequest(
            name = " Updated ",
            nick = "old",
            birthDate = existing.birthDate!!,
            stack = listOf(StackRequest("Java", 5))
        )

        `when`(userRepository.findById("u1")).thenReturn(Mono.just(existing))
        `when`(userRepository.findByNickExcludingId("old", "u1")).thenReturn(Mono.empty())
        `when`(userRepository.save(any(User::class.java))).thenAnswer { inv ->
            Mono.just(inv.arguments[0] as User)
        }
        `when`(userStackRepository.deleteByUserId("u1")).thenReturn(Mono.empty())
        `when`(template.insert(Stack::class.java).using(any(Stack::class.java))).thenAnswer { inv ->
            Mono.just(inv.arguments[0] as Stack)
        }

        StepVerifier.create(service.update("u1", req))
            .assertNext {
                assertEquals("Updated", it.name)      // trim aplicado
                assertEquals("old", it.nick)
                assertEquals(1, it.stack.size)
                assertEquals("Java", it.stack[0].name)
                assertEquals(5, it.stack[0].level)
            }
            .verifyComplete()

        verify(userRepository).save(any(User::class.java))
        verify(userStackRepository).deleteByUserId("u1")
        verify(template).insert(Stack::class.java)
    }

    @Test
    fun `should throw UserNotFoundException when updating non-existing user`() {
        val req = UserRequest("Any", "any", LocalDate.now(), emptyList())

        `when`(userRepository.findById("missing")).thenReturn(Mono.empty())

        StepVerifier.create(service.update("missing", req))
            .expectError(UserNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `should delete user when exists`() {
        val u = User(id = "u1", name = "A", nick = "a", birthDate = LocalDate.now())

        `when`(userRepository.findById("u1")).thenReturn(Mono.just(u))
        `when`(userStackRepository.deleteByUserId("u1")).thenReturn(Mono.empty())
        `when`(userRepository.deleteById("u1")).thenReturn(Mono.empty())

        StepVerifier.create(service.delete("u1")).verifyComplete()

        verify(userStackRepository).deleteByUserId("u1")
        verify(userRepository).deleteById("u1")
    }

    @Test
    fun `should throw UserNotFoundException when deleting non-existing user`() {
        `when`(userRepository.findById("missing")).thenReturn(Mono.empty())

        StepVerifier.create(service.delete("missing"))
            .expectError(UserNotFoundException::class.java)
            .verify()

        verify(userRepository, never()).deleteById(anyString())
        verify(userStackRepository, never()).deleteByUserId(anyString())
    }

    @Test
    fun `should return stacks when user exists`() {
        `when`(userRepository.existsById("u1")).thenReturn(Mono.just(true))
        `when`(userStackRepository.findByUserId("u1"))
            .thenReturn(
                Flux.just(
                    Stack(userId = "u1", name = "Kotlin", level = 5),
                    Stack(userId = "u1", name = "Spring Boot", level = 8)
                )
            )

        StepVerifier.create(service.findStacksByUserId("u1").collectList())
            .assertNext { list ->
                assertEquals(2, list.size)
                assertEquals("Kotlin", list[0].name)
                assertEquals(5, list[0].level)
                assertEquals("Spring Boot", list[1].name)
                assertEquals(8, list[1].level)
            }
            .verifyComplete()
    }

    @Test
    fun `should throw UserNotFoundException when getting stacks of non-existing user`() {
        `when`(userRepository.existsById("uX")).thenReturn(Mono.just(false))

        StepVerifier.create(service.findStacksByUserId("uX"))
            .expectError(UserNotFoundException::class.java)
            .verify()

        verify(userStackRepository, never()).findByUserId(anyString())
    }
}