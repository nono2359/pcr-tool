package cn.wthee.pcrtool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import cn.wthee.pcrtool.data.enums.AppThemeMode
import cn.wthee.pcrtool.data.preferences.SettingPreferencesKeys
import kotlinx.coroutines.launch

@Composable
fun ThemeModeSetting(modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modes = listOf(AppThemeMode.SYSTEM, AppThemeMode.DAY, AppThemeMode.NIGHT)

    Column(modifier = modifier) {
        Text(
            text = "テーマ",
            color = MaterialTheme.colorScheme.onSurface,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            modes.forEach { mode ->
                val selected = MainActivity.themeMode == mode
                TextButton(
                    onClick = {
                        MainActivity.themeMode = mode
                        scope.launch {
                            context.dataStoreSetting.edit { it[SettingPreferencesKeys.SP_THEME_MODE] = mode }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppThemeMode.label(mode), maxLines = 1)
                }
            }
        }
    }
}
