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

package io.github.photowey.kttp.idea.toolwindow

import io.github.photowey.kttp.idea.gui.ApiKeyPlacement
import io.github.photowey.kttp.idea.gui.AuthSpec
import io.github.photowey.kttp.idea.gui.ValueRef
import io.github.photowey.kttp.idea.gui.GuiValueRefCodec
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.Base64
import javax.swing.*

/**
 * Auth 标签页：配置认证
 *
 * @author photowey
 * @version 1.0.0
 * @since 2026/02/21
 */
class AuthTabPanel : JPanel(BorderLayout()) {

    private val summaryLabel = JLabel("Sources: none").apply {
        foreground = UIUtil.getContextHelpForeground()
        border = JBUI.Borders.emptyTop(4)
    }

    /**
     * Lists the authentication editors offered by the request composer.
     *
     * @author photowey
     * @version 1.0.0
     * @since 2026/02/21
     */
    private enum class AuthType(private val label: String) {
        INHERIT("Inherit"), BEARER("Bearer Token"), BASIC("Basic Auth"), API_KEY("API Key");

        override fun toString(): String = label
    }

    private val authTypeComboBox = JComboBox(AuthType.entries.toTypedArray())
    private var loadedAuth: AuthSpec? = null

    private val bearerTokenField = JTextField().apply {
        columns = 24
        toolTipText = "Enter Bearer Token"
    }

    private val usernameField = JTextField().apply {
        columns = 24
        toolTipText = "Enter username"
    }

    private val passwordField = JPasswordField().apply {
        columns = 24
        toolTipText = "Enter password"
    }

    private val apiKeyNameField = JTextField().apply {
        columns = 24
        toolTipText = "API Key name (e.g., X-API-Key)"
    }

    private val apiKeyValueField = JTextField().apply {
        columns = 24
        toolTipText = "API Key value"
    }

    private val apiKeyPlacementComboBox = JComboBox(ApiKeyPlacement.entries.toTypedArray()).apply {
        toolTipText = "Send the API key in a request header or query parameter"
    }

    private val cardLayout = CardLayout()
    private val contentPanel = JPanel(cardLayout).apply {
        add(JLabel("Inherit from environment"), "inherit")
        add(createBearerPanel(), "bearer")
        add(createBasicAuthPanel(), "basic")
        add(createApiKeyPanel(), "apikey")
    }

    init {
        border = JBUI.Borders.empty(4)

        // 顶部：Auth Type 选择器
        val topPanel = JPanel(BorderLayout()).apply {
            add(JLabel("Auth Type:"), BorderLayout.WEST)
            add(authTypeComboBox, BorderLayout.CENTER)
        }
        val north = JPanel(BorderLayout()).apply {
            add(topPanel, BorderLayout.NORTH)
            add(summaryLabel, BorderLayout.SOUTH)
        }
        add(north, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)

        // Auth Type 变化时切换内容面板
        authTypeComboBox.addActionListener {
            updateContentPanel()
        }

        updateContentPanel()
        refreshSummary()
    }

    private fun updateContentPanel() {
        cardLayout.show(
            contentPanel, when (authTypeComboBox.selectedItem as AuthType) {
                AuthType.INHERIT -> "inherit"
                AuthType.BEARER -> "bearer"
                AuthType.BASIC -> "basic"
                AuthType.API_KEY -> "apikey"
            }
        )
    }

    private fun createBearerPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8)
        panel.add(JPanel(BorderLayout(0, 4)).apply {
            add(JLabel("Token:"), BorderLayout.NORTH)
            add(bearerTokenField, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        return panel
    }

    private fun createBasicAuthPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(8)
        val gbc = GridBagConstraints()
        gbc.insets = java.awt.Insets(4, 4, 4, 4)
        gbc.anchor = GridBagConstraints.WEST

        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JLabel("Username:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(usernameField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        panel.add(JLabel("Password:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(passwordField, gbc)

        return panel
    }

    private fun createApiKeyPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(8)
        val gbc = GridBagConstraints()
        gbc.insets = java.awt.Insets(4, 4, 4, 4)
        gbc.anchor = GridBagConstraints.WEST

        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JLabel("Key Name:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(apiKeyNameField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        panel.add(JLabel("Key Value:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(apiKeyValueField, gbc)

        gbc.gridx = 0
        gbc.gridy = 2
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        panel.add(JLabel("Add to:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(apiKeyPlacementComboBox, gbc)

        return panel
    }

    /**
     * 获取认证 Headers
     */
    fun getAuthHeaders(): Map<String, String> = when (val auth = getAuthSpec { ValueRef.Literal(it) }) {
        is AuthSpec.Bearer -> if (bearerTokenField.text.isBlank()) emptyMap()
        else mapOf("Authorization" to "Bearer ${bearerTokenField.text}")

        is AuthSpec.Basic -> {
            val credentials = "${usernameField.text}:${String(passwordField.password)}"
            if (usernameField.text.isBlank() && passwordField.password.isEmpty()) emptyMap()
            else mapOf("Authorization" to "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray())}")
        }

        is AuthSpec.ApiKey -> if (auth.placement == ApiKeyPlacement.HEADER && auth.keyName.isNotBlank() && apiKeyValueField.text.isNotBlank()) {
            mapOf(auth.keyName to apiKeyValueField.text)
        } else emptyMap()

        AuthSpec.Inherit -> emptyMap()
    }

    fun getAuthSpec(valueParser: (String) -> ValueRef = { ValueRef.Literal(it) }): AuthSpec {
        fun value(input: String, previous: ValueRef?): ValueRef =
            previous?.takeIf { GuiValueRefCodec.toDisplayValue(it) == input } ?: valueParser(input)
        return when (authTypeComboBox.selectedItem as AuthType) {
            AuthType.INHERIT -> AuthSpec.Inherit
            AuthType.BEARER -> bearerTokenField.text.let { token ->
                AuthSpec.Bearer(value(token, (loadedAuth as? AuthSpec.Bearer)?.token))
            }

            AuthType.BASIC -> {
                val username = usernameField.text
                val password = String(passwordField.password)
                val previous = loadedAuth as? AuthSpec.Basic
                AuthSpec.Basic(value(username, previous?.username), value(password, previous?.password))
            }

            AuthType.API_KEY -> {
                val name = apiKeyNameField.text.trim()
                val input = apiKeyValueField.text
                AuthSpec.ApiKey(
                    name, value(input, (loadedAuth as? AuthSpec.ApiKey)?.keyValue),
                    apiKeyPlacementComboBox.selectedItem as ApiKeyPlacement
                )
            }
        }
    }

    fun setAuthSpec(auth: AuthSpec) {
        clearFields()
        when (auth) {
            AuthSpec.Inherit -> authTypeComboBox.selectedItem = AuthType.INHERIT
            is AuthSpec.Bearer -> {
                authTypeComboBox.selectedItem = AuthType.BEARER
                bearerTokenField.text = GuiValueRefCodec.toDisplayValue(auth.token)
            }

            is AuthSpec.Basic -> {
                authTypeComboBox.selectedItem = AuthType.BASIC
                usernameField.text = GuiValueRefCodec.toDisplayValue(auth.username)
                passwordField.text = GuiValueRefCodec.toDisplayValue(auth.password)
            }

            is AuthSpec.ApiKey -> {
                authTypeComboBox.selectedItem = AuthType.API_KEY
                apiKeyNameField.text = auth.keyName
                apiKeyValueField.text = GuiValueRefCodec.toDisplayValue(auth.keyValue)
                apiKeyPlacementComboBox.selectedItem = auth.placement
            }
        }
        loadedAuth = auth
        updateContentPanel()
        refreshSummary()
    }

    /** Compatibility adapter for callers supplying display text. Editor binding uses AuthSpec. */
    @JvmOverloads
    fun setAuth(authType: String, data: Map<String, String>, placement: ApiKeyPlacement = ApiKeyPlacement.HEADER) {
        clearFields()
        apiKeyPlacementComboBox.selectedItem = placement
        authTypeComboBox.selectedItem = when (authType.lowercase()) {
            "bearer", "bearer token" -> AuthType.BEARER
            "basic", "basic auth" -> AuthType.BASIC
            "apikey", "api key" -> AuthType.API_KEY
            else -> AuthType.INHERIT
        }
        bearerTokenField.text = data["token"].orEmpty()
        usernameField.text = data["username"].orEmpty()
        passwordField.text = data["password"].orEmpty()
        apiKeyNameField.text = data["keyName"].orEmpty()
        apiKeyValueField.text = data["keyValue"].orEmpty()
        updateContentPanel()
        refreshSummary()
    }

    private fun clearFields() {
        loadedAuth = null
        bearerTokenField.text = ""
        usernameField.text = ""
        passwordField.text = ""
        apiKeyNameField.text = ""
        apiKeyValueField.text = ""
        apiKeyPlacementComboBox.selectedItem = ApiKeyPlacement.HEADER
    }

    private val changeListeners = mutableListOf<() -> Unit>()

    /**
     * 通知请求工作副本发生变化；脚本写入由调用方的显式操作触发。
     */
    fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
        // 监听所有输入字段变化
        bearerTokenField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }
        })
        usernameField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }
        })
        passwordField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }
        })
        apiKeyNameField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }
        })
        apiKeyValueField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }

            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                refreshSummary(); listener()
            }
        })
        // 监听 Auth Type 变化
        authTypeComboBox.addActionListener { refreshSummary(); listener() }
        apiKeyPlacementComboBox.addActionListener { refreshSummary(); listener() }
    }

    fun setEditable(editable: Boolean) {
        authTypeComboBox.isEnabled = editable
        bearerTokenField.isEditable = editable
        usernameField.isEditable = editable
        passwordField.isEditable = editable
        apiKeyNameField.isEditable = editable
        apiKeyValueField.isEditable = editable
        apiKeyPlacementComboBox.isEnabled = editable
    }

    fun updateValueSourceSummary(summary: String) {
        summaryLabel.text = summary
    }

    private fun refreshSummary() {
        fun sourceLabel(value: String): String = when {
            value.trim().startsWith("\${env:") -> "env"
            value.trim().startsWith("@file:") -> "file"
            value.isBlank() -> "none"
            else -> "literal"
        }

        summaryLabel.text = when (authTypeComboBox.selectedItem as AuthType) {
            AuthType.BEARER -> "Sources: token=${sourceLabel(bearerTokenField.text)}"
            AuthType.BASIC -> "Sources: user=${sourceLabel(usernameField.text)}, pass=${sourceLabel(String(passwordField.password))}"
            AuthType.API_KEY -> "Sources: value=${sourceLabel(apiKeyValueField.text)}"
            else -> "Sources: inherit"
        }
    }
}
