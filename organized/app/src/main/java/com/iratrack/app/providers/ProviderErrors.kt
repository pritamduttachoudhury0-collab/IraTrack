package com.iratrack.app.providers

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Shared, provider-agnostic translation from an HTTP status code or network
 * exception into a short, actionable message: what happened, and how to fix
 * it. Deliberately generic and reviewed -- it never echoes a raw server
 * response body (which could itself contain account-identifying detail) and
 * never touches the credential string itself.
 *
 * [credentialHint] is a short phrase identifying what kind of credential the
 * provider expects (e.g. "API key", "Management API key"), used to make the
 * 401/403 messages concrete without hard-coding provider-specific copy here.
 */
object ProviderErrors {

    fun forHttpStatus(code: Int, providerLabel: String, credentialHint: String): String = when (code) {
        401 -> "$providerLabel rejected the $credentialHint as invalid. Re-check that you copied the whole value with nothing missing, extra, or truncated, then re-enter it."
        403 -> "$providerLabel accepted the $credentialHint but denied this specific request -- it doesn't have the permission this sync needs."
        404 -> "$providerLabel could not find what this request asked for. Double-check any account/team configuration IraTrack is using."
        408 -> "$providerLabel did not respond in time. This is usually temporary -- try synchronizing again shortly."
        429 -> "$providerLabel rate-limited this request. Wait a little before synchronizing again."
        in 500..599 -> "$providerLabel is temporarily unavailable (server error on $providerLabel's side). Try again later."
        else -> "$providerLabel returned an unexpected response (HTTP $code)."
    }

    fun forException(t: Throwable, providerLabel: String): String = when (t) {
        is UnknownHostException -> "Couldn't reach $providerLabel -- check that your device has a network connection."
        is SocketTimeoutException -> "$providerLabel didn't respond in time. Check your connection and try again."
        is IOException -> "A network error interrupted the request to $providerLabel. Check your connection and try again."
        else -> t.message?.takeIf { it.isNotBlank() } ?: "$providerLabel synchronization failed for an unknown reason."
    }
}
