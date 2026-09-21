package za.org.rtc.community.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import za.org.rtc.community.ui.components.RtcCard
import za.org.rtc.community.ui.components.RtcScreenScaffold
import za.org.rtc.community.ui.components.RtcSectionHeader
import za.org.rtc.community.ui.theme.RtcSpacing

@Composable
fun SettingsScreen(
    appVersion: String,
    onClearCache: () -> Unit,
    onBack: () -> Unit,
) {
    RtcScreenScaffold {
        item {
            RtcSectionHeader(
                title = "Settings",
                subtitle = "Application-level controls for this device.",
                trailing = { TextButton(onClick = onBack) { Text("Back") } },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                RtcCard {
                    Text("Language and region", style = MaterialTheme.typography.titleMedium)
                    Text("Uses your device language and regional settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RtcCard {
                    Text("Terms of Service", style = MaterialTheme.typography.titleMedium)
                    Text("Use of RTC Community is subject to the current community terms and policies.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RtcCard {
                    Text("App information", style = MaterialTheme.typography.titleMedium)
                    Text("Version $appVersion", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear app cache")
                }
                Text(
                    "Clearing the app cache removes temporary files only. It does not sign you out or delete your account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
