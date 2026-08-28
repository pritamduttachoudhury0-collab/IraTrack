package com.iratrack.app.providers

import com.iratrack.app.data.*

object ProviderRegistry {
    val capabilities = listOf(
        Capability(ProviderId.OPENAI, true, "Provider API dependent", false, true,
            "Uses official organization usage/cost endpoints when the supplied credential has access."),
        Capability(ProviderId.ANTHROPIC, true, "Official API dependent", true, true,
            "Adapter boundary is ready; only official exposed usage/billing data is accepted."),
        Capability(ProviderId.GEMINI, false, "UNAVAILABLE via this app", false, false,
            "Google documents API usage metadata on generation responses, while billing is handled through Google Cloud/AI Studio. IraTrack does not scrape the dashboard or intercept other applications' requests, so the current adapter cannot provide usage history, billing, or model statistics."),
        Capability(ProviderId.GROQ, false, "UNAVAILABLE via this app", false, false,
            "Groq exposes usage metrics in API responses and organization billing/usage in its console. IraTrack does not intercept other applications' requests, so historical spend and model statistics are unavailable through a documented read-only cross-account endpoint."),
        Capability(ProviderId.DEEPSEEK, true, "Balance snapshot only (official /user/balance)", false, false,
            "No historical cost/usage API is published. IraTrack shows the current balance, correctly labeled as a snapshot, not a spend figure."),
        Capability(ProviderId.XAI, true, "Reported (official Management API)", false, true,
            "Historical per-model daily USD cost via the separate xAI Management API (management-api.x.ai), grouped by model. Requires a Management API key, not the standard inference key."),
        Capability(ProviderId.RUNWAY, true, "Credit-based (official Organization API)", false, true,
            "Credit balance via GET /v1/organization and per-model daily credit usage via POST " +
                "/v1/organization/usage on Runway's dev.runwayml.com Organization API. Reported in " +
                "credits, Runway's native billing unit, not converted to USD.")
    )
}
