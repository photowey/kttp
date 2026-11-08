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

package io.github.photowey.kttp.idea.toolbar

import io.github.photowey.kttp.idea.actions.AddCustomRequestAction
import io.github.photowey.kttp.idea.actions.AddDeleteRequestAction
import io.github.photowey.kttp.idea.actions.AddGetRequestAction
import io.github.photowey.kttp.idea.actions.AddHeadRequestAction
import io.github.photowey.kttp.idea.actions.AddOptionsRequestAction
import io.github.photowey.kttp.idea.actions.AddPatchRequestAction
import io.github.photowey.kttp.idea.actions.AddPostRequestAction
import io.github.photowey.kttp.idea.actions.AddPutRequestAction

/**
 * Compatibility stub for old IDE/sandbox state that may reference
 * `KttpAddRequestActions$Post`, `KttpAddRequestActions$Get`, etc.
 * Inner classes delegate to the corresponding [io.github.photowey.kttp.idea.actions] implementations.
 * Plugin.xml uses AddGetRequestAction, AddPostRequestAction, etc.; this exists only for ClassNotFoundException.
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/15
 */
@Suppress("unused")
object KttpAddRequestActions {

    /**
     * Exposes the GET request insertion action to the editor toolbar.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    class Get : AddGetRequestAction()

    /**
     * Exposes the POST request insertion action to the editor toolbar.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    class Post : AddPostRequestAction()

    /**
     * Exposes the PUT request insertion action to the editor toolbar.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    class Put : AddPutRequestAction()

    /**
     * Exposes the DELETE request insertion action to the editor toolbar.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    class Delete : AddDeleteRequestAction()

    /**
     * Exposes the PATCH request insertion action to the editor toolbar.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    class Patch : AddPatchRequestAction()

    /**
     * Exposes the HEAD request insertion action to the editor toolbar.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    class Head : AddHeadRequestAction()

    /**
     * Exposes the OPTIONS request insertion action to the editor toolbar.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    class Options : AddOptionsRequestAction()

    /**
     * Exposes the generic HTTP request insertion action to the editor toolbar.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/15
     */
    class Custom : AddCustomRequestAction()
}
