# IraTrack Implementation Status

## Fully implemented

### Core application
- Compose Android UI
- Dashboard
- Provider manager
- Provider detail
- Analytics
- Security/data ownership screen
- Local history
- Offline display
- CSV export
- Delete Everything

### Local storage
- Room usage database
- Provider synchronization state
- Android Keystore-backed AES/GCM credential encryption
- Credentials are not stored in the usage database

### Synchronization
- WorkManager periodic synchronization
- Manual synchronization
- Network-required background work

### Provider integrations

#### OpenAI
Implemented against organization administration APIs:
- organization costs
- organization completions usage
- daily buckets
- model usage
- provider-reported costs

Requires an OpenAI Admin API key with appropriate read access.

#### Anthropic
Implemented against organization administration APIs:
- cost report
- message usage report
- daily buckets
- model usage where returned
- provider-reported costs

Requires an Anthropic Admin API key.

#### Gemini
The adapter is intentionally marked unavailable for historical cross-account billing.
Gemini API responses expose usage metadata for calls made through the API, but IraTrack is not a proxy and does not generate paid requests simply to measure them. Billing is handled through Google's billing system.

#### Groq
The adapter is intentionally marked unavailable for historical cross-application billing. Groq exposes usage/billing information through its console and usage metadata in API responses, but IraTrack does not intercept unrelated requests.

#### DeepSeek
Real adapter against the official `GET /user/balance` endpoint. Returns a current-balance
snapshot per currency, not historical cost (DeepSeek doesn't publish that), so it's stored
with cost status `UNAVAILABLE` and labeled as an account balance, not spend.

#### xAI
Real adapter against xAI's separate Management API (`management-api.x.ai`). Retrieves 30
days of actual reported daily cost grouped by model via
`POST /v1/billing/teams/{team_id}/usage`, using a Management API key (not the standard
inference key) that the adapter also uses to self-resolve the team ID.

#### Runway
Real adapter (`RunwayAdapter`/`RunwayParsing`) against Runway's dev.runwayml.com Organization
API: `GET /v1/organization` for the current credit balance and `POST /v1/organization/usage`
for the last 30 days of per-model daily credit usage. Uses the ordinary organization-scoped
API key (`RUNWAYML_API_SECRET`) -- Runway has no separate admin/management credential.
Everything is stored with `CostStatus.CREDIT_BASED` and `UnitKind.CREDITS`, in credits rather
than converted to USD, since the credit-to-dollar rate is account/contract-dependent.

## Product integrity rule

"Supported" means "IraTrack can legally and technically retrieve the necessary data through an official provider API."

A UI card alone does not count as provider support.

## Changes made this session (implemented, not build-verified)

No Android SDK, emulator, or network access was available in the environment that made
these changes, so none of the following has been run through `./gradlew` or against a
live provider. They are code-reviewed and internally consistent, not compiled or tested
end-to-end. Treat this whole section as **IMPLEMENTED BUT NOT LIVE-TESTED** until you've
run `./gradlew test assembleDebug` yourself.

- **Fixed a real deduplication bug**: `usage_records` had no unique constraint on
  `sourceId`, so `OnConflictStrategy.IGNORE` never actually prevented a re-sync from
  duplicating the whole fetched range — only the always-unique autoincrement `id` was
  unique. Added a unique index on `sourceId` plus a Room migration (`MIGRATION_1_2`,
  DB version 1→2) that removes any duplicates already on-device before creating the
  index, instead of wiping local history.
- **JSON export** added (`ExportManager.json` / `.jsonText`), alongside the existing
  CSV export, both on the Security screen. Export text-building was split from file
  I/O (`csvText`/`jsonText`) specifically so it's unit-testable without a `Context`.
- **Provider adapter parsing is now unit-testable**: OpenAI and Anthropic response
  parsing was extracted out of the network-calling adapter classes into
  `OpenAiParsing`/`AnthropicParsing` objects that take a JSON body string and return
  `List<UsageRecord>`. Added `OpenAiParsingTest` and `AnthropicParsingTest` with fixture
  JSON matching each provider's documented response shape, including a test that pins
  down the Anthropic cents-as-decimal-string → dollars conversion.
- **Test infrastructure was actually missing**: `AnalyticsTest.kt` existed but
  `app/build.gradle.kts` declared no `junit` dependency, so it could not run. Added
  `junit:junit`, `org.json:json` (a real JVM implementation of the `org.json.*` API,
  needed because the Android stub jar throws on every call under the plain unit test
  task), and `kotlinx-coroutines-test`.
- Expanded `AnalyticsTest` with real anomaly-detection cases (a genuine spike, normal
  variation that should *not* fire, no-baseline-yet, and usage-only rows that must never
  be treated as a cost spike) and a reported-vs-estimated cost split test.
- Added `ExportManagerTest` covering CSV escaping, JSON null-handling for missing
  optional fields, and an explicit assertion that neither export format ever contains
  credential-shaped strings.
- OpenAI and Anthropic endpoint URLs, required headers, and response shapes (including
  the Anthropic cost `amount` cents-as-string format) were checked against current
  provider documentation; see `PROVIDER_CAPABILITY_POLICY.md` for details and its
  re-verification note.

## Corrections made this session (real providers added, not stubs)

- **DeepSeek**: was previously scaffolded as fully unavailable. That was wrong.
  DeepSeek's official `GET /user/balance` is real and documented, and now has a real
  adapter (`DeepSeekAdapter`/`DeepSeekParsing`). It only returns a current balance
  snapshot, not historical cost, so it's stored with `CostStatus.UNAVAILABLE` on the
  cost field and a clear "Account balance" label rather than being shown as spend.
- **xAI**: was previously scaffolded as fully unavailable on the theory that xAI only
  exposes per-request cost on live inference calls. That was also wrong — xAI has a
  separate **Management API** (`management-api.x.ai`, distinct from the `api.x.ai`
  inference host) with a real historical daily-cost-by-model endpoint
  (`POST /v1/billing/teams/{team_id}/usage`). `XaiAdapter`/`XaiParsing` now implement
  this: it resolves the team ID from the Management API key itself
  (`/auth/management-keys/validation`), then pulls 30 days of USD cost grouped by
  model. This requires the user to create a **Management API key** in the xAI Console
  — a different credential from the normal `xai-...` inference key — which is why
  `CredentialType.XAI_MANAGEMENT_KEY` was added and the Providers screen now says
  "Management API key (not your inference key)" for xAI specifically.
  xAI's prepaid-credit-balance endpoint is documented but intentionally not yet wired
  up, since its ledger `amount` sign convention wasn't confident enough to encode
  without risking a misleading number.
- **Runway**: was previously scaffolded as fully unavailable on the theory that
  usage/billing only exists in the developer portal UI. That was wrong — Runway's
  Organization API (on `dev.runwayml.com`, a separate host from the consumer web app)
  exposes `GET /v1/organization` (credit balance, confirmed field name `creditBalance`
  from Runway's own published setup snippets) and `POST /v1/organization/usage`
  (per-model daily credit usage, up to 90 days per request, per the Organization API
  reference). `RunwayAdapter`/`RunwayParsing` now implement both. Runway does not
  publish an example response body for the usage endpoint, so its per-entry field
  names are inferred from the documented shape and parsed defensively across several
  plausible variants (mirroring the existing `request_count`/`num_requests` fallback in
  `AnthropicParsing`) rather than guessed as one fixed shape — an entry that matches
  none of them is skipped, not fabricated. Both endpoints report in Runway's native
  credits (`CostStatus.CREDIT_BASED`, `UnitKind.CREDITS`), not converted to USD, since
  the $0.01/credit rate is not guaranteed uniform across every account/contract.
  Authenticated with Runway's ordinary organization-scoped API key — unlike xAI, Runway
  has no separate management/admin credential, so `CredentialType.STANDARD_API_KEY`
  already covered it correctly and needed no change.

## Changes made this session (DeepSeek & xAI setup guides)

Same caveat as the section above: no Android SDK/emulator was available in this
environment, so the Compose UI (`GuideScreen.kt`, the "How to connect" link on the
Providers screen) is implemented and reviewed but not rendered or click-tested on a
device/emulator. The pure-JVM pieces (`ProviderErrors`, the adapters' `sync()` against a
local `MockWebServer`) were written to run under `./gradlew test`, but that command
itself was not run here either — treat this whole section as **IMPLEMENTED BUT NOT
LIVE-TESTED** until `./gradlew test assembleDebug` has actually been run.

- **Added dedicated "How to connect" guides for DeepSeek, xAI, and Runway**
  (`ui/GuideContent.kt`, `ui/GuideScreen.kt`). OpenAI and Anthropic keep their existing
  single-field connection flow unchanged; no guide entry point was added for them, or for
  Gemini/Groq. The Providers screen shows a "Need help connecting? How to connect →"
  link only when `GuideContent.forProvider(selected)` is non-null. The Runway guide covers
  the developer-portal/consumer-app split, the $10 minimum prepaid credit requirement, and
  that IraTrack shows credits rather than a converted USD figure.
- **Guide content is written from, and cross-referenced against, the real adapters** in
  `Adapters.kt` and against current official documentation (re-checked August 2026):
  DeepSeek's `GET /user/balance` (ordinary API key from platform.deepseek.com, no
  separate admin credential), and xAI's Management API (`management-api.x.ai`,
  `/auth/management-keys/validation`, `POST /v1/billing/teams/{team_id}/usage`,
  Management API key created under Console → Settings → Management Keys, distinct from
  the `xai-...` inference key). No adapter behavior needed to change to match current
  docs — both were already correct.
- **The DeepSeek guide is explicit that IraTrack shows a current balance snapshot, not
  historical spend**, matching the existing `CostStatus.UNAVAILABLE` labeling in
  `DeepSeekParsing`. The xAI guide explains the Management API key, the least-privilege
  billing-read permission it needs, and that IraTrack resolves the team automatically
  instead of asking the user for a team ID.
- **Added `CredentialType.DEEPSEEK_API_KEY`** so the Providers screen's credential field
  shows a DeepSeek-specific hint ("DeepSeek API key (from platform.deepseek.com)")
  instead of the generic "API credential" label it fell back to before. No change to how
  the credential is stored, used, or its underlying type (still an ordinary API key).
- **Improved DeepSeek/xAI error messages** (`providers/ProviderErrors.kt`): HTTP 401 →
  invalid credential, 403 → missing permission (worded distinctly from 401), 429 → rate
  limited, 5xx → provider temporarily unavailable, `UnknownHostException` /
  `SocketTimeoutException` / other `IOException` → distinct network-unavailable wording.
  xAI's team-validation step keeps its own more specific 401/403/team-missing messages
  (mentioning Management Keys specifically) on top of this shared classifier. No message
  ever includes the raw credential or a raw server response body.
- **Added a `baseUrl` constructor parameter to `DeepSeekAdapter` and `XaiAdapter`**,
  defaulting to their real hosts (`Adapters` still constructs both with no arguments, so
  production behavior is unchanged). This is purely a test seam so `DeepSeekAdapterTest`
  and `XaiAdapterTest` can exercise the actual `sync()` HTTP call, error branches, and
  message wording against a local `MockWebServer`, instead of only testing the
  `*Parsing` objects the way the previous session's tests did.
- **New tests**: `ProviderErrorsTest` (pure classifier logic), `DeepSeekAdapterTest` and
  `XaiAdapterTest` (credential validation, successful sync, invalid credential, missing
  permission, missing team configuration for xAI, API/server failure, network failure,
  and — for DeepSeek — an explicit assertion that a balance response is never marked
  `CostStatus.REPORTED`). Added the `mockwebserver` test dependency for this.
- **Runway tests**: `RunwayParsingTest` (balance parsing, `null` on an unrecognized
  response shape rather than a fabricated 0, zero-credit days kept as real data, an
  entry with no recognizable credit field skipped rather than guessed, and the
  alternate-field-name fallback) and `RunwayAdapterTest` (both endpoints succeeding,
  either endpoint failing independently without losing the other's data, invalid
  credential, and network failure), using the same `baseUrl` constructor-parameter test
  seam as `DeepSeekAdapter`/`XaiAdapter`.

## Changes made this session (Runway)

Same caveat as the sections above: no Android SDK/emulator/network access was available in
this environment, so none of this has been run through `./gradlew` or against a live Runway
organization. It is code-reviewed and internally consistent with Runway's published
documentation, not compiled or tested end-to-end. Treat this section as **IMPLEMENTED BUT
NOT LIVE-TESTED** until `./gradlew test assembleDebug` has actually been run, and until the
first real sync has been checked against a live Runway response — particularly the
`POST /v1/organization/usage` field names, which are the one part of this integration that
couldn't be confirmed against a published example payload (see `RunwayParsing`'s doc comment
and the capability table's Runway row for exactly what's confirmed vs. inferred).

- Replaced the `RunwayAdapter` scaffold with a real adapter, mirroring the
  `DeepSeekAdapter`/`XaiAdapter` structure (a `baseUrl` test seam, network calls kept
  thin, parsing extracted into a pure `RunwayParsing` object).
- Updated `ProviderRegistry.capabilities`' Runway entry and `PROVIDER_CAPABILITY_POLICY.md`'s
  Runway row to reflect the real implementation instead of "unavailable via this app."
- `CredentialType.credentialType()` needed no change — Runway already fell through to
  `STANDARD_API_KEY`, which is the correct credential type for it.

## Remaining product work

The project can still be expanded with:
- provider-specific CSV import
- richer charts
- custom date ranges
- configurable anomaly thresholds
- more OpenAI usage categories
- more Anthropic grouping dimensions
- automated provider capability discovery where official APIs permit it
- GitHub CI/release automation
- instrumentation tests for the Room migration and the Keystore-backed CredentialStore
  (these need an emulator/device, which was not available in this session)
