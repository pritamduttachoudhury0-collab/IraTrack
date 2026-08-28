package com.iratrack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Provider-specific "How to connect" guide. Native Compose screen (not a
 * WebView/external doc) so it inherits IraTrackTheme automatically -- dark
 * today, and light for free the day a light ColorScheme is added to Theme.kt,
 * since every color here comes from MaterialTheme/ui.Theme tokens rather than
 * being hard-coded.
 */
@Composable
fun GuideScreen(guide: ProviderGuide, back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = back, modifier = Modifier.semantics { contentDescription = "Back" }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
            }
            Column {
                Text(
                    "HOW TO CONNECT",
                    color = Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(guide.provider.label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(guide.intro, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 14.dp))

            guide.steps.forEachIndexed { index, step ->
                GuideStepCard(index + 1, step)
                Spacer(Modifier.height(10.dp))
            }

            VerificationCard(guide.verification)
            Spacer(Modifier.height(10.dp))

            if (guide.troubleshooting.isNotEmpty()) {
                TroubleshootingCard(guide.troubleshooting)
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GuideStepCard(number: Int, step: GuideStep) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Text(number.toString(), color = Color(0xFF061008), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(step.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(8.dp))
        Text(step.body, fontSize = 13.sp, lineHeight = 19.sp)

        step.note?.let {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface2)
                    .padding(10.dp)
            ) {
                Text("Note: ", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Muted)
                Text(it, fontSize = 12.sp, color = Muted)
            }
        }

        step.warning?.let {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Warning.copy(alpha = 0.14f))
                    .padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = "Warning",
                    tint = Warning,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(it, fontSize = 12.sp, color = Warning)
            }
        }
    }
}

@Composable
private fun VerificationCard(step: GuideStep) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(step.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(step.body, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun TroubleshootingCard(items: List<Pair<String, String>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(16.dp)
    ) {
        Text("IF SOMETHING GOES WRONG", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Muted)
        Spacer(Modifier.height(10.dp))
        items.forEachIndexed { index, (symptom, fix) ->
            Text(symptom, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(fix, fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
            if (index != items.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}

/** Small entry point used on the Providers screen for providers with a dedicated guide. */
@Composable
fun HowToConnectLink(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 2.dp)
    ) {
        Text("Need help connecting? ", color = Muted, fontSize = 12.sp)
        Text("How to connect →", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
