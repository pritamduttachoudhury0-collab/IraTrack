# IraTrack fixes applied

This build was patched against the supplied original `IraTrack-1.zip`.

## Fixes

1. **WorkManager retry/failure bug**
   - Removed the unconditional `Result.success()` behavior.
   - Transient network/HTTP 408/429/5xx/rate-limit failures now produce `Result.retry()`.
   - Permanent all-provider failures produce `Result.failure()`.
   - A transient failure is still retried when another provider succeeded; duplicate records remain safe because `sourceId` is unique.

2. **Stale `lastSync` semantics**
   - `lastSync` now means last successful synchronization.
   - A failed attempt preserves the previous successful timestamp instead of making failed data look fresh.
   - Applied to both background and manual synchronization.

3. **Export cleanup**
   - "Delete everything" now removes IraTrack's cached CSV/JSON exports as well as credentials, usage history and provider state.

4. **More stable deduplication IDs**
   - OpenAI cost IDs use the line-item identity instead of result-array position when available.
   - OpenAI usage IDs use model identity instead of result-array position when available.
   - Anthropic cost/usage IDs use the provider's model/description identity when available.

5. **Runway API correctness**
   - Updated the request body from the incorrect `endDate` field to the documented `beforeDate` field.
   - Updated parsing to the current documented `results[].usedCredits[].{model,amount}` response shape, while retaining the old flat-array fallback for compatibility.
   - Added a regression fixture for the current documented response and a request-body assertion.

6. **Manual sync resilience**
   - A provider adapter exception during a manual dashboard sync is now contained and shown as a provider failure instead of escaping the UI coroutine.

7. **Regression tests**
   - Added `SyncPolicyTest` covering success, retry, permanent failure, partial success, and no-credential cases.

## Verification

- The fixed source tree was compared directly against the supplied original tree.
- 9/9 standalone Kotlin behavioral checks for the synchronization policy passed, repeated three times.
- The fixed project contains 66 JUnit test methods across 13 test classes; the full Android/Gradle suite could not be executed because the supplied project has no wrapper script and no local Gradle/dependency cache.
- Static regression checks for every fix passed.
- A source scan found no obvious hard-coded API-key patterns.
- The complete Android Gradle test suite could not be executed in this environment because the supplied project has no `gradlew`/`gradlew.bat` wrapper script and no Gradle distribution/dependency cache was available locally. No live provider credentials were used.
