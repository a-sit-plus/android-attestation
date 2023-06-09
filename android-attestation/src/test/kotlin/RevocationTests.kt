package at.asitplus.attestation.android

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


const val TEST_STATUS_LIST_PATH = "../android-key-attestation/server/src/test/resources/status.json"

// Certificate generated by TestDPC with RSA Algorithm and StrongBox Security Level
val TEST_CERT = """
        -----BEGIN CERTIFICATE-----
        MIIB8zCCAXqgAwIBAgIRAMxm6ak3E7bmQ7JsFYeXhvcwCgYIKoZIzj0EAwIwOTEMMAoGA1UEDAwDVEVFMSkwJwYDVQQFEyA0ZjdlYzg1N2U4MDU3
        NDdjMWIxZWRhYWVmODk1NDk2ZDAeFw0xOTA4MTQxOTU0MTBaFw0yOTA4MTExOTU0MTBaMDkxDDAKBgNVBAwMA1RFRTEpMCcGA1UEBRMgMzJmYmJi
        NmRiOGM5MTdmMDdhYzlhYjZhZTQ4MTAzYWEwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQzg+sx9lLrkNIZwLYZerzL1bPK2zi75zFEuuI0fIr3
        5DJND1B4Z8RPZ3djzo3FOdAObqvoZ4CZVxcY3iQ1ffMMo2MwYTAdBgNVHQ4EFgQUzZOUqhJOO7wttSe9hYemjceVsgIwHwYDVR0jBBgwFoAUWlnI
        9iPzasns60heYXIP+h+Hz8owDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAgQwCgYIKoZIzj0EAwIDZwAwZAIwUFz/AKheCOPaBiRGDk7L
        aSEDXVYmTr0VoU8TbIqrKGWiiMwsGEmW+Jdo8EcKVPIwAjAoO7n1ruFh+6mEaTAukc6T5BW4MnmYadkkFSIjzDAaJ6lAq+nmmGQ1KlZpqi4Z/VI=
        -----END CERTIFICATE-----
        """.trimIndent()


class RevocationTestFromGoogleSources : FreeSpec({

    "custom implementation" - {

        val factory = CertificateFactory.getInstance("X509")
        val cert =
            factory.generateCertificate(ByteArrayInputStream(TEST_CERT.toByteArray(StandardCharsets.UTF_8))) as X509Certificate
        val serialNumber = cert.serialNumber

        "load Test Serial" {

            AndroidAttestationChecker.RevocationList.from(File(TEST_STATUS_LIST_PATH).inputStream())
                .isRevoked(serialNumber).shouldBeTrue()
        }

        "test cache" - {

            "without HTTP Expiry Header" {
                var requestCounter = 0
                val client = MockEngine { _ ->
                    requestCounter++
                    respond(withExpiry = false)
                }.setup()
                val times = 1000
                repeat(times) {
                    AndroidAttestationChecker.RevocationList.fromGoogleServer(client)
                }
                requestCounter shouldBe times
            }

            "without HTTP Expiry Header" {
                var requestCounter = 0
                val client = MockEngine { _ ->
                    requestCounter++
                    respond(withExpiry = true)
                }.setup()
                val times = 1000
                repeat(times) {
                    AndroidAttestationChecker.RevocationList.fromGoogleServer(client)
                }
                requestCounter shouldBe 1
            }
        }


        "load Bad Serial" {
            AndroidAttestationChecker.RevocationList.from(File(TEST_STATUS_LIST_PATH).inputStream()).isRevoked(
                BigInteger.valueOf(0xbadbeef)
            ).shouldBeFalse()
        }


    }
})

private fun MockRequestHandleScope.respond(withExpiry: Boolean) = respond(
    content = ByteReadChannel(File(TEST_STATUS_LIST_PATH).readBytes()),
    status = HttpStatusCode.OK,
    headers = if (withExpiry) headersOf(
        HttpHeaders.ContentType to listOf("application/json"),
        HttpHeaders.Expires to listOf(GMTDate(getTimeMillis() + 3600_000).toHttpDate())
    ) else headersOf(HttpHeaders.ContentType to listOf("application/json"))
)

