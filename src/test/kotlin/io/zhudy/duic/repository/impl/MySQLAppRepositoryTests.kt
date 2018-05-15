/**
 * Copyright 2017-2018 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zhudy.duic.repository.impl

import io.zhudy.duic.UserContext
import io.zhudy.duic.domain.App
import io.zhudy.duic.domain.Pageable
import io.zhudy.duic.repository.AppRepository
import io.zhudy.duic.server.Application
import io.zhudy.duic.server.BeansInitializer
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests
import org.springframework.transaction.support.TransactionTemplate
import reactor.test.StepVerifier
import java.util.*

/**
 * @author Kevin Zou (kevinz@weghst.com)
 */
@ActiveProfiles("mysql", "test")
@SpringBootTest(classes = [Application::class])
@ContextConfiguration(initializers = [BeansInitializer::class])
class MySQLAppRepositoryTests : AbstractJUnit4SpringContextTests() {

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate
    @Autowired
    lateinit var jdbcTemplate: NamedParameterJdbcTemplate
    @Autowired
    lateinit var appRepository: AppRepository

    val rootUserContext = object : UserContext {
        override val email: String
            get() = "root@unit-test.com"
        override val isRoot: Boolean
            get() = false
    }
    val normalUserContext = object : UserContext {
        override val email: String
            get() = "a@unit-test.com"
        override val isRoot: Boolean
            get() = false
    }

    @After
    fun after() {
        transactionTemplate.execute {
            jdbcTemplate.update("delete from app", EmptySqlParameterSource.INSTANCE)
            jdbcTemplate.update("delete from app_history", EmptySqlParameterSource.INSTANCE)
        }
    }

    @Test
    fun insert() {
        val app = App(
                name = UUID.randomUUID().toString(),
                profile = UUID.randomUUID().toString(),
                description = "unit test",
                token = "[TOKEN]",
                users = listOf("a@unit-test.com", "b@unit-test.com")
        )
        StepVerifier.create(appRepository.insert(app))
                .expectNext(1)
                .verifyComplete()
    }

    @Test
    fun delete() {
        val app = App(
                name = UUID.randomUUID().toString(),
                profile = UUID.randomUUID().toString(),
                description = "unit test",
                token = "[TOKEN]",
                users = listOf("a@unit-test.com", "b@unit-test.com")
        )
        appRepository.insert(app).block()

        StepVerifier.create(appRepository.delete(app, normalUserContext))
                .expectNext(1)
                .verifyComplete()

        jdbcTemplate.query(
                "SELECT * FROM app_history WHERE name=:name AND profile=:profile",
                mapOf(
                        "name" to app.name,
                        "profile" to app.profile
                ),
                ResultSetExtractor {
                    assertTrue(it.next())
                    assertEquals(app.users.joinToString(","), it.getString("users"))
                    assertEquals(normalUserContext.email, it.getString("deleted_by"))
                }
        )
    }

    @Test
    fun findOne() {
        val app = App(
                name = UUID.randomUUID().toString(),
                profile = UUID.randomUUID().toString(),
                description = "unit test",
                token = "[TOKEN]",
                users = listOf("a@unit-test.com", "b@unit-test.com")
        )
        appRepository.insert(app).block()

        val dbApp = appRepository.findOne<App>(app.name, app.profile).block()
        assertEquals(app.name, dbApp.name)
        assertEquals(app.profile, dbApp.profile)
        assertEquals(app.description, dbApp.description)
        assertEquals(app.token, dbApp.token)
        assertEquals(app.ipLimit, dbApp.ipLimit)
        assertEquals(app.users.joinToString(","), dbApp.users.joinToString(","))
    }

    @Test
    fun update() {
        val app = App(
                name = UUID.randomUUID().toString(),
                profile = UUID.randomUUID().toString(),
                description = "unit test",
                token = "[TOKEN]",
                users = listOf("a@unit-test.com", "b@unit-test.com")
        )
        appRepository.insert(app).block()

        StepVerifier.create(appRepository.update(app, normalUserContext))
                .expectNext(app.v)
                .verifyComplete()

        jdbcTemplate.query(
                "SELECT * FROM app_history WHERE name=:name AND profile=:profile",
                mapOf(
                        "name" to app.name,
                        "profile" to app.profile
                ),
                ResultSetExtractor {
                    assertTrue(it.next())
                    assertEquals(app.users.joinToString(","), it.getString("users"))
                    assertEquals(normalUserContext.email, it.getString("updated_by"))
                }
        )
    }

    @Test
    fun updateContent() {
        val app = App(
                name = UUID.randomUUID().toString(),
                profile = UUID.randomUUID().toString(),
                description = "unit test",
                token = "[TOKEN]",
                users = listOf("a@unit-test.com", "b@unit-test.com")
        )
        appRepository.insert(app).block()

        app.content = "a: a"
        val v = appRepository.updateContent(app, normalUserContext).block()
        assertEquals(app.v + 1, v)

        jdbcTemplate.query(
                "SELECT * FROM app_history WHERE name=:name AND profile=:profile",
                mapOf(
                        "name" to app.name,
                        "profile" to app.profile
                ),
                ResultSetExtractor {
                    assertTrue(it.next())
                    assertEquals(app.users.joinToString(","), it.getString("users"))
                    assertEquals(normalUserContext.email, it.getString("updated_by"))
                    assertEquals(app.v, it.getInt("v"))
                }
        )
    }

    @Test
    fun findAll() {
        for (n in 1..30) {
            val app = App(
                    name = UUID.randomUUID().toString(),
                    profile = UUID.randomUUID().toString(),
                    description = "unit test",
                    token = "[TOKEN]",
                    users = listOf("a@unit-test.com", "b@unit-test.com")
            )
            appRepository.insert(app).block()
        }

        val all = appRepository.findAll().collectList().block()
        assertTrue(all.isNotEmpty())
    }

    @Test
    fun findPage() {
        for (n in 1..30) {
            val app = App(
                    name = UUID.randomUUID().toString(),
                    profile = UUID.randomUUID().toString(),
                    description = "unit test",
                    token = "[TOKEN]",
                    users = listOf("a@unit-test.com", "b@unit-test.com")
            )
            appRepository.insert(app).block()
        }

        val p = Pageable()
        val list = appRepository.findPage(p).block()
        assertTrue(list.items.isNotEmpty())
    }

    @Test
    fun findPageByUser() {
        for (n in 1..30) {
            val app = App(
                    name = UUID.randomUUID().toString(),
                    profile = UUID.randomUUID().toString(),
                    description = "unit test",
                    token = "[TOKEN]",
                    users = listOf("a@unit-test.com", "b@unit-test.com")
            )
            appRepository.insert(app).block()
        }

        val p = Pageable()
        val list = appRepository.findPageByUser(p, normalUserContext).block()
        assertTrue(list.items.isNotEmpty())
    }

    @Test
    fun searchPage() {
        for (n in 1..30) {
            val app = App(
                    name = UUID.randomUUID().toString(),
                    profile = UUID.randomUUID().toString(),
                    description = "unit test",
                    token = "[TOKEN]",
                    users = listOf("a@unit-test.com", "b@unit-test.com")
            )
            appRepository.insert(app).block()
        }

        val p = Pageable()
        StepVerifier.create(appRepository.searchPage("", p))
                .expectNextMatches {
                    it.totalItems >= 30 && it.items.size == p.size
                }
                .verifyComplete()

        StepVerifier.create(appRepository.searchPage("测试", p))
                .expectNextMatches {
                    it.totalItems == 0 && it.items.isEmpty()
                }
                .verifyComplete()
    }

    @Test
    fun searchPageByUser() {
        for (n in 1..30) {
            val app = App(
                    name = UUID.randomUUID().toString(),
                    profile = UUID.randomUUID().toString(),
                    description = "unit test",
                    token = "[TOKEN]",
                    users = listOf("a@unit-test.com", "b@unit-test.com")
            )
            appRepository.insert(app).block()
        }

        val p = Pageable()
        StepVerifier.create(appRepository.searchPageByUser("", p, normalUserContext))
                .expectNextMatches {
                    it.totalItems >= 30 && it.items.size == p.size
                }
                .verifyComplete()

        StepVerifier.create(appRepository.searchPageByUser("测试", p, normalUserContext))
                .expectNextMatches {
                    it.totalItems == 0 && it.items.isEmpty()
                }
                .verifyComplete()
    }

    @Test
    fun findByUpdatedAt() {
        for (n in 1..30) {
            val app = App(
                    name = UUID.randomUUID().toString(),
                    profile = UUID.randomUUID().toString(),
                    description = "unit test",
                    token = "[TOKEN]",
                    users = listOf("a@unit-test.com", "b@unit-test.com")
            )
            appRepository.insert(app).block()
        }

        val updatedAt = LocalDate.parse("2018-01-01").toDate()
        val list = appRepository.findByUpdatedAt(updatedAt).collectList().block()

        assertTrue(list.size >= 30)

        var prevUpdatedAt: DateTime? = null
        list.forEach {
            if (prevUpdatedAt != null) {
                assertFalse(it.updatedAt!!.isBefore(prevUpdatedAt))
            }
            prevUpdatedAt = it.updatedAt
        }
    }

    @Test
    fun findLast50History() {
        val app = App(
                name = UUID.randomUUID().toString(),
                profile = UUID.randomUUID().toString(),
                description = "unit test",
                token = "[TOKEN]",
                users = listOf("a@unit-test.com", "b@unit-test.com")
        )
        appRepository.insert(app).block()

        for (n in 1..100) {
            app.description = "unit test[$n]"
            appRepository.update(app, normalUserContext).block()
        }

        val list = appRepository.findLast50History(app.name, app.profile).collectList().block()
        assertEquals(50, list.size)
    }

    @Test
    fun findAllNames() {
        for (n in 1..30) {
            val app = App(
                    name = UUID.randomUUID().toString(),
                    profile = UUID.randomUUID().toString(),
                    description = "unit test",
                    token = "[TOKEN]",
                    users = listOf("a@unit-test.com", "b@unit-test.com")
            )
            appRepository.insert(app).block()
        }

        val list = appRepository.findAllNames().collectList().block()
        assertTrue(list.size >= 30)
    }

    @Test
    fun findProfilesByName() {
        val name = UUID.randomUUID().toString()
        for (n in 1..30) {
            val app = App(
                    name = name,
                    profile = UUID.randomUUID().toString(),
                    description = "unit test",
                    token = "[TOKEN]",
                    users = listOf("a@unit-test.com", "b@unit-test.com")
            )
            appRepository.insert(app).block()
        }

        val list = appRepository.findProfilesByName(name).collectList().block()
        assertEquals(30, list.size)
    }

    @Test
    fun findDeletedByCreatedAt() {
        val apps = mutableListOf<App>()
        for (n in 1..30) {
            val app = App(
                    name = UUID.randomUUID().toString(),
                    profile = UUID.randomUUID().toString(),
                    description = "unit test",
                    token = "[TOKEN]",
                    users = listOf("a@unit-test.com", "b@unit-test.com")
            )

            appRepository.insert(app).block()
            apps.add(app)
        }

        apps.forEach {
            appRepository.delete(it, normalUserContext).block()
        }

        val createdAt = LocalDate.parse("2018-01-01").toDate()
        val list = appRepository.findDeletedByCreatedAt(createdAt).collectList().block()
        assertTrue(list.isNotEmpty())

        var prevCreatedAt: DateTime? = null
        list.forEach {
            if (prevCreatedAt != null) {
                assertFalse(it.createdAt!!.isBefore(prevCreatedAt))
            }
            prevCreatedAt = it.createdAt
        }
    }

}