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

package io.github.photowey.kttp.core.extension.okhttp

import okhttp3.OkHttpClient
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Replaces the TLS trust configuration with a store containing the supplied X.509 certificate.
 * The platform trust roots are not merged into this store; hostname verification is unchanged.
 */
fun OkHttpClient.Builder.addX509Certificate(certFile: File) {
    val certificate: Certificate = certFile.inputStream()
        .use(CertificateFactory.getInstance("X.509")::generateCertificate)

    val keyStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        setCertificateEntry("trusted_cert", certificate)
    }

    val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply {
            init(keyStore)
        }
        .trustManagers
        .first() as X509TrustManager

    val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }

    sslSocketFactory(sslContext.socketFactory, trustManager)
}

/** Uses the file-based keystore as the TLS trust source. This installs trust managers, not client key managers. */
fun OkHttpClient.Builder.addKeyStore(keyStoreFile: File, keystorePassword: String) {
    val keyStore: KeyStore = KeyStore.getInstance(
        keyStoreFile,
        keystorePassword.toCharArray()
    )

    val trustManagerFactory: TrustManagerFactory = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply {
            init(keyStore)
        }

    val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, trustManagerFactory.trustManagers, null)
    }

    val trustManager: X509TrustManager = trustManagerFactory
        .trustManagers
        .first { it is X509TrustManager } as X509TrustManager

    sslSocketFactory(
        sslContext.socketFactory,
        trustManager
    )
}

/**
 * Disables certificate-chain validation for this builder; hostname verification remains enabled.
 * Use only for requests where accepting untrusted certificates is intentional.
 */
fun OkHttpClient.Builder.insecure() {
    val trustAllCerts = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

        override fun getAcceptedIssuers(): Array<out X509Certificate>? = emptyArray()
    }

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustAllCerts), SecureRandom())

    sslSocketFactory(sslContext.socketFactory, trustAllCerts)
}