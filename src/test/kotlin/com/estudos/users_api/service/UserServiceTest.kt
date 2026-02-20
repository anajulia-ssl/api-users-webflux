package com.estudos.users_api.service

import com.estudos.users_api.dto.pagination.PageQuery
import com.estudos.users_api.dto.StackRequest
import com.estudos.users_api.dto.UserRequest
import com.estudos.users_api.dto.pagination.toPageable
import com.estudos.users_api.exception.NickAlreadyExistsException
import com.estudos.users_api.exception.UserNotFoundException
import com.estudos.users_api.model.Stack
import com.estudos.users_api.model.User
import com.estudos.users_api.repository.StackRepository
import com.estudos.users_api.repository.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDate
import java.util.UUID

class UserServiceTest {
    private val template: R2dbcEntityTemplate = mock(R2dbcEntityTemplate::class.java, Answers.RETURNS_DEEP_STUBS)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val stackRepository: StackRepository = mock(StackRepository::class.java)

    private val service = UserService(template, userRepository, stackRepository)

    @Test
    fun `should create when nick is unique`() {
        val req = UserRequest(
            name = "Test",
            nick = " test ",
            birthDate = LocalDate.parse("1990-01-01"),
            stack = listOf(StackRequest("Kotlin", 5))
        )

        `when`(userRepository.findByNickExcludingId("test", null)).thenReturn(Mono.empty())
        `when`(template.insert(User::class.java).using(any(User::class.java))).thenAnswer { inv ->
            Mono.just(inv.arguments[0] as User)
        }
        `when`(template.insert(Stack::class.java).using(any(Stack::class.java))).thenAnswer { inv ->
            Mono.just(inv.arguments[0] as Stack)
        }

        StepVerifier.create(service.create(req))
            .assertNext { user ->
                assertNotNull(user.id)
                assertEquals("Test", user.name)
                assertEquals("test", user.nick)
                assertEquals(LocalDate.parse("1990-01-01"), user.birthDate)
                assertEquals(1, user.stack.size)
                assertTrue(user.stack.any { it.name == "Kotlin" && it.level == 5 })
            }
            .verifyComplete()
    }

    @Test
    fun `should throw NickAlreadyExistsException when nick already exists`() {
        val req = UserRequest("Test", "test", LocalDate.now(), emptyList())
        val id = UUID.randomUUID().toString()
        `when`(userRepository.findByNickExcludingId("test", null))
            .thenReturn(Mono.just(User(id = id, name = "Test", nick = "test", birthDate = LocalDate.now())))

        StepVerifier.create(service.create(req))
            .expectError(NickAlreadyExistsException::class.java)
            .verify()
    }

    @Test
    fun `should find user by id`() {
        val id = UUID.randomUUID().toString()
        val user = User(id = id, name = "Test", nick = "test", birthDate = LocalDate.parse("1988-11-30"))

        `when`(userRepository.findById(id)).thenReturn(Mono.just(user))
        `when`(stackRepository.findByUserId(id))
            .thenReturn(Flux.just(Stack(userId = id, name = "Kotlin", level = 5)))

        StepVerifier.create(service.findById(id))
            .assertNext { user ->
                assertEquals(id, user.id)
                assertEquals("Test", user.name)
                assertEquals("test", user.nick)
                assertEquals(1, user.stack.size)
                assertTrue(user.stack.any { it.name == "Kotlin" && it.level == 5 })
            }
            .verifyComplete()
    }

    @Test
    fun `should throw UserNotFoundException when user not found by id`() {
        val id = UUID.randomUUID().toString()
        `when`(userRepository.findById(id)).thenReturn(Mono.empty())

        StepVerifier.create(service.findById(id))
            .expectError(UserNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `should return paged users`() {
        val query = PageQuery(offset = 0, limit = 10, sort = "name:asc")
        val request = MockServerHttpRequest
            .get("/api/users?offset=0&limit=10&sort=name:asc")
            .build()

        val idUser1 = UUID.randomUUID().toString()
        val user1 = User(id = idUser1, name = "Test 1", nick = "test1", birthDate = LocalDate.parse("1990-01-01"))
        val idUser2 = UUID.randomUUID().toString()
        val user2 = User(id = idUser2, name = "Test 2", nick = "test2", birthDate = LocalDate.parse("1993-05-10"))

        val pageable = query.toPageable(User::class)

        `when`(userRepository.findAllBy(pageable)).thenReturn(Flux.just(user1, user2))
        `when`(userRepository.count()).thenReturn(Mono.just(2L))
        `when`(stackRepository.findByUserId(anyString())).thenReturn(Flux.empty())

        StepVerifier.create(service.findAll(query, request))
            .assertNext { page ->
                assertEquals(0, page.resultSet.offset)
                assertEquals(10, page.resultSet.limit)
                assertEquals(2L, page.resultSet.total)
                assertEquals(2, page.resultSet.size)
                assertTrue(page.items.any { it.name == "Test 1" })
                assertTrue(page.items.any { it.name == "Test 2" })
                assertNotNull(page.links.self.href)
                assertNotNull(page.links.first.href)
                assertNotNull(page.links.last.href)
            }
            .verifyComplete()
    }

    @Test
    fun `should return empty page when no users exist`() {
        val query = PageQuery(offset = 0, limit = 10, sort = null)
        val request = MockServerHttpRequest
            .get("/api/users?offset=0&limit=10")
            .build()

        val pageable = query.toPageable(User::class)

        `when`(userRepository.findAllBy(pageable)).thenReturn(Flux.empty())
        `when`(userRepository.count()).thenReturn(Mono.just(0L))

        StepVerifier.create(service.findAll(query, request))
            .assertNext { page ->
                assertEquals(0L, page.resultSet.total)
                assertEquals(0, page.resultSet.size)
                assertTrue(page.items.isEmpty())
            }
            .verifyComplete()
    }

    @Test
    fun `should update user when exists`() {
        val id = UUID.randomUUID().toString()
        val existing = User(id = id, name = "Test", nick = "test", birthDate = LocalDate.parse("1991-04-12"))
        val req = UserRequest(
            name = " Test Updated ",
            nick = "test",
            birthDate = existing.birthDate,
            stack = listOf(StackRequest("Java", 5))
        )

        `when`(userRepository.findById(id)).thenReturn(Mono.just(existing))
        `when`(userRepository.findByNickExcludingId("test", id)).thenReturn(Mono.empty())
        `when`(userRepository.save(any(User::class.java))).thenAnswer { inv ->
            Mono.just(inv.arguments[0] as User)
        }
        `when`(stackRepository.deleteByUserId(id)).thenReturn(Mono.empty())
        `when`(template.insert(Stack::class.java).using(any(Stack::class.java))).thenAnswer { inv ->
            Mono.just(inv.arguments[0] as Stack)
        }

        StepVerifier.create(service.update(id, req))
            .assertNext { user ->
                assertEquals("Test Updated", user.name)
                assertEquals("test", user.nick)
                assertEquals(1, user.stack.size)
                assertTrue(user.stack.any { it.name == "Java" && it.level == 5 })
            }
            .verifyComplete()
    }

    @Test
    fun `should throw UserNotFoundException when updating non-existing user`() {
        val req = UserRequest("Test", "test", LocalDate.now(), emptyList())
        val id = UUID.randomUUID().toString()
        `when`(userRepository.findById(id)).thenReturn(Mono.empty())

        StepVerifier.create(service.update(id, req))
            .expectError(UserNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `should delete user when exists`() {
        val id = UUID.randomUUID().toString()
        val user = User(id = id, name = "Test", nick = "test", birthDate = LocalDate.now())

        `when`(userRepository.findById(id)).thenReturn(Mono.just(user))
        `when`(stackRepository.deleteByUserId(id)).thenReturn(Mono.empty())
        `when`(userRepository.deleteById(id)).thenReturn(Mono.empty())

        StepVerifier.create(service.delete(id)).verifyComplete()
    }

    /* ---------------------------------------------------------------------- */
    /* should throw UserNotFoundException when deleting non-existing user     */
    /* ---------------------------------------------------------------------- */
    @Test
    fun `should throw UserNotFoundException when deleting non-existing user`() {
        val id = UUID.randomUUID().toString()
        `when`(userRepository.findById(id)).thenReturn(Mono.empty())

        StepVerifier.create(service.delete(id))
            .expectError(UserNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `should return stacks when user exists`() {
        val id = UUID.randomUUID().toString()
        `when`(userRepository.existsById(id)).thenReturn(Mono.just(true))
        `when`(stackRepository.findByUserId(id))
            .thenReturn(
                Flux.just(
                    Stack(userId = id, name = "Kotlin", level = 5),
                    Stack(userId = id, name = "Spring Boot", level = 8)
                )
            )

        StepVerifier.create(service.findStacksByUserId(id).collectList())
            .assertNext { list ->
                assertEquals(2, list.size)
                assertTrue(list.any { it.name == "Kotlin" && it.level == 5 })
                assertTrue(list.any { it.name == "Spring Boot" && it.level == 8 })
            }
            .verifyComplete()
    }

    @Test
    fun `should throw UserNotFoundException when getting stacks of non-existing user`() {
        val id = UUID.randomUUID().toString()
        `when`(userRepository.existsById(id)).thenReturn(Mono.just(false))

        StepVerifier.create(service.findStacksByUserId(id))
            .expectError(UserNotFoundException::class.java)
            .verify()
    }
}
