package com.iratrack.app.data

enum class CredentialType {
    OPENAI_ADMIN_KEY,
    ANTHROPIC_ADMIN_KEY,
    XAI_MANAGEMENT_KEY,
    // DeepSeek's official GET /user/balance endpoint is authenticated with the
    // same ordinary API key created in the DeepSeek Platform console that's used
    // for chat completions -- there is no separate "admin" or "management" key
    // for DeepSeek, so this is intentionally its own case rather than being
    // lumped into STANDARD_API_KEY, purely so the Providers screen can give a
    // DeepSeek-specific hint instead of a generic one.
    DEEPSEEK_API_KEY,
    STANDARD_API_KEY
}

fun ProviderId.credentialType(): CredentialType = when (this) {
    ProviderId.OPENAI -> CredentialType.OPENAI_ADMIN_KEY
    ProviderId.ANTHROPIC -> CredentialType.ANTHROPIC_ADMIN_KEY
    ProviderId.DEEPSEEK -> CredentialType.DEEPSEEK_API_KEY
    // xAI's historical usage/cost data is only reachable through the separate
    // Management API (management-api.x.ai), authenticated with a Management API
    // key created in the xAI Console -- a different credential from the normal
    // xai-... inference key used for chat completions.
    ProviderId.XAI -> CredentialType.XAI_MANAGEMENT_KEY
    else -> CredentialType.STANDARD_API_KEY
}

fun CredentialType.hintLabel(providerLabel: String): String = when (this) {
    CredentialType.OPENAI_ADMIN_KEY -> "$providerLabel Admin API key"
    CredentialType.ANTHROPIC_ADMIN_KEY -> "$providerLabel Admin API key"
    CredentialType.XAI_MANAGEMENT_KEY -> "$providerLabel Management API key (not your inference key)"
    CredentialType.DEEPSEEK_API_KEY -> "$providerLabel API key (from platform.deepseek.com)"
    CredentialType.STANDARD_API_KEY -> "$providerLabel API credential"
}
