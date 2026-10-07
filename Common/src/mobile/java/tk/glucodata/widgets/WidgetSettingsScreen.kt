@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package tk.glucodata.widgets

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import tk.glucodata.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    var weatherPermissionGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        weatherPermissionGranted = granted
        if (granted) WidgetUpdater.requestUpdateAll(context)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.widget_settings_existing), style = MaterialTheme.typography.titleMedium)
            ExistingWidgetCard(
                title = stringResource(R.string.widget_classic),
                description = stringResource(R.string.widget_classic_description),
                onSharedSettings = { navController.navigate("settings/notification-display") }
            )
            ExistingWidgetCard(
                title = stringResource(R.string.widget_expressive),
                description = stringResource(R.string.widget_expressive_description),
                onSharedSettings = { navController.navigate("settings/notification-display") }
            )

            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.widget_settings_new), style = MaterialTheme.typography.titleMedium)
            WidgetKind.entries.forEach { kind ->
                var config by remember(kind) { mutableStateOf(WidgetConfigStore.load(context, kind)) }
                WidgetKindSettings(
                    kind = kind,
                    config = config,
                    onLocationPermission = { locationRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    hasLocationPermission = weatherPermissionGranted,
                    onChange = {
                        config = it
                        WidgetConfigStore.save(context, kind, it)
                        WidgetUpdater.requestUpdateAll(context)
                    }
                )
            }

            Text(
                stringResource(R.string.widget_settings_data_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ExistingWidgetCard(title: String, description: String, onSharedSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onSharedSettings, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Text("  " + stringResource(R.string.widget_shared_display_settings))
            }
        }
    }
}

@Composable
private fun WidgetKindSettings(
    kind: WidgetKind,
    config: WidgetConfig,
    onChange: (WidgetConfig) -> Unit,
    hasLocationPermission: Boolean,
    onLocationPermission: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(kind.labelRes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(kind.descriptionRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.widget_background), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BackgroundChip(WidgetBackground.SURFACE, R.string.widget_bg_surface, config, onChange)
                BackgroundChip(WidgetBackground.RANGE, R.string.widget_bg_range, config, onChange)
                BackgroundChip(WidgetBackground.TRANSPARENT, R.string.widget_bg_none, config, onChange)
            }

            if (config.background != WidgetBackground.TRANSPARENT) {
                Text(stringResource(R.string.widget_opacity_value, config.opacity))
                Slider(
                    value = config.opacity.toFloat(),
                    onValueChange = { onChange(config.copy(opacity = (it / 5f).toInt() * 5)) },
                    valueRange = 20f..100f
                )
            }

            Text(stringResource(R.string.widget_text_size_value, config.textScale))
            Slider(
                value = config.textScale.toFloat(),
                onValueChange = { onChange(config.copy(textScale = (it / 5f).toInt() * 5)) },
                valueRange = 70f..140f
            )

            HorizontalDivider()
            WidgetSwitch(stringResource(R.string.widget_show_arrow), config.showArrow) { onChange(config.copy(showArrow = it)) }
            WidgetSwitch(stringResource(R.string.widget_show_delta), config.showDelta) { onChange(config.copy(showDelta = it)) }
            WidgetSwitch(stringResource(R.string.widget_show_time), config.showTime) { onChange(config.copy(showTime = it)) }
            WidgetSwitch(stringResource(R.string.widget_show_unit), config.showUnit) { onChange(config.copy(showUnit = it)) }
            WidgetSwitch(stringResource(R.string.widget_range_colors), config.rangeColors) { onChange(config.copy(rangeColors = it)) }

            if (kind == WidgetKind.CLOCK_WEATHER) {
                HorizontalDivider()
                WidgetSwitch(stringResource(R.string.widget_show_date), config.showDate) {
                    onChange(config.copy(showDate = it))
                }
                WidgetSwitch(stringResource(R.string.widget_show_weather), config.showWeather) {
                    onChange(config.copy(showWeather = it))
                }
                if (config.showWeather) {
                    WidgetSwitch(stringResource(R.string.widget_show_weather_icon), config.showWeatherIcon) {
                        onChange(config.copy(showWeatherIcon = it))
                    }
                    WidgetSwitch(stringResource(R.string.widget_show_temperature), config.showTemperature) {
                        onChange(config.copy(showTemperature = it))
                    }
                    if (!hasLocationPermission) {
                        TextButton(onClick = onLocationPermission) { Text(stringResource(R.string.widget_weather_permission)) }
                    }
                    Text(stringResource(R.string.widget_weather_note),
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (kind.hasGraph) {
                Text(stringResource(R.string.widget_graph_hours), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1,3,6,12,24).forEach { hours ->
                        FilterChip(
                            selected = config.graphHours == hours,
                            onClick = { onChange(config.copy(graphHours = hours)) },
                            label = { Text(stringResource(R.string.widget_hours_short, hours)) }
                        )
                    }
                }
                WidgetSwitch(stringResource(R.string.widget_target_band), config.showTargetBand) {
                    onChange(config.copy(showTargetBand = it))
                }
            }

            if (kind.hasStats) {
                Text(stringResource(R.string.widget_period), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        WidgetStatsPeriod.TODAY to R.string.widget_period_today,
                        WidgetStatsPeriod.DAY to R.string.widget_period_24h,
                        WidgetStatsPeriod.WEEK to R.string.widget_period_7d
                    ).forEach { (period, label) ->
                        FilterChip(
                            selected = config.statsPeriod == period,
                            onClick = { onChange(config.copy(statsPeriod = period)) },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundChip(
    background: WidgetBackground,
    label: Int,
    config: WidgetConfig,
    onChange: (WidgetConfig) -> Unit
) {
    FilterChip(
        selected = config.background == background,
        onClick = { onChange(config.copy(background = background)) },
        label = { Text(stringResource(label)) }
    )
}

@Composable
private fun WidgetSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
