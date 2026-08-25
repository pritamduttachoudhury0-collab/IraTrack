# IraTrack

Private AI usage & spend tracking for OpenAI, Anthropic, DeepSeek, xAI/Grok, Runway, and more — using official APIs.

IraTrack is a local-first Android application that brings AI usage, cost, billing, and account information from multiple AI providers into one dashboard.

It is designed around one important rule:

«If an AI provider does not officially expose the information through an API that IraTrack can legitimately use, IraTrack does not scrape it, guess it, or pretend it is available.»

IraTrack stores synchronized usage history locally on the device, keeps provider credentials separate from the usage database, and performs provider synchronization directly from the Android application.

---

What IraTrack Does

IraTrack connects directly to supported provider APIs and converts their different response formats into a common usage model.

From one dashboard, you can see:

- Total tracked spend
- Today's spend
- 7-day spend
- 30-day spend
- Spend by provider
- Provider/model information where available
- Token usage
- Request counts
- Provider-reported costs
- Credit-based usage
- Usage anomalies
- Historical synchronized data
- Provider synchronization status

You can also export your locally stored usage history as:

- CSV
- JSON

---

Supported Providers

IraTrack currently knows about seven providers:

Provider| Usage| Cost / Billing| Model Data| Credential
OpenAI| ✅| Reported USD| ✅| Admin API key
Anthropic| ✅| Reported USD| ✅| Admin API key
DeepSeek| ⚠️| Balance snapshot only| —| API key
xAI / Grok| ✅| Reported USD| ✅| Management API key
Runway| ✅| Native credits| ✅| Organization API key
Google Gemini| —| Unavailable| —| Not currently supported
Groq| —| Unavailable| —| Not currently supported

Important distinction

"Listed in IraTrack" does not automatically mean "fully supported."

Some providers expose useful information only inside their dashboards or only as part of individual API responses. That is not enough for IraTrack's cross-account historical tracking model.

Those providers are deliberately marked as unavailable rather than being implemented through scraping or undocumented APIs.

---

Provider Details

OpenAI

IraTrack uses OpenAI's organization-level APIs for:

- Organization costs
- Completions usage
- Daily buckets
- Model/line-item grouping

The current adapter retrieves approximately the last 30 days of data.

Credential

OpenAI requires an appropriate Admin API key with access to the organization usage/cost endpoints.

A normal project API key is not treated as an equivalent credential.

Cost handling

OpenAI cost responses are stored as:

CostStatus.REPORTED

Meaning the cost came directly from the provider rather than being calculated by IraTrack.

---

Anthropic

IraTrack supports Anthropic's organization-level:

- Cost report
- Messages usage report
- Model-level usage information

The adapter handles Anthropic's documented cost representation, including conversion from the provider's minor currency unit into USD.

Credential

An Anthropic Admin API key is required.

Cost handling

Reported costs are stored as:

CostStatus.REPORTED

---

DeepSeek

DeepSeek is intentionally handled differently.

The official API currently provides a user balance endpoint, but it does not provide the historical billing dataset IraTrack would need to reconstruct actual historical spend.

IraTrack therefore retrieves the current account balance.

It does not turn balance changes into fake spending numbers.

What IraTrack shows

Current account balance

rather than:

Historical spend

The balance is therefore stored with:

CostStatus.UNAVAILABLE

and labeled as an account balance.

Why not calculate spending?

Suppose the balance changes from:

$20 → $12

It might look like $8 of spending.

But if the balance changes:

$12 → $30

that could simply be a manual top-up.

IraTrack does not interpret balance movements as billing events because doing so could produce misleading or even negative "spend."

---

xAI / Grok

xAI is supported through its separate Management API.

This is important because xAI's normal inference API and its management/billing API are different systems.

IraTrack uses the Management API to retrieve:

- Team information
- Historical usage
- Daily cost
- Cost grouped by model

The adapter resolves the team ID automatically from the management-key validation response.

Credential

xAI requires an:

xAI Management API key

This is not the same credential as the normal "xai-..." inference API key used for model requests.

Cost handling

xAI returns actual reported USD usage, which IraTrack stores as:

CostStatus.REPORTED

---

Runway

Runway is supported through its Organization API.

IraTrack retrieves:

- Current organization credit balance
- Daily usage
- Model information
- Credit consumption

The current implementation works with Runway's Organization API rather than scraping the Runway website or consumer dashboard.

Billing units

Runway usage is represented in credits.

IraTrack deliberately does not convert those credits into USD.

The reason is simple:

«A converted number could look like an authoritative USD bill even when the applicable credit-to-dollar relationship depends on the account, plan, or contract.»

Therefore Runway records use:

CostStatus.CREDIT_BASED
UnitKind.CREDITS

Credential

Runway uses its ordinary organization-scoped API credential.

There is no separate xAI-style management credential in the current implementation.

---

Google Gemini

Gemini is currently not supported for historical cross-account tracking.

Gemini API responses can expose usage metadata for individual API calls, but IraTrack is not a proxy and does not intercept requests made by other applications.

Billing is handled through Google's own billing systems.

Therefore IraTrack does not:

- Scrape Google dashboards
- Intercept Gemini traffic
- Reconstruct historical billing from unrelated applications

The provider is consequently shown as unavailable.

---

Groq

Groq is currently unavailable for the same fundamental reason.

Groq exposes usage information through API responses and provides billing/usage information through its own console.

IraTrack does not intercept unrelated API requests and does not scrape the console.

Therefore historical cross-account usage and billing are not currently implemented.

---

Architecture

IraTrack uses a provider-adapter architecture.

                 OFFICIAL PROVIDER APIs
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
     OpenAI           Anthropic           xAI
        │                 │                 │
        ├──────────── DeepSeek ─────────────┤
        │                 │                 │
      Runway           Gemini             Groq
        │
        ▼
                  ProviderAdapter
                          │
                          ▼
                  Common Data Model
                          │
              ┌───────────┼───────────┐
              │           │           │
              ▼           ▼           ▼
           Room DB    Analytics     Export
              │
              ▼
          Local Dashboard

Each provider has its own adapter.

The adapter is responsible for:

1. Authentication
2. Calling the provider's official API
3. Parsing the provider response
4. Converting it into IraTrack's common model
5. Reporting provider-specific errors
6. Clearly identifying whether a cost is reported, estimated, usage-only, credit-based, or unavailable

---

Common Usage Model

Different AI providers expose completely different forms of usage data.

IraTrack normalizes them into a common "UsageRecord".

A record can contain:

Provider
Timestamp
USD cost
Input units
Output units
Total units
Unit type
Unit label
Request count
Model
Cost status
Provider source ID

Supported unit types include:

TOKENS
REQUESTS
CREDITS
SECONDS
IMAGES
CHARACTERS
OTHER

This allows IraTrack to track more than just token-based AI services.

---

Cost Status

IraTrack explicitly records how trustworthy a cost value is.

"REPORTED"

The provider directly returned the cost.

Example:

xAI → $1.52

IraTrack stores the provider-reported amount.

---

"ESTIMATED"

The provider supplied usage but not a direct cost, and IraTrack calculated a price using verified pricing information.

Estimated costs are kept separate from provider-reported costs.

---

"USAGE_ONLY"

Usage exists, but a reliable cost cannot be determined.

Example:

Input tokens: 10,000
Output tokens: 2,000
Cost: unavailable

IraTrack does not invent a dollar value.

---

"CREDIT_BASED"

The provider reports usage in credits or another native billing unit.

Example:

Runway
120 credits

The value is preserved as credits instead of being converted into an assumed USD amount.

---

"UNAVAILABLE"

The required billing information is not available through an official API that IraTrack currently uses.

This status is particularly important for providers such as DeepSeek when IraTrack can retrieve a balance but cannot retrieve historical spend.

---

Security

IraTrack is designed as a local-first application.

There is no backend server required for normal operation.

Credential storage

Provider credentials are not stored in the Room usage database.

Credentials are stored separately using:

- Android Keystore
- AES-256
- GCM encryption
- Private application preferences

The encryption key is generated and maintained through the Android Keystore.

Conceptually:

Provider API Key
       │
       ▼
 AES-256-GCM encryption
       │
       ▼
Encrypted private preferences

Android Keystore
       │
       └── protects encryption key

The Room database contains usage records, not raw provider credentials.

---

Local-First Design

IraTrack does not require a central backend to function.

The normal data flow is:

Provider API
     │
     ▼
IraTrack Android App
     │
     ├── Parse
     ├── Normalize
     └── Validate
     │
     ▼
Local Room Database
     │
     ├── Dashboard
     ├── Analytics
     └── Export

Previously synchronized data remains available locally when the network is unavailable.

---

Synchronization

IraTrack supports both manual and scheduled synchronization.

Manual

The Dashboard includes a Synchronize action that checks configured providers and retrieves their available data.

Automatic

IraTrack uses Android WorkManager for periodic synchronization.

The current worker is scheduled approximately every:

6 hours

The worker requires a network connection before running.

Each configured provider is synchronized independently.

A provider failure does not require deleting previously synchronized local history.

---

Duplicate Protection

Provider records have a provider-generated/source-derived identifier.

IraTrack stores that value as a unique "sourceId".

This prevents repeated synchronization of the same provider bucket from creating duplicate local history.

The Room database uses a unique index on:

sourceId

so re-synchronizing an already stored record can safely be ignored.

---

Analytics

IraTrack performs analytics locally.

The analytics layer currently provides:

- Total spend
- Time-range totals
- Token totals
- Request totals
- Reported cost totals
- Estimated cost totals
- Provider comparisons
- Usage anomaly detection

Usage anomalies

IraTrack can flag unusually high recent provider spending compared with its local baseline.

The application reports the increase itself.

It does not claim to know why the increase happened.

For example:

OpenAI
+230% above local baseline

means exactly that — the observed cost was substantially above the calculated local baseline.

It does not mean IraTrack knows which application, person, model, or event caused the increase.

---

Export

IraTrack supports local export of synchronized usage history.

Available formats:

CSV

Useful for:

- Spreadsheets
- Manual analysis
- External data processing

JSON

Useful for:

- Backups of non-secret usage data
- Programmatic processing
- Development/debugging
- Future integrations

Exported usage data does not contain the stored provider credentials.

---

Application Screens

The Android application currently contains four primary areas:

Dashboard
Providers
Analytics
Security

Dashboard

Provides:

- Total tracked spend
- Today / 7-day / 30-day summaries
- Seven-day spend chart
- Usage anomaly alerts
- Provider summaries
- Manual synchronization

Providers

Provides:

- Provider selection
- Credential storage/removal
- Provider details
- Capability matrix
- Connection guides where needed
- Provider synchronization status

Analytics

Provides:

- Spend by provider
- Usage statistics
- Anomaly information
- Local historical analysis

Security

Provides:

- CSV export
- JSON export
- Complete local-data deletion

The Delete Everything action removes stored provider credentials and local usage history.

---

Capability Matrix

IraTrack maintains an explicit capability matrix for providers.

A provider can independently report:

Usage
Billing
Estimation
Models

This prevents the UI from implying that a provider supports functionality that the adapter does not actually implement.

For example:

DeepSeek

Usage: —
Billing: Balance snapshot only
Models: —

is preferable to incorrectly displaying DeepSeek as having full historical billing support.

---

Error Handling

Provider failures are categorized rather than shown as raw technical errors wherever possible.

Examples include:

401 → Invalid credential
403 → Missing permission
429 → Rate limited
5xx → Provider temporarily unavailable
DNS/network failure → Network unavailable
Timeout → Provider did not respond in time

Credentials are never intentionally echoed into error messages.

Raw provider response bodies are not surfaced as user-facing error messages.

---

Testing

The project includes JVM unit tests covering important provider and application behavior.

Tests include:

- OpenAI response parsing
- Anthropic response parsing
- DeepSeek parsing
- xAI parsing
- Runway parsing
- Provider error classification
- Credential-safety behavior
- Analytics
- Anomaly detection
- CSV export
- JSON export
- Provider adapter synchronization using "MockWebServer"

The provider parsing logic is separated from network calls where possible so response handling can be tested against fixture JSON.

---

Technology Stack

IraTrack is an Android application built with:

- Kotlin
- Jetpack Compose
- Material 3
- Room
- KSP
- WorkManager
- OkHttp
- Moshi
- Android Keystore
- Kotlin Coroutines

Build configuration

Application ID: com.iratrack.app
Version:        1.0.0
Minimum SDK:    API 26
Target SDK:     API 35
Compile SDK:    API 35
JVM target:     Java/Kotlin 17

---

Building the Project

Requirements

You should have:

- Android Studio
- Android SDK
- JDK 17
- Android SDK Platform 35
- Gradle wrapper included with the repository

The project is configured to use JVM 17 consistently for Java and Kotlin compilation.

Build

Clone the repository:

git clone https://github.com/your-username/IraTrack.git
cd IraTrack

Then build:

./gradlew assembleDebug

Run the unit tests with:

./gradlew test

Or build and test together:

./gradlew test assembleDebug

On Windows:

.\gradlew.bat test assembleDebug

---

Adding a New Provider

Provider integrations are intentionally isolated behind the "ProviderAdapter" interface.

A new provider should:

1. Implement "ProviderAdapter".
2. Authenticate using the provider's documented authentication mechanism.
3. Call only official API endpoints.
4. Parse the returned response.
5. Convert the data into "UsageRecord".
6. Assign the correct "CostStatus".
7. Declare its actual capabilities in "ProviderRegistry".
8. Add provider-specific tests.
9. Document credential requirements.
10. Update the capability policy documentation.

A provider should not be marked as supported simply because an API exists.

The API must expose the information IraTrack actually needs.

---

Official API Policy

This is one of the project's core design constraints.

IraTrack does not use:

- Web scraping
- Browser automation
- Private dashboard endpoints
- Reverse-engineered APIs
- Undocumented billing endpoints
- Cookie/session extraction
- Traffic interception
- Fake usage calculations presented as provider-reported costs

If an official API does not expose historical billing information, the correct behavior is:

UNAVAILABLE

not:

Let's guess.

This keeps the numbers in IraTrack meaningful.

---

Project Structure

The main application is organized roughly as follows:

app/src/main/java/com/iratrack/app/

├── analytics/
│   └── Analytics.kt
│
├── data/
│   ├── AppDatabase.kt
│   ├── CredentialType.kt
│   ├── Models.kt
│   └── UsageDao.kt
│
├── export/
│   └── ExportManager.kt
│
├── notifications/
│
├── providers/
│   ├── Adapters.kt
│   ├── ProviderAdapter.kt
│   ├── ProviderErrors.kt
│   └── ProviderRegistry.kt
│
├── security/
│   └── CredentialStore.kt
│
├── sync/
│   └── SyncWorker.kt
│
├── ui/
│
└── MainActivity.kt

Documentation is maintained under:

docs/
├── ARCHITECTURE.md
├── IMPLEMENTATION_STATUS.md
└── PROVIDER_CAPABILITY_POLICY.md

---

Project Principles

IraTrack is built around a few simple principles.

1. Accuracy over completeness

It is better to say:

«"This provider does not expose the required data."»

than to show an impressive but unreliable number.

2. Official APIs over hacks

Provider support should survive changes to dashboards and websites because IraTrack communicates with documented APIs.

3. Reported data stays reported

If a provider reports a cost, IraTrack preserves that distinction.

4. Estimates stay estimates

Calculated costs must never be presented as provider invoices.

5. Local-first privacy

Credentials and usage history stay on the user's device unless the user explicitly exports the data.

6. Provider capabilities must match reality

The capability matrix is part of the product contract, not decorative UI.

---

Current Status

IraTrack is a functional Android application rather than a UI-only prototype.

The repository currently contains:

- Working local Room persistence
- Encrypted credential storage
- Provider adapters
- Provider capability registry
- Manual synchronization
- Periodic background synchronization
- Local analytics
- Anomaly detection
- CSV export
- JSON export
- Provider-specific connection guides
- Provider error classification
- Unit tests
- Duplicate synchronization protection
- Real organization-level integrations for supported providers

Some provider integrations are intentionally limited because of the information officially exposed by those providers.

---

Roadmap

Potential future work includes:

- More officially supported providers
- Richer historical charts
- Provider and model filtering
- Custom date ranges
- Non-secret usage-data import/backup
- Configurable anomaly thresholds
- Additional notification functionality
- More comprehensive provider parser tests
- GitHub Actions / CI
- Expanded public provider capability documentation
- Continued verification of provider APIs as they evolve

Provider support will only be expanded where the necessary data can be retrieved legitimately through documented APIs.

---

Contributing

Contributions are welcome.

Before adding a provider integration, please verify the provider's current official documentation.

When contributing:

- Keep credentials out of source control.
- Use documented APIs.
- Do not scrape provider dashboards.
- Do not introduce undocumented endpoints.
- Do not silently estimate unavailable billing data.
- Preserve "CostStatus" semantics.
- Update "ProviderRegistry".
- Update "docs/PROVIDER_CAPABILITY_POLICY.md".
- Add tests for new parsing behavior.
- Document credential requirements.
- Keep provider-specific logic inside the provider layer.

If an API limitation prevents full support, document the limitation instead of working a
