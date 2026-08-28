# Provider Capability Policy

A provider is only marked as fully supported when its current official API exposes the information IraTrack needs.

## Cost states

- `REPORTED`: provider returned the cost.
- `ESTIMATED`: provider returned usage and IraTrack calculated a price from verified pricing.
- `USAGE_ONLY`: usage exists but cost cannot be reliably determined.
- `CREDIT_BASED`: consumption is represented as credits or another provider-specific billing unit.
- `UNAVAILABLE`: the provider does not expose the required information through the legitimate API available to IraTrack.

## Rules

Never:

- scrape provider websites
- bypass provider restrictions
- guess billing values
- label estimates as invoices
- invent model statistics
- claim a provider is supported when only a UI stub exists

The capability matrix must reflect implementation reality.

An `OfficialApiAdapterScaffold` is an explicit unavailable-adapter state: while a provider
uses that scaffold, its registry capability must set `usage = false` and `models = false`.
The Providers screen renders those booleans as `—`; the explanatory `notes` and billing
field must state why the information is unavailable. Do not mark scaffolded providers as
supported merely because the provider's API or console exposes related information that
the scaffold does not retrieve.

## Capability matrix (as of this codebase, verified August 2026)

| Provider  | Usage | Billing                          | Models | Notes |
|-----------|:-----:|-----------------------------------|:------:|-------|
| OpenAI    | ✓ | ✓ REPORTED (Admin key required)   | ✓ | `/v1/organization/costs` + `/v1/organization/usage/completions`. Requires an Admin API key with cost/usage read scope; a standard project key will fail with a permission error, which is surfaced as-is rather than silently retried against another endpoint. |
| Anthropic | ✓ | ✓ REPORTED (Admin key required)   | ✓ | `/v1/organizations/cost_report` + `/v1/organizations/usage_report/messages`. Cost amounts are minor-unit decimal strings (cents); parsing divides by 100, covered by `AnthropicParsingTest`. Requires an Admin API key. |
| Gemini    | — | UNAVAILABLE via this app          | — | Gemini API responses carry per-call usage metadata, but billing is handled through Google Cloud/AI Studio. The current adapter is an `OfficialApiAdapterScaffold`, so IraTrack does not scrape dashboards or intercept other applications' requests; usage history, billing, and model statistics are unavailable through this app. |
| Groq      | — | UNAVAILABLE via this app          | — | Groq exposes usage metrics in API responses and organization billing/usage in its console, but the current adapter is an `OfficialApiAdapterScaffold`. IraTrack does not intercept other applications' requests, so historical spend and model statistics are unavailable through a documented read-only cross-account endpoint. |
| DeepSeek  | — | Balance snapshot only, not a cost figure | — | Official `GET /user/balance` returns current balance per currency (`is_available`, `total_balance`, `granted_balance`, `topped_up_balance`). No historical cost/usage endpoint is published, so IraTrack shows a point-in-time balance labeled `UNAVAILABLE` cost status rather than inventing a spend number. Deliberately does **not** subtract successive balances to estimate spend, since a rising balance from a manual top-up would otherwise show as fabricated negative spend. |
| xAI       | ✓ | ✓ REPORTED (separate Management API key required) | ✓ | Real historical per-model daily USD cost via `POST /v1/billing/teams/{team_id}/usage` on xAI's **Management API** (`management-api.x.ai`), a different product/host from the inference API. Requires a Management API key (created at console.x.ai), not the `xai-...` key used for chat completions; the adapter resolves the team ID itself from `/auth/management-keys/validation`. xAI also publishes a prepaid-credit-balance endpoint (`/v1/billing/teams/{team_id}/prepaid/balance`) that is documented but not yet implemented here, pending confirming the sign convention of its ledger entries. |
| Runway    | ✓ | ✓ CREDIT_BASED (standard API key) | ✓ | `GET /v1/organization` (credit balance) + `POST /v1/organization/usage` (per-model daily credit usage, up to 90 days) on Runway's dev.runwayml.com Organization API, a separate host/product from Runway's consumer web app credits. Authenticated with the ordinary organization-scoped API key (`RUNWAYML_API_SECRET`) -- unlike OpenAI/Anthropic/xAI, Runway has no separate admin/management credential. All figures are reported in credits (Runway's native billing unit) rather than converted to USD, since the credit-to-dollar rate is account/contract-dependent and IraTrack does not want a converted number to look like an authoritative provider-reported cost. The exact per-entry field names in the usage response are inferred from Runway's documented endpoint description rather than a confirmed example payload (Runway does not publish one); the adapter tries several plausible field-name variants and skips any entry it can't parse rather than guessing a value. |

This table is derived directly from `ProviderRegistry.capabilities` and the adapters in `Adapters.kt`. If you change one, change the other — a mismatch between this file and the code is exactly the failure mode this policy exists to prevent.

Re-verification note: OpenAI and Anthropic endpoint shapes, auth headers, and the Anthropic cents-as-decimal-string cost format were checked against current provider documentation. Gemini and Groq were re-searched for any newly published official billing API; none was found. **DeepSeek and xAI were corrected in a previous session** — both were previously scaffolded as fully unavailable, which was wrong: DeepSeek does expose an official (if limited) balance endpoint, and xAI exposes real historical cost through a Management API that is separate from its inference API and easy to miss on a first pass. **Runway was corrected in this session** — it was previously scaffolded as fully unavailable too, on the assumption that Runway's usage/billing lived only in its developer portal UI; that was wrong. Runway's dev.runwayml.com Organization API exposes both a credit balance endpoint and a per-model daily credit usage endpoint, reachable with the same ordinary API key already used for generation requests. This is a point-in-time check, not a guarantee — provider APIs do change, and a contributor adding or correcting support for any provider should update both this table and `ProviderRegistry` together.

## In-app setup guides (DeepSeek, xAI, Runway)

DeepSeek, xAI, and Runway each have a dedicated "How to connect" guide reachable from the
Providers screen (`ui/GuideContent.kt`, rendered by `ui/GuideScreen.kt`). Their content
was re-verified against official docs alongside this table and must stay in sync with
whatever this table says: if DeepSeek's, xAI's, or Runway's endpoint, credential type, or
capability changes here, update `GuideContent.kt` in the same change. OpenAI and
Anthropic intentionally have no guide entry point — their existing single-field Admin
API key flow is considered self-explanatory.
