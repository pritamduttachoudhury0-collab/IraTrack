package com.iratrack.app.ui

import com.iratrack.app.data.ProviderId

/**
 * A single guide step. [warning] renders with the app's warning styling
 * (e.g. "don't paste your inference key here"); everything else is a plain
 * numbered instruction with an optional supporting note.
 */
data class GuideStep(
    val title: String,
    val body: String,
    val note: String? = null,
    val warning: String? = null
)

data class ProviderGuide(
    val provider: ProviderId,
    val intro: String,
    val steps: List<GuideStep>,
    val verification: GuideStep,
    val troubleshooting: List<Pair<String, String>> // (symptom, fix)
)

/**
 * Content here intentionally mirrors exactly what `DeepSeekAdapter`,
 * `XaiAdapter`, and `RunwayAdapter` (in providers/Adapters.kt) actually call
 * and require -- see docs/PROVIDER_CAPABILITY_POLICY.md. If any adapter's
 * endpoint, credential type, or capability changes, update this content in
 * the same change so the guide never drifts from the implementation.
 */
object GuideContent {

    val deepSeek = ProviderGuide(
        provider = ProviderId.DEEPSEEK,
        intro = "DeepSeek gives IraTrack your current account balance, not a day-by-day spending " +
            "history. This guide covers getting a key, adding it, and what the balance number does " +
            "and doesn't tell you.",
        steps = listOf(
            GuideStep(
                title = "Get a DeepSeek API key",
                body = "Sign in at platform.deepseek.com, open API Keys, and create a new key. " +
                    "This is an ordinary DeepSeek API key -- the same kind used to send chat " +
                    "requests -- not a separate \"admin\" or \"billing\" credential. DeepSeek only " +
                    "shows the full key once, so copy it immediately.",
                note = "If you already use this key elsewhere (e.g. in an app that calls the " +
                    "DeepSeek chat API), the same key works here too."
            ),
            GuideStep(
                title = "Add it to IraTrack",
                body = "Open Providers, select DeepSeek, and paste the key into the field labeled " +
                    "\"DeepSeek API key (from platform.deepseek.com)\", then tap STORE.",
                warning = "Paste only the key itself -- not a curl command, not your DeepSeek " +
                    "account email or password. The field accepts one plain value."
            ),
            GuideStep(
                title = "Understand what you'll see",
                body = "DeepSeek doesn't publish a historical spending API, so IraTrack cannot show " +
                    "a chart of past DeepSeek spend the way it does for OpenAI or Anthropic. What " +
                    "you get instead is a live snapshot: your current account balance, refreshed " +
                    "every time you synchronize.",
                note = "IraTrack labels this snapshot \"Account balance,\" separate from \"spend.\" " +
                    "It will never show DeepSeek numbers on the 7-day spend chart or add them into " +
                    "totals as if they were historical cost."
            )
        ),
        verification = GuideStep(
            title = "What success looks like",
            body = "After synchronizing, the DeepSeek row shows a status like \"current balance " +
                "retrieved,\" and its detail screen shows your balance labeled as an account " +
                "balance snapshot, not a spend total."
        ),
        troubleshooting = listOf(
            "\"Rejected the API key as invalid\"" to
                "The key was mistyped, has extra whitespace, or was revoked/rotated in the " +
                "DeepSeek console. Create a fresh key and re-enter it.",
            "\"Rate-limited\"" to
                "You're synchronizing very frequently. Wait a bit and try again -- IraTrack's " +
                "background sync already runs periodically on its own.",
            "\"Temporarily unavailable\"" to
                "DeepSeek's servers are having trouble; this isn't something wrong on your end. " +
                "Try again later.",
            "Network error" to
                "Check your device's connection. IraTrack needs network access to reach " +
                "api.deepseek.com."
        )
    )

    val xai = ProviderGuide(
        provider = ProviderId.XAI,
        intro = "xAI keeps two separate credential systems: an inference key for calling Grok, " +
            "and a Management API key for account/billing operations. IraTrack needs the second " +
            "one -- it's the only way to pull real historical daily cost per model.",
        steps = listOf(
            GuideStep(
                title = "Create a Management API key (not your inference key)",
                body = "Sign in at console.x.ai. Under Settings → Users, confirm your account has " +
                    "\"Management Keys Read + Write\" permission. Then go to Settings → Management " +
                    "Keys and create a new key.",
                warning = "This is a different credential from the xai-... key you'd use to call " +
                    "the chat API. A regular inference key will not work here -- xAI's Management " +
                    "API rejects it."
            ),
            GuideStep(
                title = "Permissions this key needs",
                body = "The Management API key needs read access to your team's billing data " +
                    "(the usage/cost endpoints). IraTrack only ever reads billing information -- it " +
                    "never creates, edits, or deletes API keys, teams, or payment methods through " +
                    "this key, even though the Management API can technically do those things.",
                note = "Least privilege: if the xAI Console lets you scope a key to specific " +
                    "permissions when creating it, grant only billing/usage read access rather than " +
                    "full account management."
            ),
            GuideStep(
                title = "About team configuration",
                body = "xAI tracks usage and billing per team, not per account. IraTrack does not " +
                    "ask you to enter a team ID -- it automatically resolves the correct team from " +
                    "the Management API key itself, since each key is created inside one specific " +
                    "team.",
                note = "If your xAI account belongs to more than one team, make sure you create " +
                    "the Management API key from inside the team whose spending you want IraTrack " +
                    "to track -- not a different one."
            ),
            GuideStep(
                title = "Add it to IraTrack",
                body = "Open Providers, select xAI / Grok, and paste the Management API key into " +
                    "the field labeled \"xAI Management API key (not your inference key)\", then tap " +
                    "STORE.",
                warning = "Don't paste your team ID, org ID, or a curl command here -- just the key " +
                    "value. IraTrack resolves the team on its own."
            ),
            GuideStep(
                title = "What xAI data IraTrack shows",
                body = "IraTrack pulls the last 30 days of real, provider-reported daily cost in " +
                    "USD, grouped by model, from xAI's Management API. This is genuine historical " +
                    "cost data -- not an estimate calculated from token counts.",
                note = "xAI's Management API also exposes a separate prepaid credit balance " +
                    "endpoint. IraTrack does not read it yet, so your xAI prepaid credit balance " +
                    "will not appear here -- only historical daily cost."
            )
        ),
        verification = GuideStep(
            title = "What success looks like",
            body = "After synchronizing, the xAI row shows a status like \"synchronized N reported " +
                "daily cost records.\" Its detail screen and the Analytics tab then show real " +
                "per-model xAI cost for the last 30 days, mixed in with your other providers' spend."
        ),
        troubleshooting = listOf(
            "\"Rejected the Management API key as invalid\"" to
                "You may have pasted your xai-... inference key by mistake, or the Management key " +
                "was revoked/rotated. Create a fresh Management API key at console.x.ai → Settings " +
                "→ Management Keys.",
            "\"Accepted the key but denied this request\" / missing permission" to
                "The account that created the key needs \"Management Keys Read + Write\" " +
                "permission, and the key itself needs billing-read access. Check console.x.ai → " +
                "Settings → Users and → Management Keys.",
            "\"Did not resolve to a team/organization ID\"" to
                "This usually means the key is scoped oddly or was created outside any team. " +
                "Recreate it from inside the specific xAI team you want tracked.",
            "\"Couldn't find billing data for this team\"" to
                "The team the key resolved to may no longer exist, or you may have switched teams " +
                "in the xAI Console since creating the key. Recreate the key from the current team.",
            "Rate-limited or temporarily unavailable" to
                "Wait a bit and try again -- this is usually momentary and not specific to your " +
                "account.",
            "Network error" to
                "Check your device's connection. IraTrack needs network access to reach " +
                "management-api.x.ai, which is a different host from the inference API."
        )
    )

    val runway = ProviderGuide(
        provider = ProviderId.RUNWAY,
        intro = "Runway has two completely separate credit pools and two separate sites: the " +
            "consumer web app (app.runwayml.com) and the developer API (dev.runwayml.com). " +
            "IraTrack only ever talks to the developer API, using the same key your app already " +
            "uses to generate video, image, or audio content.",
        steps = listOf(
            GuideStep(
                title = "Set up a developer organization and add credits",
                body = "Sign in at dev.runwayml.com and create an organization if you don't already " +
                    "have one. Runway's API requires a minimum $10 prepaid credit top-up (at " +
                    "$0.01/credit) before any request -- including IraTrack's balance/usage " +
                    "requests -- will succeed.",
                note = "This prepaid balance is separate from any Standard, Pro, or Max subscription " +
                    "credits on the consumer web app. They never share a pool."
            ),
            GuideStep(
                title = "Create an API key",
                body = "In the developer portal, open the API Keys tab and create a new key. Runway " +
                    "shows the full key value only once, so copy it immediately.",
                warning = "This is Runway's ordinary organization-scoped API key -- the same one used " +
                    "for generation requests. Runway does not have a separate admin or billing-only " +
                    "credential the way OpenAI, Anthropic, or xAI do."
            ),
            GuideStep(
                title = "Add it to IraTrack",
                body = "Open Providers, select Runway, and paste the key into the field labeled " +
                    "\"Runway API credential,\" then tap STORE.",
                warning = "Paste only the key itself -- not a curl command, not your Runway account " +
                    "email or password."
            ),
            GuideStep(
                title = "What Runway data IraTrack shows",
                body = "IraTrack pulls your current credit balance and the last 30 days of per-model " +
                    "daily credit usage from Runway's Organization API. Both are shown in credits, " +
                    "Runway's own billing unit -- not converted to a dollar figure -- because the " +
                    "credit-to-dollar rate can vary by contract, and a converted number would look " +
                    "like an authoritative provider-reported cost when it's really IraTrack's own " +
                    "arithmetic.",
                note = "Runway labels this cost status \"credit-based\" everywhere in the app, the " +
                    "same way DeepSeek's balance is kept separate from \"spend.\""
            )
        ),
        verification = GuideStep(
            title = "What success looks like",
            body = "After synchronizing, the Runway row shows a status like \"synchronized the " +
                "current credit balance and N daily per-model credit usage record(s).\" Its detail " +
                "screen shows figures in credits, not USD."
        ),
        troubleshooting = listOf(
            "\"Rejected the API key as invalid\"" to
                "The key was mistyped, has extra whitespace, or was revoked/rotated in the developer " +
                "portal. Create a fresh key at dev.runwayml.com and re-enter it.",
            "\"Accepted the key but denied this request\"" to
                "Confirm you're using a key from the developer portal (dev.runwayml.com), not " +
                "anything from the consumer web app, and that the organization has completed its " +
                "minimum $10 credit top-up.",
            "Balance shows but usage is empty, or vice versa" to
                "IraTrack shows whichever of the two calls succeeds even if the other fails -- check " +
                "the sync status message for the specific reason the other one didn't return data.",
            "Rate-limited or temporarily unavailable" to
                "Wait a bit and try again -- this is usually momentary and not specific to your " +
                "account.",
            "Network error" to
                "Check your device's connection. IraTrack needs network access to reach " +
                "api.dev.runwayml.com, which is a different host from Runway's consumer web app."
        )
    )

    fun forProvider(provider: ProviderId): ProviderGuide? = when (provider) {
        ProviderId.DEEPSEEK -> deepSeek
        ProviderId.XAI -> xai
        ProviderId.RUNWAY -> runway
        else -> null
    }
}
