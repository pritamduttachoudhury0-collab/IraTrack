# IraTrack Architecture

```text
                    OFFICIAL PROVIDER APIs
                             |
              +--------------+--------------+
              |              |              |
          OpenAI         Anthropic       Gemini ...
              |              |              |
              +--------------+--------------+
                             |
                    ProviderAdapter
                             |
                    Common data model
                             |
              +--------------+--------------+
              |              |              |
           Room DB      Local Analytics   Export
              |              |
          Dashboard     Anomaly Detection
              |
        Offline-first UI

API credentials
       |
CredentialStore
       |
Android Keystore
       |
Encrypted private preferences
```

## Provider adapter contract

Each adapter must:

- authenticate directly with the provider
- request only legitimate official API resources
- parse only returned data
- preserve provider-reported vs estimated status
- return an explicit failure when the API does not expose required data

An adapter must never scrape a web dashboard or infer a bill from incomplete information without marking the result as an estimate.

## Common consumption model

`UsageRecord` supports:

- input units
- output units
- total units
- requests
- model
- USD cost
- cost certainty
- unit kind
- provider source identifier

This avoids turning IraTrack into a token-only tracker.

## Local-first security model

Room stores usage history, never credentials.

CredentialStore uses Android Keystore to protect an AES key and encrypts credentials before writing them to private preferences.

No backend is required for normal application operation.
