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

import io.github.photowey.kttp.idea.io.HistoryPersistence.HistoryFile
import io.github.photowey.kttp.idea.io.HistoryPersistence.Record
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Collections

/**
 * Serializes application-wide history access within this repository instance and publishes successful writes.
 * The archive stores absolute paths; presentation may shorten them without changing file associations.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
internal class HistoryRepository(private val stateFile: () -> File) {
    private val json = Json { prettyPrint = false }
    private val listeners = mutableListOf<(List<Record>) -> Unit>()

    @Synchronized
    fun load(): List<Record> = read(stateFile())

    /**
     * Rereads history, normalizes new paths to absolute paths, deduplicates by response path, and retains the newest limit.
     * Persists before notifying subscribers; unreadable existing history is never treated as an empty archive.
     */
    @Synchronized
    fun append(records: List<Record>): List<Record> {
        val file = stateFile()
        val existing = read(file)
        if (records.isEmpty()) return existing
        val merged = (existing + records.map(::absolutePaths))
            .distinctBy { it.responseFilePath }.takeLast(MAX_ENTRIES)
        return persist(file, merged)
    }

    /** Persists an empty history index after validating the old archive; saved response files are not deleted. */
    @Synchronized
    fun clear(): List<Record> {
        val file = stateFile()
        read(file) // A failed read must never authorize overwriting the source archive.
        return persist(file, emptyList())
    }

    /**
     * Subscribes to future successful writes and returns an unsubscribe function; no initial snapshot is delivered.
     * Callbacks run synchronously on the writer thread and should not throw.
     */
    @Synchronized
    fun subscribe(listener: (List<Record>) -> Unit): () -> Unit {
        listeners.add(listener)
        return { unsubscribe(listener) }
    }

    @Synchronized
    private fun unsubscribe(listener: (List<Record>) -> Unit) {
        listeners.remove(listener)
    }

    private fun read(file: File): List<Record> {
        val text = AtomicStateFile.read(file) ?: return emptyList()
        return try {
            snapshot(json.decodeFromString(HistoryFile.serializer(), text).entries)
        } catch (failure: SerializationException) {
            throw StatePersistenceException("Invalid history archive: ${file.absolutePath}", failure)
        } catch (failure: IllegalArgumentException) {
            throw StatePersistenceException("Invalid history archive: ${file.absolutePath}", failure)
        }
    }

    private fun persist(file: File, records: List<Record>): List<Record> {
        val published = snapshot(records)
        AtomicStateFile.replace(file, json.encodeToString(HistoryFile.serializer(), HistoryFile(published)))
        listeners.toList().forEach { it(published) }
        return published
    }

    private fun absolutePaths(record: Record): Record = record.copy(
        responseFilePath = File(record.responseFilePath).absolutePath,
        scriptFilePath = record.scriptFilePath?.let { File(it).absolutePath }
    )

    private fun snapshot(records: List<Record>): List<Record> = Collections.unmodifiableList(records.toList())

    /**
     * Defines the maximum number of execution history entries retained.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/11/05
     */
    private companion object {
        const val MAX_ENTRIES = 100
    }
}
