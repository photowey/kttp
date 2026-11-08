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

package io.github.photowey.kttp.dsl

import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.core.context.execution.result.ResultExecutable
import io.github.photowey.kttp.core.printer.println
import kotlin.reflect.KProperty

/**
 * Obtains a delegated executable result through the current script execution context.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class StoredValueDelegate<T>(
    private val context: KttpContext,
    private val executableResult: ResultExecutable<T>,
    private val provider: () -> T?,
) : AbstractValueDelegate<T>() {
    /** Returns the stored non-null value, or executes the holder immediately to initialize it. */
    override fun getValueImpl(thisRef: Any?, property: KProperty<*>): T {
        provider()?.let { return it }
        context.printer.println("Initializing value for property `${property.name}`")
        return executableResult.execute()
    }
}