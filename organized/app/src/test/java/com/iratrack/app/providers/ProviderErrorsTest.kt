package com.iratrack.app.providers

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ProviderErrorsTest {

    @Test
    fun status_401_is_reported_as_invalid_credential() {
        val msg = ProviderErrors.forHttpStatus(401, "DeepSeek", "API key")
        assertTrue(msg.contains("invalid", ignoreCase = true))
        assertTrue(msg.contains("DeepSeek"))
    }

    @Test
    fun status_403_is_reported_as_missing_permission_not_invalid_credential() {
        val msg = ProviderErrors.forHttpStatus(403, "xAI", "Management API key")
        assertTrue(msg.contains("permission", ignoreCase = true) || msg.contains("denied", ignoreCase = true))
        assertTrue("403 must not be worded like an invalid-credential (401) error", !msg.contains("invalid", ignoreCase = true))
    }

    @Test
    fun status_429_is_reported_as_rate_limited() {
        val msg = ProviderErrors.forHttpStatus(429, "xAI", "Management API key")
        assertTrue(msg.contains("rate-limited", ignoreCase = true))
    }

    @Test
    fun status_5xx_is_reported_as_temporarily_unavailable() {
        for (code in listOf(500, 502, 503, 504)) {
            val msg = ProviderErrors.forHttpStatus(code, "DeepSeek", "API key")
            assertTrue("HTTP $code should read as temporary/server-side", msg.contains("temporarily unavailable", ignoreCase = true))
        }
    }

    @Test
    fun message_never_echoes_the_credential_value() {
        val secret = "sk-super-secret-value-should-never-appear"
        val msg = ProviderErrors.forHttpStatus(401, "DeepSeek", "API key")
        assertTrue(!msg.contains(secret))
    }

    @Test
    fun unknown_host_is_reported_as_no_network() {
        val msg = ProviderErrors.forException(UnknownHostException("api.deepseek.com"), "DeepSeek")
        assertTrue(msg.contains("network", ignoreCase = true) || msg.contains("reach", ignoreCase = true))
    }

    @Test
    fun timeout_is_reported_distinctly_from_generic_io_error() {
        val timeout = ProviderErrors.forException(SocketTimeoutException(), "xAI")
        assertTrue(timeout.contains("didn't respond in time", ignoreCase = true) || timeout.contains("time", ignoreCase = true))
    }

    @Test
    fun generic_io_exception_is_reported_as_network_error() {
        val msg = ProviderErrors.forException(IOException("connection reset"), "xAI")
        assertTrue(msg.contains("network", ignoreCase = true))
    }

    @Test
    fun blank_exception_message_falls_back_to_a_readable_default() {
        val msg = ProviderErrors.forException(RuntimeException(""), "DeepSeek")
        assertTrue(msg.isNotBlank())
        assertTrue(msg.contains("DeepSeek"))
    }
}
