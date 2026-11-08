/*
 * Copyright (c) 2026-present The Kttp Authors. All rights reserved.
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

package io.github.photowey.kttp.idea.io

import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Verifies concurrent history persistence, immutable snapshots, path normalization, and subscriptions.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class HistoryRepositoryTest {
    @Test
    fun concurrentWritersKeepEveryEntryAndPublishInCommitOrder() = withRepository { repository, _ ->
        val pool = Executors.newFixedThreadPool(4)
        val publishedSizes = mutableListOf<Int>()
        repository.subscribe { publishedSizes.add(it.size) }
        try {
            val tasks = (1..40).map { index ->
                pool.submit {
                    repository.append(
                        listOf(
                            HistoryPersistence.Record(
                                "request-$index",
                                "/response/$index"
                            )
                        )
                    )
                }
            }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals((1..40).toList(), publishedSizes)
            assertEquals((1..40).map { "request-$it" }.toSet(), repository.load().map { it.scriptName }.toSet())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun failedReadCannotPublishOrReplaceTheSourceArchive() = withRepository { repository, file ->
        val corrupted = "{ damaged history"
        file.writeText(corrupted)
        val published = mutableListOf<List<HistoryPersistence.Record>>()
        repository.subscribe { published.add(it) }
        assertFailsWith<StatePersistenceException> {
            repository.append(
                listOf(
                    HistoryPersistence.Record(
                        "new",
                        "/new"
                    )
                )
            )
        }
        assertFailsWith<StatePersistenceException> { repository.clear() }
        assertTrue(published.isEmpty())
        assertEquals(corrupted, file.readText())
    }

    @Test
    fun snapshotsAreImmutableAndBothPersistedPathsAreAbsolute() = withRepository { repository, file ->
        val snapshot =
            repository.append(listOf(HistoryPersistence.Record("relative", "responses/a.json", "requests/a.kttp")))
        assertTrue(java.io.File(snapshot.single().responseFilePath).isAbsolute)
        assertTrue(java.io.File(snapshot.single().scriptFilePath!!).isAbsolute)
        assertFailsWith<UnsupportedOperationException> { (snapshot as MutableList<HistoryPersistence.Record>).clear() }
        val reloaded = HistoryRepository { file }.load()
        assertEquals(snapshot, reloaded)
    }

    @Test
    fun unsubscribeStopsNotificationsAndDuplicateResponsesRemainSingleEntries() = withRepository { repository, _ ->
        var notifications = 0
        val unsubscribe = repository.subscribe { notifications++ }
        val record = HistoryPersistence.Record("same", "/response/same")
        repository.append(listOf(record, record))
        unsubscribe()
        repository.append(listOf(record))
        assertEquals(1, notifications)
        assertEquals(listOf(record), repository.load())
    }

    private fun withRepository(action: (HistoryRepository, java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("kttp-history-repository").toFile()
        val file = directory.resolve("history.json")
        try {
            action(HistoryRepository { file }, file)
        } finally {
            directory.deleteRecursively()
        }
    }
}
