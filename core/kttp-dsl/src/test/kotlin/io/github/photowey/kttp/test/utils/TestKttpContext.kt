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

package io.github.photowey.kttp.test.utils

import io.github.photowey.kttp.core.context.ClientContextImpl
import io.github.photowey.kttp.core.context.CookiesContext
import io.github.photowey.kttp.core.context.KttpContextImpl
import io.github.photowey.kttp.core.context.NoopCookiesContext
import io.github.photowey.kttp.core.context.createKttpContext
import io.github.photowey.kttp.core.context.store.EnvironmentStore
import io.github.photowey.kttp.core.context.store.NoopEnvironmentStore
import io.github.photowey.kttp.core.interceptor.KttpInterceptor
import io.github.photowey.kttp.core.printer.NoopPrinter
import io.github.photowey.kttp.core.context.storage.InMemoryStorage
import io.github.photowey.kttp.core.context.storage.Storage

/**
 * Creates a minimal [KttpContextImpl] with a no-op printer and, by default, in-memory storage.
 * Its HTTP client is real; registration-only tests avoid network calls by not executing requests.
 */
fun testKttpContext(
    storage: Storage = InMemoryStorage(),
    environmentStore: EnvironmentStore = NoopEnvironmentStore,
    cookiesContext: CookiesContext = NoopCookiesContext,
): KttpContextImpl {
    val printer = NoopPrinter
    val clientContext = ClientContextImpl(KttpInterceptor(printer, null))
    return createKttpContext(
        storage = storage,
        environmentStore = environmentStore,
        cookiesContext = cookiesContext,
        clientContext = clientContext,
        printer = printer,
    )
}
