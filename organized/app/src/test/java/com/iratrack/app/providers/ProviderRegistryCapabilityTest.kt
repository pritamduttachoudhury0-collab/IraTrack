package com.iratrack.app.providers

import com.iratrack.app.data.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderRegistryCapabilityTest {

    @Test
    fun official_api_scaffolds_are_never_marked_as_supporting_usage_or_models() {
        ProviderRegistry.capabilities.forEach { capability ->
            val adapter = Adapters.get(capability.provider)
            if (adapter is OfficialApiAdapterScaffold) {
                assertFalse(
                    "${capability.provider} uses OfficialApiAdapterScaffold, so usage must be unavailable",
                    capability.usage
                )
                assertFalse(
                    "${capability.provider} uses OfficialApiAdapterScaffold, so models must be unavailable",
                    capability.models
                )
            }
        }
    }

}
