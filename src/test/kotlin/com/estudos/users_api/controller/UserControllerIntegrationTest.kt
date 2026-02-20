package com.estudos.users_api.controller

import com.estudos.users_api.dto.StackResponse
import com.estudos.users_api.dto.UserResponse
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.LocalDate
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired lateinit var webTestClient: WebTestClient
    @Autowired lateinit var template: R2dbcEntityTemplate

    @BeforeEach
    fun cleanDb() {
        template.databaseClient.sql("DELETE FROM STACKS").fetch().rowsUpdated().then().block()
        template.databaseClient.sql("DELETE FROM USERS").fetch().rowsUpdated().then().block()
    }

    @Nested
    inner class CreateTests {

        @Test
        fun `should create user when valid request`() {
            val body = """
                {
                  "name": "Test",
                  "nick": "test",
                  "birth_date": "1995-01-01",
                  "stack": [ { "name": "Kotlin", "level": 5 } ]
                }
            """.trimIndent()

            webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated
                .expectBody(UserResponse::class.java)
                .consumeWith { r ->
                    val u = r.responseBody!!
                    Assertions.assertNotNull(u.id)
                    Assertions.assertEquals("Test", u.name)
                    Assertions.assertEquals("test", u.nick)
                    Assertions.assertEquals(LocalDate.parse("1995-01-01"), u.birthDate)
                    Assertions.assertEquals(1, u.stack.size)
                    Assertions.assertTrue(u.stack.any { it.name == "Kotlin" && it.level == 5 })
                }
        }

        @Test
        fun `should create user without nick`() {
            val body = """
                {
                  "name": "Test",
                  "nick": null,
                  "birth_date": "1995-01-01",
                  "stack": [ { "name": "Kotlin", "level": 5 } ]
                }
            """.trimIndent()

            webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated
                .expectBody(UserResponse::class.java)
                .consumeWith { r ->
                    val u = r.responseBody!!
                    Assertions.assertNotNull(u.id)
                    Assertions.assertEquals("Test", u.name)
                    Assertions.assertNull(u.nick)
                    Assertions.assertEquals(LocalDate.parse("1995-01-01"), u.birthDate)
                    Assertions.assertTrue(u.stack.any { it.name == "Kotlin" && it.level == 5 })
                }
        }

        @Test
        fun `should return 409 when nick already exists`() {
            val user1 = """
                {"name":"Test 1","nick":"test","birth_date":"1990-01-01","stack":[{"name":"Spring","level":5}]}
            """.trimIndent()
            val user2 = """
                {"name":"Test 2","nick":"test","birth_date":"1992-02-02","stack":[{"name":"Kotlin","level":7}]}
            """.trimIndent()

            webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(user1)
                .exchange().expectStatus().isCreated

            webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(user2)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$[0].error").isEqualTo("conflict_exception")
                .jsonPath("$[0].description").isEqualTo("nick 'test' already exists")
        }

        @ParameterizedTest
        @MethodSource("com.estudos.users_api.controller.UserControllerIntegrationTest#invalidCreateBodies")
        fun `should return 400 with error response when invalid create request`(
            jsonBody: String,
            expectedFragment: String
        ) {
            webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$").isArray
                .jsonPath("$[0].error").isEqualTo("validation_exception")
                .jsonPath("$[*].description").value<List<String>> { descs ->
                    Assertions.assertTrue(
                        descs.any { it.contains(expectedFragment) },
                        "Expected to find '$expectedFragment' in $descs"
                    )
                }
        }

        @ParameterizedTest
        @CsvSource("1", "10")
        fun `should accept stack level at limits`(level: Int) {
            val body = """
                {
                  "name": "Test",
                  "nick": "test$level",
                  "birth_date": "1990-01-01",
                  "stack": [ { "name": "Java", "level": $level } ]
                }
            """.trimIndent()

            webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated
                .expectBody(UserResponse::class.java)
                .consumeWith { r ->
                    val user = r.responseBody!!
                    Assertions.assertEquals(level, user.stack.first().level)
                }
        }

        @Test
        fun `should reject duplicate stack items case insensitive`() {
            val body = """
                {
                  "name": "Test",
                  "nick": "test",
                  "birth_date": "1990-01-01",
                  "stack": [ { "name": "Java", "level": 5 }, { "name": "java", "level": 6 } ]
                }
            """.trimIndent()

            webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$[0].error").isEqualTo("validation_exception")
                .jsonPath("$[*].description")
                .value<List<String>> { descriptions ->
                    Assertions.assertTrue(
                        "stack cannot contain duplicate values" in descriptions,
                        "Expected descriptions to contain the duplicate-stack message, but was: $descriptions"
                    )
                }
        }
    }

    @Nested
    inner class ReadTests {

        @Test
        fun `should return 200 with empty list when no users exist`() {
            webTestClient.get().uri { it.path("/api/users").build() }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(0)
                .jsonPath("$.result_set.limit").isEqualTo(20)
                .jsonPath("$.result_set.offset").isEqualTo(0)
                .jsonPath("$.result_set.total").isEqualTo(0)
                .jsonPath("$._links.self.href").exists()
                .jsonPath("$._links.first.href").exists()
                .jsonPath("$._links.last.href").exists()
        }

        @Test
        fun `should return 200 with list of users when users exist`() {
            val body = """
                { 
                  "name": "Test", 
                  "nick":"test", 
                  "birth_date":"1995-01-01",
                  "stack": [ { "name":"Kotlin", "level":5 } ] 
                }
            """.trimIndent()

            webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isCreated

            webTestClient.get().uri { it.path("/api/users").build() }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].id").isNotEmpty
                .jsonPath("$.items[0].name").isEqualTo("Test")
                .jsonPath("$.items[0].nick").isEqualTo("test")
                .jsonPath("$.items[0].birth_date").isEqualTo("1995-01-01")
                .jsonPath("$.items[0].stack[0].name").isEqualTo("Kotlin")
                .jsonPath("$.items[0].stack[0].level").isEqualTo(5)
        }

        @Test
        fun `should sort users by name asc`() {
            val userA = """{"name":"User A","nick":"user1","birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}"""
            val userB = """{"name":"User B","nick":"user2","birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}"""

            webTestClient.post().uri("/api/users").contentType(MediaType.APPLICATION_JSON).bodyValue(userA)
                .exchange().expectStatus().isCreated
            webTestClient.post().uri("/api/users").contentType(MediaType.APPLICATION_JSON).bodyValue(userB)
                .exchange().expectStatus().isCreated

            webTestClient.get().uri {
                it.path("/api/users")
                    .queryParam("offset", 0)
                    .queryParam("limit", 10)
                    .queryParam("sort", "name:asc")
                    .build()
            }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].name").isEqualTo("User A")
                .jsonPath("$.items[1].name").isEqualTo("User B")
        }

        @Test
        fun `should return 200 when user exists by id`() {
            val create = """
                {"name":"Test","nick":"test","birth_date":"1985-05-05","stack":[{"name":"Oracle","level":5}]}
            """.trimIndent()

            val userId = webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(create)
                .exchange().expectStatus().isCreated
                .expectBody(UserResponse::class.java).returnResult().responseBody!!.id

            webTestClient.get().uri("/api/users/{id}", userId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(userId)
                .jsonPath("$.name").isEqualTo("Test")
                .jsonPath("$.nick").isEqualTo("test")
                .jsonPath("$.birth_date").isEqualTo("1985-05-05")
                .jsonPath("$.stack[0].name").isEqualTo("Oracle")
                .jsonPath("$.stack[0].level").isEqualTo(5)
        }

        @Test
        fun `should return 404 when user not exist by id`() {
            val randomId = UUID.randomUUID().toString()

            webTestClient.get().uri("/api/users/{id}", randomId)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$[0].error").isEqualTo("not_found_exception")
                .jsonPath("$[0].description").isEqualTo("user with id '$randomId' not found")
        }

        @ParameterizedTest
        @CsvSource(
            value = [
                "/api/users?offset=-1&limit=5; invalid_pagination; Invalid pagination parameters",
                "/api/users?offset=0&limit=0; invalid_pagination; Invalid pagination parameters",
                "/api/users?offset=0&limit=5&sort=unknown:asc; invalid_sort; Invalid sorting parameters"
            ],
            delimiter = ';'
        )
        fun `should return 400 with error response when invalid pagination or sort`(
            url: String,
            expectedError: String,
            expectedDescription: String
        ) {
            webTestClient.get().uri(url)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$[0].error").isEqualTo(expectedError)
                .jsonPath("$[0].description").isEqualTo(expectedDescription)
        }
    }

    @Nested
    inner class UpdateTests {

        @Test
        fun `should update user when valid request`() {
            val create = """
                {"name":"Test","nick":"test","birth_date":"1992-02-02","stack":[{"name":"Java","level":5}]}
            """.trimIndent()

            val userId = webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(create)
                .exchange().expectStatus().isCreated
                .expectBody(UserResponse::class.java).returnResult().responseBody!!.id

            val update = """
                {"name":"Test Updated","nick":"test","birth_date":"1992-02-02","stack":[{"name":"Spring","level":7}]}
            """.trimIndent()

            webTestClient.put().uri("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(userId)
                .jsonPath("$.name").isEqualTo("Test Updated")
                .jsonPath("$.nick").isEqualTo("test")
                .jsonPath("$.birth_date").isEqualTo("1992-02-02")
                .jsonPath("$.stack[0].name").isEqualTo("Spring")
                .jsonPath("$.stack[0].level").isEqualTo(7)
        }

        @Test
        fun `should return 404 when updating not existing user`() {
            val update = """
                {"name":"Test","nick":"test","birth_date":"1990-01-01","stack":[{"name":"Kotlin","level":5}]}
            """.trimIndent()

            val randomId = UUID.randomUUID().toString()

            webTestClient.put().uri("/api/users/{id}", randomId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$[0].error").isEqualTo("not_found_exception")
                .jsonPath("$[0].description").isEqualTo("user with id '$randomId' not found")
        }

        @Test
        fun `should return 409 when updating with duplicate nick`() {
            val user1 = """
                {"name":"Test 1","nick":"test1","birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}
            """.trimIndent()
            val user2 = """
                {"name":"Test 2","nick":"test2","birth_date":"1991-01-01","stack":[{"name":"Spring","level":5}]}
            """.trimIndent()

            val aId = webTestClient.post().uri("/api/users").contentType(MediaType.APPLICATION_JSON).bodyValue(user1)
                .exchange().expectStatus().isCreated
                .expectBody(UserResponse::class.java).returnResult().responseBody!!.id

            webTestClient.post().uri("/api/users").contentType(MediaType.APPLICATION_JSON).bodyValue(user2)
                .exchange().expectStatus().isCreated

            val updateUser1 = """
                {"name":"Test 1 Updated","nick":"test2","birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}
            """.trimIndent()

            webTestClient.put().uri("/api/users/{id}", aId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateUser1)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$[0].error").isEqualTo("conflict_exception")
                .jsonPath("$[0].description").isEqualTo("nick 'test2' already exists")
        }

        @Test
        fun `should update nick to null when allowed`() {
            val create = """
                {"name":"Test","nick":"test","birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}
            """.trimIndent()
            val userId = webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(create)
                .exchange().expectStatus().isCreated
                .expectBody(UserResponse::class.java).returnResult().responseBody!!.id

            val update = """
                {"name":"Test","nick":null,"birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}
            """.trimIndent()

            webTestClient.put().uri("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.nick").doesNotExist()
        }

        @ParameterizedTest
        @MethodSource("com.estudos.users_api.controller.UserControllerIntegrationTest#invalidCreateBodies")
        fun `should return 400 when invalid update request`(
            invalidJson: String,
            expectedFragment: String
        ) {
            val create = """
                {"name":"Test","nick":"test","birth_date":"1991-03-03","stack":[{"name":"Java","level":5}]}
            """.trimIndent()
            val id = webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(create)
                .exchange().expectStatus().isCreated
                .expectBody(UserResponse::class.java).returnResult().responseBody!!.id

            webTestClient.put().uri("/api/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidJson)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$").isArray
                .jsonPath("$[0].error").isEqualTo("validation_exception")
                .jsonPath("$[*].description").value<List<String>> {
                    Assertions.assertTrue(
                        it.any { msg -> msg.contains(expectedFragment) },
                        "Expected to find '$expectedFragment' in $it"
                    )
                }
        }
    }

    @Nested
    inner class DeleteTests {

        @Test
        fun `should delete user when exists`() {
            val create = """
                {"name":"Test","nick":"test","birth_date":"1993-04-04","stack":[{"name":"Spring","level":5}]}
            """.trimIndent()

            val id = webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(create)
                .exchange().expectStatus().isCreated
                .expectBody(UserResponse::class.java).returnResult().responseBody!!.id

            webTestClient.delete().uri("/api/users/{id}", id)
                .exchange()
                .expectStatus().isNoContent
                .expectBody().isEmpty
        }

        @Test
        fun `should return 404 when deleting not existing user`() {
            val randomId = UUID.randomUUID().toString()

            webTestClient.delete().uri("/api/users/{id}", randomId)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$[0].error").isEqualTo("not_found_exception")
                .jsonPath("$[0].description").isEqualTo("user with id '$randomId' not found")
        }
    }

    @Nested
    inner class StackTests {

        @Test
        fun `should return stacks when user exists`() {
            val create = """
              {"name":"Test","nick":"test","birth_date":"1994-06-06",
               "stack":[{"name":"Kotlin","level":5},{"name":"Spring Boot","level":8}]}
            """.trimIndent()

            val id = webTestClient.post().uri("/api/users")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(create)
                .exchange().expectStatus().isCreated
                .expectBody(UserResponse::class.java).returnResult().responseBody!!.id

            val result = webTestClient.get().uri("/api/users/{id}/stacks", id)
                .exchange()
                .expectStatus().isOk
                .expectBodyList(StackResponse::class.java)
                .returnResult()

            val list = result.responseBody!!

            Assertions.assertEquals(2, list.size)
            Assertions.assertTrue(list.any { it.name == "Kotlin" && it.level == 5 })
            Assertions.assertTrue(list.any { it.name == "Spring Boot" && it.level == 8 })
        }

        @Test
        fun `should return 404 when user not exist for stacks`() {
            val randomId = UUID.randomUUID().toString()

            webTestClient.get().uri("/api/users/{id}/stacks", randomId)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$[0].error").isEqualTo("not_found_exception")
                .jsonPath("$[0].description").isEqualTo("user with id '$randomId' not found")
        }
    }

    companion object {
        @JvmStatic
        fun invalidCreateBodies() = listOf(
            // name blank
            Arguments.of(
                """{"name":"","nick":"test","birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}""",
                "name must not be blank"
            ),
            // name < 3
            Arguments.of(
                """{"name":"Te","nick":"test","birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}""",
                "name size must be between 3 and 255"
            ),
            // name > 255
            Arguments.of(
                """{"name":"${"T".repeat(256)}","nick":"test","birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}""",
                "name size must be between 3 and 255"
            ),
            // nick empty
            Arguments.of(
                """{"name":"Test","nick":"","birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}""",
                "nick size must be between 1 and 255"
            ),
            // nick > 255
            Arguments.of(
                """{"name":"Test","nick":"${"t".repeat(256)}","birth_date":"1990-01-01","stack":[{"name":"Java","level":5}]}""",
                "nick size must be between 1 and 255"
            ),
            // birth_date null
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":null,"stack":[{"name":"Java","level":5}]}""",
                "birth date must not be null"
            ),
            // birth_date in the future
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":"${LocalDate.now().plusDays(1)}","stack":[{"name":"Java","level":5}]}""",
                "birth date must be a past date"
            ),
            // stack null
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":"1990-01-01","stack":null}""",
                "stack must not be null"
            ),
            // stack empty
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":"1990-01-01","stack":[]}""",
                "stack must contain at least 1 element"
            ),
            // stack duplicate
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":"1990-01-01","stack":[{"name":"Java","level":5},{"name":"Java","level":7}]}""",
                "stack cannot contain duplicate values"
            ),
            // stack[].name null
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":"1990-01-01","stack":[{"name":null,"level":5}]}""",
                "stack item name must not be null"
            ),
            // stack[].name blank
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":"1990-01-01","stack":[{"name":" ","level":5}]}""",
                "stack item name must not be blank"
            ),
            // stack[].name > 32
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":"1990-01-01","stack":[{"name":"${"A".repeat(33)}","level":5}]}""",
                "stack item name size must be less than or equal to 32"
            ),
            // level null
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":"1990-01-01","stack":[{"name":"Java","level":null}]}""",
                "stack item level must not be null"
            ),
            // level < 1
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":"1990-01-01","stack":[{"name":"Java","level":0}]}""",
                "stack item level must be between 1 and 10"
            ),
            // level > 10
            Arguments.of(
                """{"name":"Test","nick":"test","birth_date":"1990-01-01","stack":[{"name":"Java","level":11}]}""",
                "stack item level must be between 1 and 10"
            )
        )
    }
}