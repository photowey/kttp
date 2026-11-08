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

package io.github.photowey.kttp.idea.run

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.UrlFilter
import com.intellij.openapi.project.Project

/**
 * Console filter: makes URLs and response file paths in Kttp run output clickable.
 * - URLs are made clickable via UrlFilter
 * - Response file paths (after "Response file saved.") are made clickable via KttpResponseFileLinkFilter
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
class KttpConsoleFilterProvider : ConsoleFilterProvider {

    override fun getDefaultFilters(project: Project): Array<Filter> =
        arrayOf(
            UrlFilter(),
            KttpResponseFileLinkFilter(project)
        )
}
