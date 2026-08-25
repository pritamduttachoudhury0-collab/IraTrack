#IraTrack

Private AI Spend — Local-First Android AI Usage & Cost Tracker

IraTrack is an open-source, local-first Android application for tracking AI-provider usage, spending, balances, and consumption without requiring an IraTrack account or backend.

The goal is simple:

«Your AI spending data should belong to you.»

IraTrack keeps credentials and usage history on the device and communicates directly with supported provider APIs.

---

Status: Experimental / Not Fully Tested

Important: IraTrack is currently an experimental indie-developer project.

This repository is not presented as production-ready software.

I am developing IraTrack independently, with no development budget. Because of that, I cannot afford paid API credits or subscriptions for every provider supported by the application.

That creates an important limitation:

I cannot personally live-test every provider integration against a paid account.

Some parts of the application have been implemented and covered by automated/unit tests, but real-world provider integrations still require testing with valid credentials and accounts.

Therefore:

- Do not assume every provider integration works perfectly.
- Do not use IraTrack as your sole source of billing information.
- Always verify important spending information against the provider's own dashboard or billing system.
- Provider APIs can change without warning.
- Some providers require administrative or management credentials.
- Some providers expose usage but not historical billing.
- Some integrations are implemented but have not been live-tested by me.

If you encounter a bug, please report it. That is one of the main reasons this repository is public.

---

What is IraTrack?

IraTrack is designed to provide one local dashboard for AI services such as:

- OpenAI
- Anthropic
- Gemini
- Groq
- DeepSeek
- xAI
- Runway

Instead of sending your credentials or usage information to an IraTrack server, the application communicates directly with providers.

There is no IraTrack backend.

Core principles

1. Credentials stay on the device.
2. Provider data is never fabricated.
3. Reported costs are distinguished from estimates.
4. Unavailable information is explicitly marked unavailable.
5. Provider-specific API behavior stays inside provider adapters.
6. Different consumption units are supported.
7. Previously synchronized information remains available offline.
8. No IraTrack telemetry or advertising.
9. No provider website scraping.
10. No fake billing numbers just to make the dashboard look complete.

---

Current Features

Dashboard

- Total spending
- Today
- 7-day view
- 30-day view
- Provider breakdown
- Usage history
- Local analytics
- Anomaly detection

Provider integrations

The application currently contains adapter implementations or explicit capability boundaries for:

Provider| Current status
OpenAI| Implemented — Admin API required
Anthropic| Implemented — Admin API required
xAI| Implemented — Management API key required
Runway| Implemented — credit-based
DeepSeek| Balance snapshot only
Gemini| Currently unavailable through IraTrack
Groq| Currently unavailable through IraTrack

Provider support is deliberately conservative.

If IraTrack cannot obtain reliable information through an official API, it does not scrape a dashboard or invent a number.

---

Privacy & Security

IraTrack is designed around local-first operation.

API credentials are stored separately from the Room database and protected using:

Android Keystore + AES/GCM encryption

Credentials are not intended to appear in:

- Logcat
- Exceptions
- CSV exports
- JSON exports
- Notifications
- Analytics
- URLs
- Ordinary Room database tables

The application also includes a Delete Everything action for removing local credentials and usage data.

No IraTrack server is required for normal operation.

---

How the Architecture Works

             Official Provider APIs
                      |
       +--------------+--------------+
       |              |              |
     OpenAI       Anthropic        xAI
       |              |              |
       +--------------+--------------+
                      |
               ProviderAdapter
                      |
               Common Data Model
                      |
          +-----------+-----------+
          |           |           |
       Room DB    Analytics     Export
          |           |
      Dashboard   Anomaly Detection
          
      API Credentials
             |
      CredentialStore
             |
      Android Keystore
             |
      Encrypted Storage

Each provider adapter is responsible for:

- Authentication
- Calling legitimate official APIs
- Parsing provider responses
- Preserving reported/estimated status
- Returning explicit errors when information is unavailable

---

Important: IraTrack Does Not Fake Numbers

This is one of the most important design decisions in the project.

Different providers expose completely different billing systems.

For example:

- One provider may expose historical USD costs.
- Another may expose usage but no historical cost.
- Another may expose credits rather than dollars.
- Another may require an organization Admin API key.
- Another may only expose information through a dashboard.

IraTrack does not pretend these systems are equivalent.

The internal cost states include:

- "REPORTED"
- "ESTIMATED"
- "USAGE_ONLY"
- "CREDIT_BASED"
- "UNAVAILABLE"

If reliable information cannot be obtained, IraTrack says so.

---

Provider Capability Details

OpenAI

Historical organization usage and cost integration is implemented using official organization APIs.

Requirement: appropriate OpenAI Admin API credentials.

A normal project API key may not have the permissions required.

Anthropic

Organization usage and cost integration is implemented using official organization APIs.

Requirement: Anthropic Admin API credentials.

xAI

xAI's billing integration uses the Management API, not the normal inference API.

Requirement: xAI Management API key.

This is different from the normal "xai-..." inference key.

Runway

Runway usage is represented in credits, rather than being converted into an assumed USD value.

The application intentionally avoids pretending that a universal credit-to-dollar conversion exists for every account or contract.

DeepSeek

DeepSeek currently provides a balance endpoint that can be accessed through its official API.

IraTrack treats this as a balance snapshot, not historical spending.

It deliberately does not subtract balances over time to "guess" spending, because account top-ups could make that calculation misleading.

Gemini & Groq

The current implementation does not provide the required historical cross-account billing functionality through an official API.

Therefore IraTrack does not scrape their websites or intercept requests from other applications.

These providers remain explicitly unavailable through the current implementation.

---

Testing Status

This is where I want to be completely transparent.

Some parts of the project have automated tests covering things such as:

- Analytics
- Anomaly detection
- Export generation
- JSON parsing
- Provider response parsing
- Error handling
- Provider capability rules
- Credential-shaped data leaking into exports
- Local HTTP adapter behavior

However:

Automated tests are not the same thing as testing against real paid provider accounts.

I do not currently have the budget to maintain paid accounts/credits across every supported provider.

The UI has also not been exhaustively tested across every Android device, Android version, provider account type, and API response variation.

So the correct classification is:

«Experimental software — partially tested, not production-certified.»

---

Building

Requirements

- Android Studio
- JDK 17
- Android SDK
- Gradle wrapper included in the repository

Application ID:

com.iratrack.app

Minimum Android version:

API 26

Target SDK:

API 35

The project is configured for JVM 17 for both Java and Kotlin/KSP.

Build with:

./gradlew assembleDebug

Run tests with:

./gradlew test

For a more complete check:

./gradlew test assembleDebug

---

Contributing

Contributions are welcome.

In particular, help with the following would be extremely valuable:

- Testing provider integrations with real accounts
- Finding API changes
- Testing different Android versions
- Testing different provider account configurations
- Improving UI
- Adding automated tests
- Reviewing security
- Improving documentation
- Adding verified provider adapters

Please don't "fix" missing data by guessing.

If an API doesn't expose something, the correct solution is to document the limitation or find an official API that provides it.

---

Reporting Bugs

When reporting a provider integration problem, please include:

- Provider
- Android version
- IraTrack version
- Credential type used
- Relevant error message
- What you expected
- What actually happened

Never post your API key, secret, token, or other credentials in a GitHub issue.

---

Roadmap

- [ ] More real-world provider testing
- [ ] Complete verification of every provider adapter
- [ ] Richer historical charts
- [ ] Provider/model filtering
- [ ] Custom date ranges
- [ ] JSON backup/import improvements
- [ ] Configurable anomaly thresholds
- [ ] Notification permission flow
- [ ] More security tests
- [ ] GitHub Actions CI
- [ ] Expanded provider documentation
- [ ] Community-contributed provider testing

---

Why Make It Open Source?

IraTrack is being built as an independent project without a development budget.

I could keep the repository private until everything is perfectly polished.

That would also mean nobody could help test it.

Instead, I'm publishing it openly while being honest about its current state:

It works in parts. It has real architecture and real implementations. It also has untested areas.

The purpose of publishing now is to let other developers:

- Inspect the code
- Test it
- Find bugs
- Suggest improvements
- Contribute provider testing
- Improve the architecture
- Help turn an experimental project into something reliable

---

License

IraTrack is open source under the license included in this repository.

---

Disclaimer

IraTrack is an independent project and is not affiliated with or endorsed by OpenAI, Anthropic, Google, Groq, DeepSeek, xAI, Runway, or any other provider mentioned in this repository.

IraTrack should not be considered an authoritative billing system.

For financial or billing decisions, always verify information with the relevant provider.

---

Project Status

Version: "1.0.0"

Stage: Experimental / Open Development

Developer: Independent / Indie Developer

Budget: $0 for paid provider API testing

Philosophy: Build honestly. Never fabricate data. Improve through real testing and community feedback.

«If something doesn't work, please tell me. That's more useful than pretending it works.»
