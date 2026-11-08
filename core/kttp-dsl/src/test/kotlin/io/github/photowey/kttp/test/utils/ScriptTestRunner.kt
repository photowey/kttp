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

import io.github.photowey.kttp.core.context.KttpContext
import io.github.photowey.kttp.dsl.builder.KttpBuilder
import io.github.photowey.kttp.script.Kttp

/**
 * Applies the script block with [Kttp] as receiver without a separate request execution phase.
 * Script statements and eager reads of delegated request values still run normally.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class ScriptStatement(val context: KttpContext = testKttpContext()) {

    private var isScriptApplied = false

    /**
     * Runs [buildScript] to register requests without a subsequent execution phase.
     * Receiver is [Kttp] (script class), so GET/POST/... and `val x by GET(...)` work like in a real script.
     */
    fun applyScript(buildScript: Kttp.() -> Unit): ScriptStatement {
        require(!isScriptApplied) { "Script is already applied" }
        TestKttp(context.builderFactory.createKttpBuilder()).apply(buildScript)
        isScriptApplied = true
        return this
    }
}

/**
 * Test-only concrete [Kttp] to run script blocks with the same receiver as .kttp.kts.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
private class TestKttp(kttpBuilder: KttpBuilder) : Kttp(kttpBuilder)
