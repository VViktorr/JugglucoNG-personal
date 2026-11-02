@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package tk.glucodata.widgets

import tk.glucodata.ui.components.AppSlider

import tk.glucodata.ui.util.appButtonShapes
import tk.glucodata.ui.util.appFilterChipShapes
import tk.glucodata.ui.util.appFilterChipColors

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import tk.glucodata.ui.components.StyledSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import tk.glucodata.ui.components.AppTopBar
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
import tk.glucodata.GlucoseWidget
import tk.glucodata.R
import tk.glucodata.widget.ExpressiveWidgetReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<WidgetSettingsKind?>(null) }
    var weatherPermissionGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        weatherPermissionGranted = granted
        if (granted) {
            WidgetUpdater.requestUpdateAll(context)
            ExpressiveWidgetReceiver.updateAll(context)
        }
    }

    BackHandler(enabled = selected != null) { selected = null }

    Scaffold(
        topBar = {
            AppTopBar(
                title = selected?.let { stringResource(it.labelRes) }
                    ?: stringResource(R.string.widget_settings_title),
                onNavigateBack = {
                    if (selected != null) selected = null else navController.popBackStack()
                }
            )
        }
    ) { padding ->
        if (selected == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WidgetSettingsKind.entries.forEach { kind ->
                    WidgetCard(kind = kind, onClick = { selected = kind })
                }
                Text(
                    stringResource(R.string.widget_settings_data_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            val kind = selected!!
            var config by remember(kind) { mutableStateOf(WidgetConfigStore.load(context, kind)) }
            WidgetDetailSettings(
                modifier = Modifier.padding(padding),
                kind = kind,
                config = config,
                hasLocationPermission = weatherPermissionGranted,
                onLocationPermission = { locationRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                onChange = {
                    config = it
                    WidgetConfigStore.save(context, kind, it)
                    refreshWidget(context, kind)
                }
            )
        }
    }
}

@Composable
private fun WidgetCard(kind: WidgetSettingsKind, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(kind.labelRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(kind.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

private fun refreshWidget(context: android.content.Context, kind: WidgetSettingsKind) {
    when (kind) {
        WidgetSettingsKind.BASIC -> GlucoseWidget.refreshSettings()
        WidgetSettingsKind.CHART -> ExpressiveWidgetReceiver.updateAll(context)
        WidgetSettingsKind.RANGE, WidgetSettingsKind.CLOCK_WEATHER ->
            WidgetUpdater.requestUpdateAll(context)
    }
}

@Composable
private fun WidgetDetailSettings(
    modifier: Modifier,
    kind: WidgetSettingsKind,
    config: WidgetConfig,
    onChange: (WidgetConfig) -> Unit,
    hasLocationPermission: Boolean,
    onLocationPermission: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(kind.descriptionRes), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(stringResource(R.string.widget_section_appearance), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.widget_font), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WidgetFontFamily.entries.forEach { family ->
                FilterChip(
                    shapes = appFilterChipShapes(),
                    colors = appFilterChipColors(),
                    selected = config.fontFamily == family,
                    onClick = { onChange(config.copy(fontFamily = family, mainFontFamily = family, detailFontFamily = family)) },
                    label = { Text(stringResource(if (family == WidgetFontFamily.IBM_PLEX) R.string.widget_font_ibm_plex else R.string.widget_font_system)) })
            }
        }
        Text(stringResource(R.string.widget_font_weight), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(300, 400, 500, 600, 700).forEach { weight ->
                FilterChip(
                    shapes = appFilterChipShapes(),
                    colors = appFilterChipColors(),
                    selected = config.primaryFontWeight == weight,
                    onClick = { onChange(config.copy(primaryFontWeight = weight, secondaryFontWeight = weight)) },
                    label = { Text(weight.toString()) })
            }
        }


        Text(if (kind.hasWeather) stringResource(R.string.widget_clock_detail_size_value, config.detailScale) else stringResource(R.string.widget_detail_size_value, config.detailScale))
        AppSlider(
            value = config.detailScale.toFloat(),
            onValueChange = { onChange(config.copy(detailScale = (it / 5f).toInt() * 5)) },
            valueRange = 70f..180f
        )

        Text(if (kind.hasWeather) stringResource(R.string.widget_clock_glucose_size_value, config.glucoseScale) else stringResource(R.string.widget_glucose_size_value, config.glucoseScale))
        AppSlider(
            value = config.glucoseScale.toFloat(),
            onValueChange = { onChange(config.copy(glucoseScale = (it / 5f).toInt() * 5)) },
            valueRange = 70f..180f
        )

        Text(stringResource(R.string.widget_corner_radius_value, config.cornerRadiusDp))
        AppSlider(
            value = config.cornerRadiusDp.toFloat(),
            onValueChange = { onChange(config.copy(cornerRadiusDp = it.toInt().coerceIn(0, 24))) },
            valueRange = 0f..24f,
            steps = 23
        )

        Text(stringResource(R.string.widget_background), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BackgroundChip(WidgetBackground.SURFACE, R.string.widget_bg_surface, config, onChange)
            BackgroundChip(WidgetBackground.RANGE, R.string.widget_bg_range, config, onChange)
            BackgroundChip(WidgetBackground.TRANSPARENT, R.string.widget_bg_none, config, onChange)
        }

        if (config.background != WidgetBackground.TRANSPARENT) {
            Text(stringResource(R.string.widget_opacity_value, config.opacity))
            AppSlider(
                value = config.opacity.toFloat(),
                onValueChange = { onChange(config.copy(opacity = (it / 5f).toInt() * 5)) },
                valueRange = 20f..100f
            )
        }

        HorizontalDivider()
        Text(stringResource(R.string.widget_section_visibility), style = MaterialTheme.typography.titleMedium)
        WidgetSwitch(stringResource(R.string.widget_show_arrow), config.showArrow) {
            onChange(config.copy(showArrow = it))
        }

        if (kind != WidgetSettingsKind.BASIC) {
            WidgetSwitch(stringResource(R.string.widget_range_colors), config.rangeColors) {
                onChange(config.copy(rangeColors = it))
            }
        }

        WidgetSwitch(stringResource(R.string.widget_show_time), config.showTime) {
            onChange(config.copy(showTime = it))
        }

        if (kind != WidgetSettingsKind.BASIC) {
            WidgetSwitch(stringResource(R.string.widget_show_delta), config.showDelta) {
                onChange(config.copy(showDelta = it))
            }
            WidgetSwitch(stringResource(R.string.widget_show_unit), config.showUnit) {
                onChange(config.copy(showUnit = it))
            }


        }

        if (kind.hasWeather) {
            HorizontalDivider()
            Text(stringResource(R.string.widget_section_components), style = MaterialTheme.typography.titleMedium)

            WidgetSwitch(stringResource(R.string.widget_show_date), config.showDate) {
                onChange(config.copy(showDate = it))
            }
            WidgetSwitch(stringResource(R.string.widget_show_weather), config.showWeather) {
                onChange(config.copy(showWeather = it))
            }
            if (config.showWeather) {
                Text(stringResource(R.string.widget_weather_size_value, config.weatherScale))
                AppSlider(
                    value = config.weatherScale.toFloat(),
                    onValueChange = { onChange(config.copy(weatherScale = (it / 5f).toInt() * 5)) },
                    valueRange = 70f..200f
                )

                Text(stringResource(R.string.widget_weather_icon_size_value, config.weatherIconScale))
                AppSlider(
                    value = config.weatherIconScale.toFloat(),
                    onValueChange = { onChange(config.copy(weatherIconScale = (it / 5f).toInt() * 5)) },
                    valueRange = 70f..220f
                )

                Text(stringResource(R.string.widget_temperature_size_value, config.temperatureScale))
                AppSlider(
                    value = config.temperatureScale.toFloat(),
                    onValueChange = { onChange(config.copy(temperatureScale = (it / 5f).toInt() * 5)) },
                    valueRange = 70f..200f
                )

                Text(stringResource(R.string.widget_weather_shift_value, config.weatherShiftDp))
                AppSlider(
                    value = config.weatherShiftDp.toFloat(),
                    onValueChange = { onChange(config.copy(weatherShiftDp = it.toInt())) },
                    valueRange = 0f..40f
                )

                WidgetSwitch(stringResource(R.string.widget_show_weather_icon), config.showWeatherIcon) {
                    onChange(config.copy(showWeatherIcon = it))
                }
                WidgetSwitch(stringResource(R.string.widget_show_temperature), config.showTemperature) {
                    onChange(config.copy(showTemperature = it))
                }
                if (!hasLocationPermission) {
                    TextButton(shapes = appButtonShapes(), onClick = onLocationPermission) {
                        Text(stringResource(R.string.widget_weather_permission))
                    }
                }
                Text(
                    stringResource(R.string.widget_weather_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (kind.hasGraph) {
            HorizontalDivider()
            Text(stringResource(R.string.widget_graph_hours), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 3, 6, 12, 24).forEach { hours ->
                    FilterChip(
                        shapes = appFilterChipShapes(),
                        colors = appFilterChipColors(),
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
            HorizontalDivider()
            Text(stringResource(R.string.widget_period), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    WidgetStatsPeriod.TODAY to R.string.widget_period_today,
                    WidgetStatsPeriod.DAY to R.string.widget_period_24h,
                    WidgetStatsPeriod.WEEK to R.string.widget_period_7d
                ).forEach { (period, label) ->
                    FilterChip(
                        shapes = appFilterChipShapes(),
                        colors = appFilterChipColors(),
                        selected = config.statsPeriod == period,
                        onClick = { onChange(config.copy(statsPeriod = period)) },
                        label = { Text(stringResource(label)) }
                    )
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
        shapes = appFilterChipShapes(),
        colors = appFilterChipColors(),
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
        StyledSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

