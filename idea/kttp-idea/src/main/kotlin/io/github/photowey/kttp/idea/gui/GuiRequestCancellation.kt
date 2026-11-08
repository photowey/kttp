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

package io.github.photowey.kttp.idea.gui

import okhttp3.Call
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A request-scoped cancellation handle, including cancellation before the HTTP call starts.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/11/05
 */
class GuiRequestCancellation {
    private val cancelled = AtomicBoolean()
    private val activeCall = AtomicReference<Call?>()
    private val activeAction = AtomicReference<(() -> Unit)?>()

    val isCancelled: Boolean get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        activeCall.get()?.cancel()
        activeAction.get()?.invoke()
    }

    /**
     * Attaches one cancellation action, invoking it immediately if already cancelled.
     * Close the returned handle to detach this action without resetting cancellation.
     */
    internal fun attachAction(action: () -> Unit): AutoCloseable {
        check(activeAction.compareAndSet(null, action)) { "Cancellation handle already owns an execution" }
        if (cancelled.get()) action()
        return AutoCloseable { activeAction.compareAndSet(action, null) }
    }

    internal fun attach(call: Call) {
        check(activeCall.compareAndSet(null, call)) { "Cancellation handle already owns an HTTP call" }
        if (cancelled.get()) call.cancel()
    }

    internal fun detach(call: Call) {
        activeCall.compareAndSet(call, null)
    }
}
