# IraTrack

## Private AI Spend

IraTrack is a local-first Android control panel for understanding AI-provider usage and spending without requiring an IraTrack account or backend.

### Product principles

1. **Credentials stay on the device.**
2. **Provider data is never fabricated.**
3. **Estimated costs are visibly distinguished from provider-reported costs.**
4. **Provider-specific behavior stays inside provider adapters.**
5. **Different consumption units are first-class data: tokens, requests, credits, seconds, images, characters and other units.**
6. **Previously synchronized information remains usable offline.**
7. **No IraTrack telemetry, advertising, remote logging or credential proxy.**
8. **Provider websites are not scraped.**

## Included

- Premium dark Compose dashboard
- Total / today / 7-day / 30-day spend
- Provider detail screens
- Provider capability matrix
- Local anomaly detection
- Model analytics when model data actually exists
- Local history
- Periodic WorkManager synchronization
- Local notification channel for future anomaly notifications
- CSV export containing non-secret usage data
- Android Keystore credential encryption
- Destructive Delete Everything action
- Adapter architecture for OpenAI, Anthropic, Gemini, Groq, DeepSeek, xAI and Runway
- Conservative OpenAI organization-cost adapter
- Safe adapter scaffolds for providers whose current official usage/billing contract still needs implementation/verification

## Important implementation boundary

This project intentionally does **not** pretend every provider exposes the same billing API.

For example, some providers expose usage in dashboards or require organization/project/billing context. IraTrack therefore uses a capability model and refuses to turn unavailable information into fake numbers.

The next adapter work should be based only on the provider's current official API documentation.

## Security

API credentials are stored outside Room. They are encrypted with AES/GCM using a key generated and held by Android Keystore.

Credentials are not intended to appear in:

- Logcat
- exceptions
- exports
- notifications
- analytics
- URLs
- ordinary Room tables

The application has a Delete Everything action that removes credentials and local usage records.

## Build

Open the project in Android Studio. Gradle configuration is included.

Application ID:

`com.iratrack.app`

Version:

`1.0.0`

Minimum Android version:

API 26

## Roadmap

- Complete verified official adapters for every provider that exposes the necessary API
- Add richer historical charts
- Add provider/model filtering
- Add custom date ranges
- Add JSON export
- Add import/backup of non-secret usage data
- Add configurable anomaly thresholds
- Add notification permission flow
- Add tests for every parser and security component
- Add GitHub Actions
- Add public provider capability documentation

## Name

**IraTrack** is the product name.

**Private AI Spend** is the product descriptor.


## Current implementation status

This is not a UI-only scaffold. The application contains working local persistence, secure credential storage, synchronization scheduling, analytics, export, anomaly detection, and real organization-level integrations for OpenAI and Anthropic where their official Admin APIs provide the required data.

For providers where the official API does not provide the needed historical cross-account billing surface, the app reports the limitation instead of scraping a dashboard or inventing numbers. This is an intentional product feature, not a missing placeholder.

OpenAI organization costs and usage APIs require administrative access; Anthropic's organization Usage and Cost APIs likewise require Admin API access. Provider support should therefore be configured with the correct credential type.

## Build JVM

The Android module targets JVM 17 consistently for Java and Kotlin/KSP.
This matches builders using JDK 17 and avoids the
`Inconsistent JVM-target compatibility` error between `compileDebugJavaWithJavac`
and `kspDebugKotlin`.
