@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import tk.glucodata.ui.components.TabScreenDefaults
import tk.glucodata.ui.components.TabScreenHeader
import tk.glucodata.ui.components.AppTopBar
import tk.glucodata.ui.util.findActivity
import tk.glucodata.ui.util.fullRestart
import tk.glucodata.ui.util.hardRestart
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.glucodata.BuildConfig
import tk.glucodata.DataSmoothing
import tk.glucodata.HealthConnection
import tk.glucodata.MainActivity
import tk.glucodata.Natives
import tk.glucodata.OutboundApiSettings
import tk.glucodata.R
import tk.glucodata.SensorBluetooth
import tk.glucodata.SensorSourceResolver
import tk.glucodata.alerts.SensorHandoverRuntime
import tk.glucodata.data.calibration.CalibrationManager
import tk.glucodata.data.ScheduledBackupSettings
import tk.glucodata.data.ScheduledBackupWorker
import tk.glucodata.drivers.ManagedSensorRuntime
import tk.glucodata.ui.components.StyledSwitch
import tk.glucodata.ui.theme.labelLargeExpressive
import tk.glucodata.ui.viewmodel.DashboardViewModel
//import tk.glucodata.ui.components.ExportDataDialog
import tk.glucodata.ui.components.*
import kotlin.math.roundToInt
import java.util.Locale

/**
 * M3 Expressive Settings Screen
 *
 * Cards in the same section are connected with:
 * - First card: rounded top corners only
 * - Middle cards: no corners (squared)
 * - Last card: rounded bottom corners only
 * - 2dp gap between cards in a group
 */
private val settingsSearchTopicStrings = mapOf(
    "cgm_readiness_title" to intArrayOf(
        R.string.cgm_readiness_title, R.string.refresh, R.string.cgm_readiness_dashboard_title, R.string.cgm_readiness_setup_title,
        R.string.cgm_readiness_setup_body, R.string.cgm_readiness_ready_title, R.string.cgm_readiness_attention_title, R.string.cgm_readiness_ready_body,
        R.string.cgm_readiness_attention_body, R.string.cgm_readiness_fix_action, R.string.cgm_readiness_summary_body, R.string.cgm_readiness_dismiss_action,
        R.string.cgm_readiness_review_action, R.string.cgm_readiness_critical_count, R.string.cgm_readiness_warning_count, R.string.cgm_readiness_attention_count,
        R.string.cgm_readiness_status_ready, R.string.cgm_readiness_status_critical, R.string.cgm_readiness_status_warning, R.string.cgm_readiness_status_recommended,
        R.string.cgm_readiness_status_manual, R.string.cgm_readiness_bluetooth_title, R.string.cgm_readiness_bluetooth_ready_detail, R.string.cgm_readiness_bluetooth_detail,
        R.string.cgm_readiness_enable_bluetooth_action, R.string.cgm_readiness_nearby_title, R.string.cgm_readiness_location_permission_title, R.string.cgm_readiness_nearby_ready_detail,
        R.string.cgm_readiness_location_permission_ready_detail, R.string.cgm_readiness_nearby_detail, R.string.cgm_readiness_location_permission_detail, R.string.cgm_readiness_allow_action,
        R.string.cgm_readiness_weather_location_title, R.string.cgm_readiness_weather_location_ready_detail, R.string.cgm_readiness_weather_location_detail, R.string.cgm_readiness_open_location_permission_action,
        R.string.cgm_readiness_location_services_title, R.string.cgm_readiness_location_services_ready_detail, R.string.cgm_readiness_location_services_detail, R.string.cgm_readiness_open_location_action,
        R.string.cgm_readiness_notification_permission_title, R.string.cgm_readiness_notification_permission_ready_detail, R.string.cgm_readiness_notification_permission_detail, R.string.cgm_readiness_notifications_title,
        R.string.cgm_readiness_notifications_ready_detail, R.string.cgm_readiness_notifications_detail, R.string.cgm_readiness_open_notifications_action, R.string.cgm_readiness_exact_alarm_title,
        R.string.cgm_readiness_exact_alarm_ready_detail, R.string.cgm_readiness_exact_alarm_detail, R.string.cgm_readiness_open_alarm_action, R.string.cgm_readiness_full_screen_title,
        R.string.cgm_readiness_full_screen_ready_detail, R.string.cgm_readiness_full_screen_detail, R.string.cgm_readiness_open_full_screen_action, R.string.cgm_readiness_battery_title,
        R.string.cgm_readiness_battery_ready_detail, R.string.cgm_readiness_battery_detail, R.string.cgm_readiness_open_battery_action, R.string.cgm_readiness_background_activity_title,
        R.string.cgm_readiness_background_activity_restricted_detail, R.string.cgm_readiness_background_activity_detail, R.string.cgm_readiness_open_battery_usage_action, R.string.cgm_readiness_dnd_title,
        R.string.cgm_readiness_dnd_ready_detail, R.string.cgm_readiness_dnd_detail, R.string.cgm_readiness_open_dnd_action, R.string.cgm_readiness_overlay_title,
        R.string.cgm_readiness_overlay_not_needed_detail, R.string.cgm_readiness_overlay_ready_detail, R.string.cgm_readiness_overlay_detail, R.string.cgm_readiness_open_overlay_action,
        R.string.cgm_readiness_data_saver_title, R.string.cgm_readiness_data_saver_detail, R.string.cgm_readiness_data_saver_ready_detail, R.string.cgm_readiness_open_data_saver_action,
        R.string.cgm_readiness_nfc_title, R.string.cgm_readiness_nfc_ready_detail, R.string.cgm_readiness_nfc_detail, R.string.cgm_readiness_open_nfc_action,
        R.string.cgm_readiness_manufacturer_title, R.string.cgm_readiness_manufacturer_detail, R.string.cgm_readiness_settings_unavailable,
    ),
    "display_colors_title" to intArrayOf(
        R.string.position_top, R.string.position_center, R.string.position_bottom, R.string.alignment_left,
        R.string.alignment_center, R.string.alignment_right, R.string.notification_settings_title, R.string.typography,
        R.string.font_app_plex, R.string.font_system_google_sans, R.string.font_weight_label, R.string.theme_light,
        R.string.regular, R.string.medium, R.string.font_size_percent, R.string.status_bar_icon_size_percent,
        R.string.appearance, R.string.show_trend_arrow, R.string.notification_large_arrow_title, R.string.notification_large_arrow_desc,
        R.string.arrow_size_percent, R.string.notification_charts_section, R.string.show_chart_expanded, R.string.show_chart_expanded_desc,
        R.string.show_chart_collapsed, R.string.show_chart_collapsed_desc, R.string.show_target_range, R.string.show_target_range_desc,
        R.string.notification_chart_pause_screen_off, R.string.notification_chart_pause_screen_off_summary, R.string.notification_status_line_section, R.string.notification_show_delta_title,
        R.string.notification_show_delta_minutes_desc, R.string.notification_show_iob_title, R.string.notification_show_iob_desc, R.string.notification_show_cob_title,
        R.string.notification_show_cob_desc, R.string.notification_iob_cob_risk_title, R.string.notification_iob_cob_risk_desc, R.string.notification_iob_risk_without_cob_title,
        R.string.notification_iob_risk_without_cob_desc, R.string.preview_window_always, R.string.preview_window_never, R.string.preview_window_expanded_only,
        R.string.display_colors_title, R.string.dashboard_bars_section, R.string.dashboard_bars_stacked_title, R.string.dashboard_bars_stacked_desc,
        R.string.dashboard_bars_ratio_title, R.string.dashboard_bars_ratio_desc, R.string.dashboard_bars_ratio_value, R.string.glucose_palette_edit_title,
        R.string.glucose_range_colors_title, R.string.glucose_range_colors_desc, R.string.glucose_arrow_forecast_title, R.string.glucose_arrow_forecast_desc,
        R.string.glucose_chart_range_colors_title, R.string.glucose_chart_range_colors_desc, R.string.glucose_app_chart_range_colors_title, R.string.glucose_app_chart_range_colors_desc,
        R.string.display_readouts_section, R.string.preview_window_title, R.string.dashboard_show_delta_title, R.string.dashboard_show_delta_desc,
        R.string.dashboard_rows_show_delta_title, R.string.dashboard_rows_show_delta_desc, R.string.dashboard_show_reading_age_title, R.string.dashboard_show_reading_age_desc,
        R.string.floatglucose, R.string.enable_overlay, R.string.enable_overlay_desc, R.string.floating_permission_required,
        R.string.background, R.string.filled, R.string.transparent, R.string.corner_radius_dp,
        R.string.background_opacity_percent, R.string.use_subtle_outline, R.string.use_subtle_outline_desc, R.string.position_layout,
        R.string.dynamic_island_mode, R.string.dynamic_island_desc, R.string.offset, R.string.dp_value,
        R.string.auto, R.string.gap_width_value, R.string.metrics, R.string.display_secondary_values,
        R.string.display_secondary_values_desc, R.string.font_source, R.string.font_system_sans, R.string.size_sp,
        R.string.aod_settings_title, R.string.enable_overlay_service, R.string.accessibility_service_enabled, R.string.open_accessibility_settings,
        R.string.aod_show_on_lockscreen, R.string.aod_show_on_lockscreen_desc, R.string.visuals, R.string.active_positions_randomized,
        R.string.text_alignment, R.string.show_chart, R.string.opacity_percent, R.string.text_size_percent,
        R.string.chart_size_percent,
    ),
    "glucose_alerts_title" to intArrayOf(
        R.string.custom_high, R.string.custom_low, R.string.glucose_alerts_title, R.string.high_alerts_title,
        R.string.add_high_alert, R.string.low_alerts_title, R.string.add_low_alert, R.string.predictive_alerts,
        R.string.same_direction_suppression_title, R.string.same_direction_suppression_summary, R.string.off, R.string.minutes_short_format,
        R.string.other_alerts, R.string.preemptive_snooze, R.string.preemptive_snooze_desc, R.string.talker,
        R.string.speakglucose, R.string.speakalarms, R.string.high_alert, R.string.low_alert,
        R.string.disabled_status, R.string.collapse_action, R.string.expand_action, R.string.delete_custom_alert,
        R.string.alert_name, R.string.threshold_label, R.string.alert_after, R.string.look_ahead,
        R.string.rearm_margin_label, R.string.rearm_min_interval_label, R.string.pre_high_iob_coverage_label, R.string.alert_feature_off,
        R.string.persistent_high_fall_suppress_label, R.string.sensor_expiry_warnings_title, R.string.days_short, R.string.hours_short,
        R.string.sensor_expiry_warnings_desc, R.string.delta_change_label, R.string.delta_count_label, R.string.delta_border_below_label,
        R.string.delta_border_above_label, R.string.falling_fast_explanation, R.string.rising_fast_explanation, R.string.delta_alarm_interval_label,
        R.string.delta_alarm_interval_follow, R.string.delta_interval_1min, R.string.delta_interval_5min, R.string.delta_early_trigger_label,
        R.string.delta_early_trigger_desc, R.string.unit_mmol, R.string.unit_mg, R.string.low_alerts,
        R.string.high_alerts, R.string.duration_label, R.string.snooze, R.string.cancel,
        R.string.active_time_range_title, R.string.time_range_summary, R.string.only_alert_during_hours, R.string.start,
        R.string.end, R.string.alert_active_from_to, R.string.alert_active_from_to_next_day, R.string.select_time_for_label,
        R.string.ok, R.string.retry_summary_constant_forever, R.string.retry_summary_constant, R.string.retry_summary_forever,
        R.string.retry_summary, R.string.realert_if_not_dismissed, R.string.retry_if_no_reaction, R.string.retry_every,
        R.string.retry_constantly, R.string.max_retries, R.string.retry_forever, R.string.soundname,
        R.string.app_default_sound, R.string.system_default_sound, R.string.custom_sound_selected, R.string.tap_to_change,
    ),
    "journal_title" to intArrayOf(
        R.string.journal_manage_title, R.string.journal_title, R.string.journal_gate_desc, R.string.journal_show_tab_title,
        R.string.journal_show_tab_desc, R.string.journal_dashboard_quickadd_title, R.string.journal_dashboard_quickadd_desc, R.string.journal_quickadd_always_now_title,
        R.string.journal_quickadd_always_now_desc, R.string.journal_dose_math_title, R.string.journal_dose_calculator_desc, R.string.journal_calculations_title,
        R.string.journal_calculations_desc, R.string.journal_import_health_activity, R.string.journal_aaps_import_title, R.string.journal_aaps_import_desc,
        R.string.journal_food_library, R.string.journal_food_library_count, R.string.journal_insulin_library, R.string.journal_insulin_library_count,
        R.string.historyname, R.string.journal_food_macros_title, R.string.journal_food_macros_desc, R.string.journal_food_choose_desc,
        R.string.journal_add_food, R.string.journal_food_disabled_library, R.string.journal_add_preset, R.string.journal_archived_presets,
        R.string.disabled_status, R.string.journal_active_window, R.string.minutes_short_format, R.string.journal_active_insulin,
        R.string.journal_use_for_calculation, R.string.carbo, R.string.unit_carbs_value, R.string.journal_food_protein,
        R.string.journal_food_fat, R.string.journal_food_search, R.string.clear, R.string.journal_edit_food,
        R.string.enable, R.string.disable, R.string.name, R.string.unit_carbs_short,
        R.string.journal_food_absorption, R.string.minutes, R.string.save, R.string.delete,
        R.string.journal_edit_preset, R.string.journal_scientific_name, R.string.journal_active_insulin_subtitle, R.string.journal_use_for_calculation_subtitle,
        R.string.journal_curve_unverified, R.string.journal_curve_steady_state, R.string.journal_curve_reference_only, R.string.journal_curve_source_backed,
        R.string.journal_curve_unverified_desc, R.string.journal_curve_steady_state_desc, R.string.journal_curve_reference_only_desc, R.string.journal_curve_source_backed_desc,
        R.string.journal_curve_preview, R.string.journal_curve_use_source, R.string.journal_curve_reset, R.string.journal_curve_activity_desc,
        R.string.journal_curve_add_point, R.string.journal_curve_point, R.string.journal_curve_minutes, R.string.journal_curve_activity,
        R.string.colors,
    ),
    "manual_calibration" to intArrayOf(
        R.string.raw, R.string.auto, R.string.unable_to_open_destination_file, R.string.calibration_profile_exported,
        R.string.export_failed_with_error, R.string.unknown_error, R.string.unable_to_open_source_file, R.string.import_summary,
        R.string.import_failed_with_error, R.string.invalid_profile, R.string.no_active_sensor_selected, R.string.no_calibrations_to_export,
        R.string.selected_count, R.string.cancel, R.string.deselect_all, R.string.select_all,
        R.string.calibration_with_mode, R.string.calibration_model_table_action, R.string.calibration_import_export_action, R.string.calibration_deleted,
        R.string.undo, R.string.delete_calibrations_count, R.string.this_action_can_be_undone, R.string.calibrations_deleted_count,
        R.string.delete, R.string.disabled_calibrations_cleared, R.string.all_calibrations_cleared, R.string.enable_calibration,
        R.string.enabled_status, R.string.disabled_status, R.string.calibration_hide_initial_values, R.string.calibration_initial_hidden_status,
        R.string.calibration_initial_shown_status, R.string.calibratepast, R.string.calibration_apply_past_subtitle, R.string.calibration_lock_past_history_title,
        R.string.calibration_lock_past_history_subtitle, R.string.calibration_keep_disabled_history_title, R.string.calibration_keep_disabled_history_subtitle, R.string.calibration_overwrite_values_title,
        R.string.calibration_overwrite_values_subtitle, R.string.calibration_from_journal_title, R.string.calibration_from_journal_subtitle, R.string.calibrate_a,
        R.string.calibration_visual_continuity_subtitle, R.string.calibration_algorithm, R.string.calibration_weight_fresh_desc, R.string.calibration_weight_balanced_desc,
        R.string.calibration_weight_stable_desc, R.string.calibration_weight_title, R.string.calibration_weight_fresh, R.string.calibration_weight_balanced,
        R.string.calibration_weight_stable, R.string.points, R.string.slope, R.string.offset,
        R.string.intercept, R.string.anchor, R.string.confidence, R.string.calibration_from_journal_badge,
        R.string.clear, R.string.calibrate_action, R.string.disable, R.string.enable,
        R.string.calibration_import_export_title, R.string.calibration_import_export_desc, R.string.export_profile, R.string.calibration_export_profile_desc,
        R.string.import_profile_merge, R.string.calibration_import_merge_desc, R.string.import_profile_replace, R.string.calibration_import_replace_desc,
        R.string.clear_calibrations_title, R.string.choose_what_to_clear, R.string.clear_disabled_only, R.string.disabled_calibrations_count,
        R.string.calibrations_will_be_removed_count,
    ),
    "watches" to intArrayOf(
        R.string.watches, R.string.helpname, R.string.watch_transport_section, R.string.notify,
        R.string.separate, R.string.watch_wearos_enable_desc, R.string.watch_gms_unavailable, R.string.watch_gms_unavailable_device,
        R.string.wentwrong, R.string.config, R.string.watch_wearos_config_desc, R.string.watch_wearos_config_disabled,
        R.string.watch_kerfstok_bridge, R.string.status, R.string.watch_garmin_status_desc, R.string.watch_garmin_enable_hint,
        R.string.watch_help_wearos, R.string.watch_help_config, R.string.watch_help_garmin, R.string.never,
        R.string.wearos_config_title, R.string.watch_status_section, R.string.watch_none_found, R.string.watch_none_found_hint,
        R.string.watchappinstalled, R.string.wear_watch_app_missing, R.string.wear_claim_state_phone_owns, R.string.wear_claim_state_requesting,
        R.string.wear_claim_state_connected, R.string.wear_watch_app_missing_hint, R.string.watch_routing_section, R.string.wear_auto_sensor_switch,
        R.string.wear_auto_sensor_switch_on_desc, R.string.wear_auto_sensor_switch_off_desc, R.string.wear_direct_sensor_on_watch, R.string.wear_direct_sensor_watch_desc,
        R.string.wear_direct_sensor_phone_desc, R.string.watch_enter_amounts, R.string.watch_enter_amounts_on, R.string.watch_enter_amounts_off,
        R.string.actions_section, R.string.watch_none_selected, R.string.saved, R.string.watch_sync_now,
        R.string.watch_restart_app, R.string.watch_send_defaults, R.string.garmin_status_title, R.string.garmin_state_section,
        R.string.garmin_sdk_ready, R.string.answer_yes, R.string.answer_no, R.string.garmin_registered,
        R.string.garmin_last_send, R.string.garmin_last_received, R.string.garmin_queued_messages, R.string.garmin_queue_waiting,
        R.string.garmin_queue_empty, R.string.reinit, R.string.sync, R.string.sendqueue,
        R.string.darkmode, R.string.kerfstok_watch_ui, R.string.getkerfstok, R.string.toolongsecret,
        R.string.invalidport, R.string.nomirrorport, R.string.nohttpport, R.string.portrange,
        R.string.invalid_interval, R.string.webserver_cert_created, R.string.webserver_cert_export, R.string.sendto,
        R.string.webserver, R.string.active, R.string.webserver_running, R.string.webserver_paused,
        R.string.secret, R.string.localonly, R.string.webserver_local_only_desc, R.string.port,
        R.string.usessl, R.string.webserver_https_subtitle, R.string.webserver_cert_generating, R.string.webserver_cert_needs_update,
        R.string.webserver_certificate, R.string.webserver_cert_none, R.string.webserver_cert_expired, R.string.webserver_cert_address_changed,
        R.string.webserver_cert_selfsigned, R.string.webserver_cert_custom, R.string.webserver_cert_expires, R.string.webserver_cert_generate,
        R.string.webserver_cert_update, R.string.webserver_cert_regenerate, R.string.webserver_cert_import, R.string.ssl_port,
        R.string.interval, R.string.webserver_wifi_unavailable, R.string.webserver_loopback, R.string.webserver_endpoint_current,
        R.string.webserver_endpoint_entries, R.string.webserver_endpoint_report, R.string.webserver_cert_export_hint, R.string.webserver_cert_export_certificate,
        R.string.webserver_cert_export_with_key, R.string.webserver_cert_export_key_warning, R.string.close, R.string.webserver_cert_import_hint,
        R.string.privatekey, R.string.fullchain, R.string.webserver_help_endpoints, R.string.webserver_help_paths,
        R.string.webserver_help_local, R.string.webserver_help_ssl,
    ),
    "widget_settings_title" to intArrayOf(
        R.string.widget_settings_title, R.string.widget_settings_data_note, R.string.widget_section_appearance, R.string.widget_font,
        R.string.widget_font_ibm_plex, R.string.widget_font_system, R.string.widget_font_weight, R.string.widget_clock_detail_size_value,
        R.string.widget_detail_size_value, R.string.widget_clock_glucose_size_value, R.string.widget_glucose_size_value, R.string.widget_corner_radius_value,
        R.string.widget_background, R.string.widget_bg_surface, R.string.widget_bg_range, R.string.widget_bg_none,
        R.string.widget_opacity_value, R.string.widget_section_visibility, R.string.widget_show_arrow, R.string.widget_range_colors,
        R.string.widget_show_time, R.string.widget_show_delta, R.string.widget_show_unit, R.string.widget_section_components,
        R.string.widget_show_date, R.string.widget_show_weather, R.string.widget_weather_size_value, R.string.widget_weather_icon_size_value,
        R.string.widget_temperature_size_value, R.string.widget_weather_shift_value, R.string.widget_show_weather_icon, R.string.widget_show_temperature,
        R.string.widget_weather_permission, R.string.widget_weather_note, R.string.widget_graph_hours, R.string.widget_hours_short,
        R.string.widget_target_band, R.string.widget_period, R.string.widget_period_today, R.string.widget_period_24h,
        R.string.widget_period_7d,
    ),
)

@Composable
fun ExpressiveSettingsScreen(
    navController: NavController,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    viewModel: DashboardViewModel = viewModel(),
    category: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sensorStatusRevision by tk.glucodata.UiRefreshBus.revision.collectAsStateWithLifecycle()
    val showMqAccount = remember(context, sensorStatusRevision) {
        tk.glucodata.drivers.mq.MQRegistry.persistedRecords(context).isNotEmpty()
    }
    // LibreView is relevant whenever the account is already set up *or* a Libre-family
    // sensor is active — the account no longer has to be configured in the setup wizard first.
    val showLibreView = remember {
        runCatching { Natives.getuselibreview() }.getOrDefault(false) ||
            runCatching { Natives.getlibreAccountIDnumber() }.getOrDefault(0L) > 0L ||
            hasActiveLibreSensorForLibreView()
    }
    val showOttaiSettings = remember {
        SensorBluetooth.mygatts().any { callback ->
            (callback as? tk.glucodata.drivers.ottai.OttaiDriver)?.isUiEnabled() == true
        }
    }

    // States
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val isMmol = tk.glucodata.ui.util.GlucoseFormatter.isMmol(unit)
    val patchedLibreEnabled by viewModel.patchedLibreBroadcastEnabled.collectAsStateWithLifecycle()
    val notificationChartEnabled by viewModel.notificationChartEnabled.collectAsStateWithLifecycle()
    val chartSmoothingMinutes by viewModel.chartSmoothingMinutes.collectAsStateWithLifecycle()
    val dataSmoothingGraphOnly by viewModel.dataSmoothingGraphOnly.collectAsStateWithLifecycle()
    val dataSmoothingCollapseChunks by viewModel.dataSmoothingCollapseChunks.collectAsStateWithLifecycle()
    val dataSmoothingExchangeOnly by viewModel.dataSmoothingExchangeOnly.collectAsStateWithLifecycle()
    val journalEnabled by viewModel.journalEnabled.collectAsStateWithLifecycle()
    val predictiveSimulationEnabled by viewModel.predictiveSimulationEnabled.collectAsStateWithLifecycle()
    val alertsMasterEnabled by viewModel.alertsMasterEnabled.collectAsStateWithLifecycle()
    var healthConnectEnabled by rememberSaveable { mutableStateOf(Natives.gethealthConnect()) }
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val sensorName by viewModel.sensorName.collectAsStateWithLifecycle()
    val isRawCalibrationMode = viewMode == 1 || viewMode == 3
    val calibrationRevision by CalibrationManager.revision.collectAsStateWithLifecycle()
    val calibrationEnabled = remember(isRawCalibrationMode, sensorName, calibrationRevision) {
        CalibrationManager.isEnabledForMode(isRawCalibrationMode, sensorName)
    }
    val calibrationModeLabel = if (isRawCalibrationMode) "Raw" else "Auto"

    // Rows that carry a master switch read the same state their own screen reads. The
    // system-granted ones can change while this screen is in the background, so they are
    // re-read on resume rather than only at first composition.
    val floatingOverlayEnabled by viewModel.floatingRepository.isEnabled.collectAsStateWithLifecycle(initialValue = false)
    var floatingOverlayAllowed by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    var aodServiceEnabled by remember { mutableStateOf(isAodAccessibilityEnabled(context)) }
    val insulinPensEnabled by tk.glucodata.InsulinPenManager.enabled.collectAsStateWithLifecycle()
    var webServerActive by remember { mutableStateOf(Natives.getusexdripwebserver()) }
    var nightscoutActive by remember { mutableStateOf(isNightscoutActive(context)) }

    // Auto-refresh data when screen becomes active (e.g. returning from Alerts screen)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
                floatingOverlayAllowed = android.provider.Settings.canDrawOverlays(context)
                aodServiceEnabled = isAodAccessibilityEnabled(context)
                webServerActive = Natives.getusexdripwebserver()
                nightscoutActive = isNightscoutActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasLowAlarm by viewModel.hasLowAlarm.collectAsStateWithLifecycle()
    val lowAlarmValue by viewModel.lowAlarmThreshold.collectAsStateWithLifecycle()
    val lowAlarmSoundMode by viewModel.lowAlarmSoundMode.collectAsStateWithLifecycle()
    val hasHighAlarm by viewModel.hasHighAlarm.collectAsStateWithLifecycle()
    val highAlarmValue by viewModel.highAlarmThreshold.collectAsStateWithLifecycle()
    val highAlarmSoundMode by viewModel.highAlarmSoundMode.collectAsStateWithLifecycle()

    // Dialog states
    var showUnitDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showFactoryResetDialog by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showScheduledBackupDialog by remember { mutableStateOf(false) }
    var scheduledBackupConfig by remember {
        mutableStateOf(ScheduledBackupSettings.load(context))
    }
    LaunchedEffect(Unit) {
        ScheduledBackupWorker.initialize(context)
    }
    var pendingSettingsImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExportPackageImportUri by remember { mutableStateOf<Uri?>(null) }



    // Advanced settings
    var turbo by remember { mutableStateOf(Natives.getpriority()) }
    var autoConnect by remember { mutableStateOf(Natives.getAndroid13()) }
    val handoverPrefs = remember {
        context.getSharedPreferences("tk.glucodata_preferences", android.content.Context.MODE_PRIVATE)
    }
    var sensorHandoverEnabled by remember {
        mutableStateOf(handoverPrefs.getBoolean(SensorHandoverRuntime.PREF_ENABLED, false))
    }
    var sensorHandoverAction by remember {
        mutableStateOf(
            handoverPrefs.getInt(
                SensorHandoverRuntime.PREF_OLD_ACTION,
                SensorHandoverRuntime.OLD_ACTION_DEACTIVATE
            )
        )
    }
    var showSensorHandoverActionDialog by remember { mutableStateOf(false) }

    val currentLocale = AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
    val currentLangName = currentLocale.displayLanguage.replaceFirstChar { it.uppercase() }

    val themeLabel = when(themeMode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
    }
    val graphSmoothingLabel = if (chartSmoothingMinutes <= 0) {
        stringResource(R.string.graph_smoothing_none)
    } else {
        val collapseIntervalMinutes = DataSmoothing.collapseIntervalMinutes(chartSmoothingMinutes)
        buildList {
            add(stringResource(R.string.minutes_short_format, chartSmoothingMinutes))
            if (dataSmoothingExchangeOnly) {
                add(stringResource(R.string.data_smoothing_exchange_only_title))
            } else if (dataSmoothingGraphOnly) {
                add(stringResource(
                    if (dataSmoothingCollapseChunks) R.string.data_smoothing_scope_graph_and_sent
                    else R.string.data_smoothing_graph_only_title
                ))
            } else {
                add(stringResource(R.string.data_smoothing_scope_all))
            }
            if (dataSmoothingCollapseChunks) {
                add(stringResource(R.string.data_smoothing_collapse_summary_format, collapseIntervalMinutes))
            }
        }.joinToString(" · ")
    }

    var settingsQuery by rememberSaveable(category) { mutableStateOf("") }
    val normalizedQuery = settingsQuery.trim()
    fun matches(vararg ids: Int): Boolean = normalizedQuery.isEmpty() ||
        ids.any { context.getString(it).contains(normalizedQuery, ignoreCase = true) }
    fun matchesTopic(key: String, vararg ids: Int): Boolean = matches(*ids) ||
        settingsSearchTopicStrings[key]?.let { matches(*it) } == true

    val generalColor = MaterialTheme.colorScheme.primary
    val glucoseColor = MaterialTheme.colorScheme.primary
    val notifColor = MaterialTheme.colorScheme.secondary
    val exchangeColor = MaterialTheme.colorScheme.tertiary
    val advColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dataColor = MaterialTheme.colorScheme.secondary
            val xdripEnabled by viewModel.xDripBroadcastEnabled.collectAsStateWithLifecycle()
            val glucodataBroadcastEnabled by viewModel.glucodataBroadcastEnabled.collectAsStateWithLifecycle()
            val broadcastComputedTrend by viewModel.broadcastComputedTrend.collectAsStateWithLifecycle()
            val xdripReportAsLibre2 by viewModel.xdripReportAsLibre2.collectAsStateWithLifecycle()

            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri ->
                    if (uri != null) {
                        scope.launch {
                            when (tk.glucodata.data.ExportPackageExporter.detectImportFileType(context, uri)) {
                                tk.glucodata.data.ExportPackageExporter.ImportFileType.SETTINGS -> {
                                    withContext(Dispatchers.Main) { pendingSettingsImportUri = uri }
                                }
                                tk.glucodata.data.ExportPackageExporter.ImportFileType.EXPORT_PACKAGE -> {
                                    withContext(Dispatchers.Main) { pendingExportPackageImportUri = uri }
                                }
                                tk.glucodata.data.ExportPackageExporter.ImportFileType.OTHER -> {
                                    val result = tk.glucodata.data.HistoryExporter.importFromCsv(context, uri)
                                    withContext(Dispatchers.Main) {
                                        val msg = if (result.success)
                                            context.getString(R.string.imported_readings_count, result.successCount)
                                        else
                                            context.getString(
                                                R.string.import_failed_with_error,
                                                result.errorMessage ?: context.getString(R.string.unknown_error)
                                            )
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                }
            )

                val anyExchangeConsumer = patchedLibreEnabled || xdripEnabled || glucodataBroadcastEnabled ||
                        OutboundApiSettings.isEnabled() ||
                        Natives.getgadgetbridge() ||
                        Natives.getwatchdrip()
                val freezeDisplayedValues by tk.glucodata.data.calibration.CalibrationManager
                    .freezeDisplayedValues.collectAsStateWithLifecycle()
    val barsStacked by viewModel.dashboardBarsStacked.collectAsStateWithLifecycle()
    val categoryTitles = linkedMapOf(
        "appearance" to R.string.settings_category_appearance,
        "alarms" to R.string.settings_category_alarms,
        "devices" to R.string.settings_category_devices,
        "processing" to R.string.settings_category_processing,
        "journal" to R.string.settings_category_journal,
        "connections" to R.string.settings_category_connections,
        "data" to R.string.settings_category_data,
        "app" to R.string.settings_category_app,
        "reset" to R.string.settings_category_reset,
    )
    val categoryDescriptions = mapOf(
        "appearance" to R.string.settings_category_appearance_description,
        "alarms" to R.string.settings_category_alarms_description,
        "devices" to R.string.settings_category_devices_description,
        "processing" to R.string.settings_category_processing_description,
        "journal" to R.string.settings_category_journal_description,
        "connections" to R.string.settings_category_connections_description,
        "data" to R.string.settings_category_data_description,
        "app" to R.string.settings_category_app_description,
    )
    val categoryIcons = mapOf(
        "appearance" to Icons.Default.Palette,
        "alarms" to Icons.Default.AddAlert,
        "devices" to Icons.Default.Devices,
        "processing" to Icons.Default.WaterDrop,
        "journal" to Icons.Default.Vaccines,
        "connections" to Icons.Default.Share,
        "data" to Icons.Default.Backup,
        "app" to Icons.Default.Info,
    )
    val categoryTitle = categoryTitles[category] ?: R.string.settings
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = TabScreenDefaults.contentPadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "title") {
            if (category == null) {
                TabScreenHeader(title = stringResource(R.string.settings))
            } else {
                AppTopBar(
                    title = stringResource(categoryTitle),
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
        item(key = "search") {
            OutlinedTextField(
                value = settingsQuery,
                onValueChange = { settingsQuery = it },
                label = { Text(stringResource(R.string.settings_search_label)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (settingsQuery.isNotEmpty()) {
                        IconButton(onClick = { settingsQuery = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.settings_search_clear))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        if (category == null && normalizedQuery.isEmpty()) {
            item(key = "readiness") {
                DashboardCgmReadinessBanner(onOpenReadiness = { navController.navigate("settings/cgm-readiness") })
            }
            categoryTitles.filterKeys { it != "reset" }.forEach { (key, title) ->
                item(key = "category_$key") {
                    SettingsItem(
                        title = stringResource(title),
                        subtitle = if (key == "appearance") {
                            stringResource(categoryDescriptions.getValue(key)) + " · " +
                                stringResource(if (barsStacked) R.string.settings_bars_stacked_summary else R.string.settings_bars_side_summary) + " · " + unit
                        } else stringResource(categoryDescriptions.getValue(key)),
                        icon = categoryIcons.getValue(key),
                        iconTint = MaterialTheme.colorScheme.primary,
                        position = CardPosition.SINGLE,
                        onClick = { navController.navigate("settings/category/$key") },
                    )
                }
            }
        } else {
            if ((category == null || category == "appearance") && matches(R.string.theme_title)) {
                item(key = "theme_title") {
                SettingsItem(
                    title = stringResource(R.string.theme_title),
                    subtitle = themeLabel,
                    icon = if(themeMode == ThemeMode.LIGHT) Icons.Default.LightMode else Icons.Default.DarkMode,
                    iconTint = generalColor,
                    position = CardPosition.SINGLE,
                    onClick = { showThemeDialog = true }
                )
                }
            }
            if ((category == null || category == "appearance") && matches(R.string.languagename)) {
                item(key = "languagename") {
                SettingsItem(
                    title = stringResource(R.string.languagename),
                    subtitle = currentLangName,
                    icon = Icons.Default.Language,
                    iconTint = generalColor,
                    position = CardPosition.SINGLE,
                    onClick = { showLanguageDialog = true }
                )
                }
            }
            if ((category == null || category == "appearance") && matches(R.string.unit)) {
                item(key = "unit") {
                SettingsItem(
                    title = stringResource(R.string.unit),
                    subtitle = unit,
                    icon = Icons.AutoMirrored.Filled.List,
                    iconTint = glucoseColor,
                    position = CardPosition.SINGLE,
                    onClick = { showUnitDialog = true }
                )
                }
            }
            if ((category == null || category == "appearance") && matchesTopic("display_colors_title", R.string.display_colors_title, R.string.display_colors_desc)) {
                item(key = "display_colors_title") {
                SettingsItem(
                    title = stringResource(R.string.display_colors_title),
                    subtitle = stringResource(R.string.display_colors_desc),
                    icon = Icons.Default.Palette,
                    iconTint = glucoseColor,
                    position = CardPosition.SINGLE,
                    onClick = { navController.navigate("settings/display-colors") }
                )
                }
            }
            if ((category == null || category == "appearance") && matchesTopic("widget_settings_title", R.string.widget_settings_title, R.string.widget_settings_subtitle)) {
                item(key = "widget_settings_title") {
                SettingsItem(
                    title = stringResource(R.string.widget_settings_title),
                    subtitle = stringResource(R.string.widget_settings_subtitle),
                    icon = Icons.Default.Layers,
                    iconTint = glucoseColor,
                    position = CardPosition.SINGLE,
                    onClick = { navController.navigate("settings/widgets") }
                )
                }
            }
            if ((category == null || category == "appearance") && matches(R.string.notification_settings_title, R.string.notification_settings_subtitle)) {
                item(key = "notification_settings_title") {
                SettingsItem(
                    title = stringResource(R.string.notification_settings_title),
                    subtitle = stringResource(R.string.notification_settings_subtitle),
                    icon = Icons.Default.ClearAll,
                    iconTint = notifColor,
                    position = CardPosition.SINGLE,
                    onClick = { navController.navigate("settings/notification-display") }
                )
                }
            }
            if ((category == null || category == "appearance") && matches(R.string.floatglucose, R.string.floating_permission_required, R.string.enabled_status, R.string.disabled_status)) {
                item(key = "floatglucose") {
                SettingsNavSwitchItem(
                    title = stringResource(R.string.floatglucose),
                    subtitle = when {
                        !floatingOverlayAllowed -> stringResource(R.string.floating_permission_required)
                        floatingOverlayEnabled -> stringResource(R.string.enabled_status)
                        else -> stringResource(R.string.disabled_status)
                    },
                    checked = floatingOverlayEnabled,
                    // The overlay cannot start without the system grant, so the switch
                    // sends you to give it rather than flipping to a state it can't hold.
                    onCheckedChange = { enabled ->
                        if (enabled && !floatingOverlayAllowed) {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else {
                            viewModel.toggleFloatingGlucose(enabled)
                        }
                    },
                    onClick = { navController.navigate("settings/floating-display") },
                    icon = Icons.Default.PictureInPicture,
                    iconTint = notifColor,
                    position = CardPosition.SINGLE
                )
                }
            }
            if ((category == null || category == "appearance") && matches(R.string.lock_screen_aod, R.string.accessibility_service_enabled, R.string.open_accessibility_settings)) {
                item(key = "lock_screen_aod") {
                SettingsNavSwitchItem(
                    title = stringResource(R.string.lock_screen_aod),
                    subtitle = if (aodServiceEnabled) {
                        stringResource(R.string.accessibility_service_enabled)
                    } else {
                        stringResource(R.string.open_accessibility_settings)
                    },
                    checked = aodServiceEnabled,
                    // The lock-screen overlay is an accessibility service: only the system
                    // settings screen can turn it on or off.
                    onCheckedChange = {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onClick = { navController.navigate("settings/aod-display") },
                    icon = Icons.Default.Visibility,
                    iconTint = notifColor,
                    position = CardPosition.SINGLE
                )
                }
            }
            if ((category == null || category == "alarms") && matchesTopic("glucose_alerts_title", R.string.glucose_alerts_title, R.string.global_active, R.string.global_all_alerts_disabled)) {
                item(key = "glucose_alerts_title") {
                SettingsNavSwitchItem(
                    title = stringResource(R.string.glucose_alerts_title),
                    subtitle = stringResource(
                        if (alertsMasterEnabled) R.string.global_active else R.string.global_all_alerts_disabled
                    ),
                    checked = alertsMasterEnabled,
                    onCheckedChange = { viewModel.setAlertsMasterEnabled(it) },
                    onClick = { navController.navigate("settings/alerts") },
                    icon = Icons.Default.AddAlert,
                    iconTint = MaterialTheme.colorScheme.error,
                    position = CardPosition.SINGLE,
                )
                }
            }
            if ((category == null || category == "devices") && matches(R.string.turbo_title, R.string.turbo_desc)) {
                item(key = "turbo_title") {
                SettingsSwitchItem(
                    title = stringResource(R.string.turbo_title),
                    subtitle = stringResource(R.string.turbo_desc),
                    checked = turbo,
                    icon = Icons.Default.Speed,
                    iconTint = advColor,
                    position = CardPosition.SINGLE,
                    onCheckedChange = { Natives.setpriority(it); turbo = it }
                )
                }
            }
            if ((category == null || category == "devices") && matches(R.string.autoconnect_title, R.string.autoconnect_desc)) {
                item(key = "autoconnect_title") {
                SettingsSwitchItem(
                    title = stringResource(R.string.autoconnect_title),
                    subtitle = stringResource(R.string.autoconnect_desc),
                    checked = autoConnect,
                    icon = Icons.Default.Autorenew,
                    iconTint = advColor,
                    position = CardPosition.SINGLE,
                    onCheckedChange = { SensorBluetooth.setAutoconnect(it); autoConnect = it }
                )
                }
            }
            if ((category == null || category == "devices") && matches(R.string.sensor_handover_title, R.string.sensor_handover_desc)) {
                item(key = "sensor_handover_title") {
                SettingsSwitchItem(
                    title = stringResource(R.string.sensor_handover_title),
                    subtitle = stringResource(R.string.sensor_handover_desc),
                    checked = sensorHandoverEnabled,
                    icon = Icons.Default.SwapHoriz,
                    iconTint = advColor,
                    position = CardPosition.SINGLE,
                    onCheckedChange = {
                        handoverPrefs.edit().putBoolean(SensorHandoverRuntime.PREF_ENABLED, it).apply()
                        sensorHandoverEnabled = it
                    }
                )
                }
            }
            if (sensorHandoverEnabled && (category == null || category == "devices") && matches(R.string.sensor_handover_old_action_title, R.string.sensor_handover_action_remove, R.string.sensor_handover_action_deactivate)) {
                item(key = "sensor_handover_old_action_title") {
                if (sensorHandoverEnabled) {
                    SettingsItem(
                        title = stringResource(R.string.sensor_handover_old_action_title),
                        subtitle = if (sensorHandoverAction == SensorHandoverRuntime.OLD_ACTION_REMOVE) {
                            stringResource(R.string.sensor_handover_action_remove)
                        } else {
                            stringResource(R.string.sensor_handover_action_deactivate)
                        },
                        icon = Icons.Default.DeleteSweep,
                        iconTint = advColor,
                        position = CardPosition.SINGLE,
                        onClick = { showSensorHandoverActionDialog = true }
                    )
                }
                }
            }
            if ((category == null || category == "devices") && matches(R.string.meterlist, R.string.glucose_meters_desc)) {
                item(key = "meterlist") {
                SettingsItem(
                    title = stringResource(R.string.meterlist),
                    subtitle = stringResource(R.string.glucose_meters_desc),
                    icon = Icons.Default.Bloodtype,
                    iconTint = exchangeColor,
                    position = CardPosition.SINGLE,
                    onClick = { navController.navigate("settings/glucose-meters") }
                )
                }
            }
            if ((category == null || category == "devices") && matches(R.string.insulin_pens_title, R.string.enabled_status, R.string.disabled_status)) {
                item(key = "insulin_pens_title") {
                SettingsNavSwitchItem(
                    title = stringResource(R.string.insulin_pens_title),
                    subtitle = stringResource(
                        if (insulinPensEnabled) R.string.enabled_status else R.string.disabled_status
                    ),
                    checked = insulinPensEnabled,
                    onCheckedChange = { tk.glucodata.InsulinPenManager.setEnabled(it) },
                    onClick = { navController.navigate("settings/insulin-pens") },
                    icon = Icons.Default.Vaccines,
                    iconTint = exchangeColor,
                    position = CardPosition.SINGLE
                )
                }
            }
            if ((category == null || category == "devices") && matchesTopic("watches", R.string.watches)) {
                item(key = "watches") {
                SettingsItem(
                    title = stringResource(R.string.watches),
                    subtitle = "WearOS, Watchdrip, GadgetBridge, Kerfstok",
                    icon = Icons.Default.Devices,
                    iconTint = exchangeColor,
                    position = CardPosition.SINGLE,
                    onClick = { navController.navigate("settings/watch") }
                )
                }
            }
            if ((category == null || category == "processing") && matchesTopic("manual_calibration", R.string.manual_calibration, R.string.enabled_status, R.string.disabled_status)) {
                item(key = "manual_calibration") {
                SettingsNavSwitchItem(
                    title = stringResource(R.string.manual_calibration),
                    subtitle = "$calibrationModeLabel · ${
                        stringResource(
                            if (calibrationEnabled) R.string.enabled_status else R.string.disabled_status
                        )
                    }",
                    checked = calibrationEnabled,
                    onCheckedChange = {
                        CalibrationManager.setEnabledForMode(isRawCalibrationMode, it, sensorName)
                    },
                    onClick = { navController.navigate("settings/calibrations") },
                    icon = Icons.Default.WaterDrop,
                    iconTint = glucoseColor,
                    position = CardPosition.SINGLE
                )
                }
            }
            if ((category == null || category == "processing") && matches(R.string.graph_smoothing_title)) {
                item(key = "graph_smoothing_title") {
                SettingsNavSwitchItem(
                    title = stringResource(R.string.graph_smoothing_title),
                    subtitle = graphSmoothingLabel,
                    checked = chartSmoothingMinutes > 0,
                    onCheckedChange = { viewModel.setDataSmoothingEnabled(it) },
                    onClick = { navController.navigate("settings/data-smoothing") },
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    iconTint = glucoseColor,
                    position = CardPosition.SINGLE
                )
                }
            }
            if ((category == null || category == "processing") && matches(R.string.predictive_simulation_title, R.string.enabled_status, R.string.disabled_status)) {
                item(key = "predictive_simulation_title") {
                SettingsNavSwitchItem(
                    title = stringResource(R.string.predictive_simulation_title),
                    subtitle = stringResource(
                        if (predictiveSimulationEnabled) R.string.enabled_status else R.string.disabled_status
                    ),
                    checked = predictiveSimulationEnabled,
                    onCheckedChange = { viewModel.setPredictiveSimulationEnabled(it) },
                    onClick = { navController.navigate("settings/predictive-simulation") },
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    iconTint = glucoseColor,
                    position = CardPosition.SINGLE
                )
                }
            }
            if ((category == null || category == "processing") && matches(R.string.freeze_displayed_values, R.string.freeze_displayed_values_on, R.string.freeze_displayed_values_off)) {
                item(key = "freeze_displayed_values") {
                SettingsSwitchItem(
                    title = stringResource(R.string.freeze_displayed_values),
                    subtitle = stringResource(
                        if (freezeDisplayedValues) {
                            R.string.freeze_displayed_values_on
                        } else {
                            R.string.freeze_displayed_values_off
                        }
                    ),
                    checked = freezeDisplayedValues,
                    onCheckedChange = {
                        tk.glucodata.data.calibration.CalibrationManager
                            .setFreezeDisplayedValues(it)
                    },
                    icon = Icons.Default.Lock,
                    iconTint = dataColor,
                    position = CardPosition.SINGLE
                )
                }
            }
            if ((category == null || category == "journal") && matchesTopic("journal_title", R.string.journal_title, R.string.enabled_status, R.string.disabled_status)) {
                item(key = "journal_title") {
                SettingsNavSwitchItem(
                    title = stringResource(R.string.journal_title),
                    subtitle = stringResource(
                        if (journalEnabled) R.string.enabled_status else R.string.disabled_status
                    ),
                    checked = journalEnabled,
                    onCheckedChange = { viewModel.setJournalEnabled(it) },
                    onClick = { navController.navigate("settings/journal") },
                    icon = Icons.Default.Vaccines,
                    iconTint = glucoseColor,
                    position = CardPosition.SINGLE
                )
                }
            }
            if ((category == null || category == "connections") && matches(R.string.xdripbroadcast, R.string.patchedlibrebroadcast)) {
                item(key = "xdripbroadcast") {
                SettingsSwitchItem(
                    title = stringResource(R.string.xdripbroadcast),
                    subtitle = stringResource(R.string.patchedlibrebroadcast),
                    checked = patchedLibreEnabled,
                    icon = Icons.Default.Share,
                    iconTint = exchangeColor,
                    position = CardPosition.SINGLE,
                    onCheckedChange = { viewModel.togglePatchedLibreBroadcast(it) }
                )
                }
            }
            if ((category == null || category == "connections") && matches(R.string.xdrip_compatible_title, R.string.xdrip_compatible_desc)) {
                item(key = "xdrip_compatible_title") {
                SettingsSwitchItem(
                    title = stringResource(R.string.xdrip_compatible_title),
                    subtitle = stringResource(R.string.xdrip_compatible_desc),
                    checked = xdripEnabled,
                    icon = Icons.Default.Radio,
                    iconTint = exchangeColor,
                    position = CardPosition.SINGLE,
                    onCheckedChange = { viewModel.toggleXDripBroadcast(it) }
                )
                // Only the xDrip-style broadcast carries the source name. Stay visible while
                // enabled, so turning the broadcast off does not strand the claim out of reach.
                }
            }
            if ((xdripEnabled || xdripReportAsLibre2) && (category == null || category == "connections") && matches(R.string.xdrip_report_as_libre2_title, R.string.xdrip_report_as_libre2_desc)) {
                item(key = "xdrip_report_as_libre2_title") {
                if (xdripEnabled || xdripReportAsLibre2) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.xdrip_report_as_libre2_title),
                        subtitle = stringResource(R.string.xdrip_report_as_libre2_desc),
                        checked = xdripReportAsLibre2,
                        icon = Icons.Default.Badge,
                        iconTint = exchangeColor,
                        position = CardPosition.SINGLE,
                        onCheckedChange = { viewModel.setXdripReportAsLibre2(it) }
                    )
                }
                }
            }
            if ((category == null || category == "connections") && matches(R.string.aaps_broadcast, R.string.glucodata_subtitle)) {
                item(key = "aaps_broadcast") {
                SettingsSwitchItem(
                    title = stringResource(R.string.aaps_broadcast),
                    subtitle = stringResource(R.string.glucodata_subtitle),
                    checked = glucodataBroadcastEnabled,
                    icon = Icons.AutoMirrored.Filled.Send,
                    iconTint = exchangeColor,
                    position = CardPosition.SINGLE,
                    onCheckedChange = { viewModel.toggleGlucodataBroadcast(it) }
                )
                // The computed trend only ever reaches an ExchangeGlucosePayload consumer, so the
                // row is noise until one of them is on. Stay visible while the setting itself is
                // enabled, otherwise turning the last consumer off would strand it out of reach.
                }
            }
            if ((anyExchangeConsumer || broadcastComputedTrend) && (category == null || category == "connections") && matches(R.string.broadcast_computed_trend_title, R.string.broadcast_computed_trend_desc)) {
                item(key = "broadcast_computed_trend_title") {
                if (anyExchangeConsumer || broadcastComputedTrend) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.broadcast_computed_trend_title),
                        subtitle = stringResource(R.string.broadcast_computed_trend_desc),
                        checked = broadcastComputedTrend,
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        iconTint = exchangeColor,
                        position = CardPosition.SINGLE,
                        onCheckedChange = { viewModel.setBroadcastComputedTrend(it) }
                    )
                }
                }
            }
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) && (category == null || category == "connections") && matches(R.string.health_connect_title, R.string.health_connect_desc)) {
                item(key = "health_connect_title") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.health_connect_title),
                        subtitle = stringResource(R.string.health_connect_desc),
                        checked = healthConnectEnabled,
                        icon = Icons.Default.HealthAndSafety,
                        iconTint = exchangeColor,
                        position = CardPosition.SINGLE,
                        onCheckedChange = { enabled ->
                            Natives.sethealthConnect(enabled)
                            healthConnectEnabled = enabled
                            if (enabled) {
                                MainActivity.tryHealth = 5
                                (context.findActivity() as? MainActivity)?.let(HealthConnection::init)
                            } else {
                                MainActivity.tryHealth = 0
                                HealthConnection.stop()
                            }
                        }
                    )
                }
                }
            }
            if ((category == null || category == "connections") && matches(R.string.outbound_api_title, R.string.outbound_api_desc)) {
                item(key = "outbound_api_title") {
                SettingsItem(
                    title = stringResource(R.string.outbound_api_title),
                    subtitle = stringResource(R.string.outbound_api_desc),
                    icon = Icons.Default.CloudUpload,
                    iconTint = exchangeColor,
                    position = CardPosition.SINGLE,
                    onClick = { navController.navigate("settings/outbound-api") }
                )
                }
            }
            if ((category == null || category == "connections") && matches(R.string.mirror, R.string.mirror_desc)) {
                item(key = "mirror") {
                SettingsItem(
                    title = stringResource(R.string.mirror),
                    subtitle = stringResource(R.string.mirror_desc),
                    icon = Icons.Default.Devices,
                    iconTint = exchangeColor,
                    position = CardPosition.SINGLE,
                    onClick = { navController.navigate("settings/mirror") }
                )
                // Edit 67b: Determine if LibreView is visible to adjust card positions
                }
            }
            if ((category == null || category == "connections") && matches(R.string.nightscout_config, R.string.enabled_status, R.string.disabled_status)) {
                item(key = "nightscout_config") {
                SettingsNavSwitchItem(
                    title = stringResource(R.string.nightscout_config),
                    subtitle = stringResource(
                        if (nightscoutActive) R.string.enabled_status else R.string.disabled_status
                    ),
                    checked = nightscoutActive,
                    onCheckedChange = {
                        setNightscoutActive(context, it)
                        nightscoutActive = it
                    },
                    onClick = { navController.navigate("settings/nightscout") },
                    icon = Icons.Default.CloudUpload,
                    iconTint = exchangeColor,
                    position = CardPosition.SINGLE
                )
                }
            }
            if (showLibreView && (category == null || category == "connections") && matches(R.string.libreview_config, R.string.libreview_desc)) {
                item(key = "libreview_config") {
                if (showLibreView) {
                    SettingsItem(
                        title = stringResource(R.string.libreview_config),
                        subtitle = stringResource(R.string.libreview_desc),
                        icon = Icons.Default.Cloud,
                        iconTint = exchangeColor,
                        position = CardPosition.SINGLE,
                        onClick = { navController.navigate("settings/libreview") }
                    )
                }
                }
            }
            if (showMqAccount && (category == null || category == "connections") && matches(R.string.mq_account_title, R.string.mq_account_linked_desc)) {
                item(key = "mq_account_title") {
                if (showMqAccount) {
                    SettingsItem(
                        title = stringResource(R.string.mq_account_title),
                        subtitle = stringResource(R.string.mq_account_linked_desc),
                        icon = Icons.Default.Cloud,
                        iconTint = exchangeColor,
                        position = CardPosition.SINGLE,
                        onClick = { navController.navigate("settings/mq-account") }
                    )
                }
                }
            }
            if (showOttaiSettings && (category == null || category == "connections") && matches(R.string.ottai_setup_title, R.string.ottai_settings_desc)) {
                item(key = "ottai_setup_title") {
                if (showOttaiSettings) {
                    SettingsItem(
                        title = stringResource(R.string.ottai_setup_title),
                        subtitle = stringResource(R.string.ottai_settings_desc),
                        icon = Icons.Default.Sensors,
                        iconTint = exchangeColor,
                        position = CardPosition.SINGLE,
                        onClick = { navController.navigate("settings/ottai") }
                    )
                }
                }
            }
            if ((category == null || category == "connections") && matches(R.string.webserver, R.string.enabled_status, R.string.disabled_status)) {
                item(key = "webserver") {
                SettingsNavSwitchItem(
                    title = stringResource(R.string.webserver),
                    subtitle = stringResource(
                        if (webServerActive) R.string.enabled_status else R.string.disabled_status
                    ),
                    checked = webServerActive,
                    onCheckedChange = {
                        Natives.setusexdripwebserver(it)
                        webServerActive = it
                    },
                    onClick = { navController.navigate("settings/webserver") },
                    icon = Icons.Default.Language,
                    iconTint = exchangeColor,
                    position = CardPosition.SINGLE
                )
                }
            }
            if ((category == null || category == "data") && matches(R.string.scheduled_backup_title)) {
                item(key = "scheduled_backup_title") {
                SettingsItem(
                    title = stringResource(R.string.scheduled_backup_title),
                    subtitle = scheduledBackupSummary(scheduledBackupConfig),
                    icon = Icons.Default.Backup,
                    iconTint = dataColor,
                    position = CardPosition.SINGLE,
                    onClick = { showScheduledBackupDialog = true }
                )
                }
            }
            if ((category == null || category == "data") && matches(R.string.export_data_settings, R.string.export_data_settings_desc)) {
                item(key = "export_data_settings") {
                SettingsItem(
                    title = stringResource(R.string.export_data_settings),
                    subtitle = stringResource(R.string.export_data_settings_desc),
                    icon = androidx.compose.material.icons.Icons.Default.CloudUpload,
                    iconTint = dataColor,
                    position = CardPosition.SINGLE,
                    onClick = { showExportDialog = true }
                )
                }
            }
            if ((category == null || category == "data") && matches(R.string.import_data_settings, R.string.import_data_settings_desc)) {
                item(key = "import_data_settings") {
                SettingsItem(
                    title = stringResource(R.string.import_data_settings),
                    subtitle = stringResource(R.string.import_data_settings_desc),
                    icon = Icons.Default.FolderOpen,
                    iconTint = dataColor,
                    position = CardPosition.SINGLE,
                    onClick = { 
                        importLauncher.launch(
                            arrayOf(
                                "application/json",
                                "application/gzip",
                                "application/zstd",
                                "text/*",
                                "text/csv",
                                "text/tab-separated-values",
                                "*/*"
                            )
                        )
                    }
                )

                // A reading's displayed value is a record, not a derivation —
                // see tk.glucodata.data.ReadingDisplay. Its subtitle carries the
                // current behaviour so the common question is answered in place.
                }
            }
            if ((category == null || category == "app") && matchesTopic("cgm_readiness_title", R.string.cgm_readiness_title, R.string.cgm_readiness_settings_desc)) {
                item(key = "cgm_readiness_title") {
                SettingsItem(
                    title = stringResource(R.string.cgm_readiness_title),
                    subtitle = stringResource(R.string.cgm_readiness_settings_desc),
                    icon = Icons.Default.Security,
                    iconTint = advColor,
                    position = CardPosition.SINGLE,
                    onClick = { navController.navigate("settings/cgm-readiness") }
                )
                }
            }
            if ((category == null || category == "app") && matches(R.string.debug_logs, R.string.debug_logs_desc)) {
                item(key = "debug_logs") {
                SettingsItem(
                    title = stringResource(R.string.debug_logs),
                    subtitle = stringResource(R.string.debug_logs_desc),
                    icon = Icons.Default.BugReport,
                    iconTint = advColor,
                    position = CardPosition.SINGLE,
                    onClick = { navController.navigate("settings/debug") }
                )
                }
            }
            if ((category == null || category == "app") && matches(R.string.app_updates_title)) {
                item(key = "app_updates_title") {
                AppUpdatesSettingsItem(
                    iconTint = dataColor,
                    position = CardPosition.SINGLE,
                    onOpen = { navController.navigate("settings/app-updates") }
                )
                }
            }
            if ((category == null || category == "app") && matches(R.string.restart_app, R.string.restart_app_desc)) {
                item(key = "restart_app") {
                DangerItem(
                    title = stringResource(R.string.restart_app),
                    subtitle = stringResource(R.string.restart_app_desc),
                    icon = Icons.Filled.Refresh,
                    position = CardPosition.SINGLE,
                    severity = DangerSeverity.LOW,
                    onClick = { context.findActivity()?.fullRestart() }
                )
                }
            }
            if ((category == null || category == "reset") && matches(R.string.clear_history, R.string.clear_history_desc_short)) {
                item(key = "clear_history") {
                DangerItem(
                    title = stringResource(R.string.clear_history),
                    subtitle = stringResource(R.string.clear_history_desc_short),
                    icon = Icons.Filled.History,
                    position = CardPosition.SINGLE,
                    severity = DangerSeverity.LOW,
                    onClick = { showClearHistoryDialog = true }
                )
                }
            }
            if ((category == null || category == "reset") && matches(R.string.clear_app_data, R.string.clear_app_data_desc)) {
                item(key = "clear_app_data") {
                DangerItem(
                    title = stringResource(R.string.clear_app_data),
                    subtitle = stringResource(R.string.clear_app_data_desc),
                    icon = Icons.Filled.Delete,
                    position = CardPosition.SINGLE,
                    severity = DangerSeverity.MEDIUM,
                    onClick = { showClearDataDialog = true }
                )
                }
            }
            if ((category == null || category == "reset") && matches(R.string.factory_reset, R.string.factory_reset_desc)) {
                item(key = "factory_reset") {
                DangerItem(
                    title = stringResource(R.string.factory_reset),
                    subtitle = stringResource(R.string.factory_reset_desc),
                    icon = Icons.Filled.Warning,
                    position = CardPosition.SINGLE,
                    severity = DangerSeverity.HIGH,
                    onClick = { showFactoryResetDialog = true }
                )
                }
            }
            if ((category == null || category == "connections") && matches(R.string.api_source_settings_title)) {
                item(key = "direct_api_source_settings_title") {
                    SettingsItem(
                        title = stringResource(R.string.api_source_settings_title),
                        subtitle = stringResource(R.string.settings_category_connections_description),
                        icon = Icons.Default.Cloud,
                        iconTint = MaterialTheme.colorScheme.primary,
                        position = CardPosition.SINGLE,
                        onClick = { navController.navigate("settings/api-source") },
                    )
                }
            }
            if ((category == null || category == "alarms") && matches(R.string.talker)) {
                item(key = "direct_talker") {
                    SettingsItem(
                        title = stringResource(R.string.talker),
                        subtitle = stringResource(R.string.settings_category_alarms_description),
                        icon = Icons.Default.VolumeUp,
                        iconTint = MaterialTheme.colorScheme.primary,
                        position = CardPosition.SINGLE,
                        onClick = { navController.navigate("settings/alerts/talker") },
                    )
                }
            }
            if (category == "app" && normalizedQuery.isEmpty()) {
                item(key = "reset_link") {
                    SettingsItem(
                        title = stringResource(R.string.settings_category_reset),
                        subtitle = stringResource(R.string.settings_reset_description),
                        icon = Icons.Default.Warning,
                        iconTint = MaterialTheme.colorScheme.error,
                        position = CardPosition.SINGLE,
                        onClick = { navController.navigate("settings/category/reset") },
                    )
                }
            }
            if (category == "app" || (category == null && normalizedQuery.isNotEmpty() && matches(R.string.about_text))) {
        item(key = "about") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                // ui-guardrails: allow text_literal - a version number, the same in every language
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
            }
        }
    }

    // Dialogs
    if (showUnitDialog) UnitPickerDialog(isMmol, { 
        viewModel.setUnit(it)
        showUnitDialog = false
        context.findActivity()?.hardRestart() 
    }, { showUnitDialog = false })
    if (showThemeDialog) ThemePickerDialog(themeMode, { onThemeChanged(it); showThemeDialog = false }, { showThemeDialog = false })
    if (showSensorHandoverActionDialog) SensorHandoverActionPickerDialog(
        currentAction = sensorHandoverAction,
        onSelect = {
            handoverPrefs.edit().putInt(SensorHandoverRuntime.PREF_OLD_ACTION, it).apply()
            sensorHandoverAction = it
            showSensorHandoverActionDialog = false
        },
        onDismiss = { showSensorHandoverActionDialog = false }
    )
    if (showLanguageDialog) LanguagePickerDialog { showLanguageDialog = false }
    if (showClearHistoryDialog) ConfirmActionDialog(stringResource(R.string.clean_history_confirm), stringResource(R.string.clear_history_desc_long), Icons.Filled.History, { scope.launch { tk.glucodata.data.DataManagement.clearHistory() }; showClearHistoryDialog = false }, { showClearHistoryDialog = false })
    if (showClearDataDialog) ConfirmActionDialog(
        stringResource(R.string.clean_data_confirm), 
        stringResource(R.string.clear_app_data_desc), 
        Icons.Filled.Delete, 
        { 
            scope.launch { 
                tk.glucodata.data.DataManagement.clearAppData()
                // Full restart to kill process and clear native memory
                context.findActivity()?.fullRestart()
            }
            showClearDataDialog = false 
        }, 
        { showClearDataDialog = false }, 
        isDestructive = true
    )
    if (showFactoryResetDialog) ConfirmActionDialog(
        stringResource(R.string.factory_reset_confirm), 
        stringResource(R.string.factory_reset_alert), 
        Icons.Filled.Warning, 
        { 
            scope.launch { 
                tk.glucodata.data.DataManagement.factoryReset()
                // Full restart to kill process and clear native memory
                context.findActivity()?.fullRestart()
            }
        }, 
        { showFactoryResetDialog = false }, 
        isDestructive = true
    )
    
    if (showExportDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ExportDataSettingsSheet(
            onDismiss = { showExportDialog = false },
            sheetState = sheetState
        )
    }
    if (showScheduledBackupDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ScheduledBackupSettingsSheet(
            onDismiss = { showScheduledBackupDialog = false },
            sheetState = sheetState,
            onConfigurationChanged = { scheduledBackupConfig = it }
        )
    }
    pendingSettingsImportUri?.let { uri ->
        ConfirmActionDialog(
            title = stringResource(R.string.settings_import_confirm_title),
            message = stringResource(R.string.settings_import_confirm_message),
            icon = Icons.Default.Settings,
            onConfirm = {
                pendingSettingsImportUri = null
                scope.launch {
                    val result = tk.glucodata.data.SettingsExporter.importFromJson(context, uri)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_import_successful),
                                Toast.LENGTH_LONG
                            ).show()
                            context.findActivity()?.fullRestart()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.import_failed_with_error,
                                    result.exceptionOrNull()?.localizedMessage
                                        ?: context.getString(R.string.unknown_error)
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            onDismiss = { pendingSettingsImportUri = null }
        )
    }
    pendingExportPackageImportUri?.let { uri ->
        ConfirmActionDialog(
            title = stringResource(R.string.import_data_settings),
            message = stringResource(R.string.settings_import_confirm_message),
            icon = Icons.Default.FolderOpen,
            onConfirm = {
                pendingExportPackageImportUri = null
                scope.launch {
                    val result = tk.glucodata.data.ExportPackageExporter.importFromJson(context, uri)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            val summary = result.getOrThrow()
                            Toast.makeText(
                                context,
                                if (summary.restartRequired) {
                                    context.getString(R.string.settings_import_successful)
                                } else {
                                    context.getString(
                                        R.string.imported_readings_count,
                                        summary.historyReadings
                                    )
                                },
                                Toast.LENGTH_LONG
                            ).show()
                            if (summary.historyReadings > 0) {
                                // Pin the imported serial for display (when idle) so the
                                // dashboard chart shows the imported glucose. Persisted, so
                                // it survives the restart below.
                                viewModel.onHistoryImported(summary.historyDisplaySerial)
                            } else {
                                viewModel.refreshData()
                            }
                            if (summary.restartRequired) {
                                context.findActivity()?.fullRestart()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.import_failed_with_error,
                                    result.exceptionOrNull()?.localizedMessage
                                        ?: context.getString(R.string.unknown_error)
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            onDismiss = { pendingExportPackageImportUri = null }
        )
    }

    // ... Bottom of function ...

}


@Composable
fun PredictiveSimulationSettingsScreen(
    navController: NavController,
    viewModel: DashboardViewModel
) {
    val journalEnabled by viewModel.journalEnabled.collectAsStateWithLifecycle()
    val predictiveSimulationEnabled by viewModel.predictiveSimulationEnabled.collectAsStateWithLifecycle()
    val notificationChartPredictionEnabled by viewModel.predictiveSimulationNotificationChartEnabled.collectAsStateWithLifecycle()
    val trendMomentumEnabled by viewModel.predictionTrendMomentumEnabled.collectAsStateWithLifecycle()
    val modelProfile by viewModel.predictionModelProfile.collectAsStateWithLifecycle()
    val horizonMinutes by viewModel.predictionHorizonMinutes.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.predictive_simulation_title),
                onNavigateBack = { navController.popBackStack() },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "master") {
                MasterSwitchCard(
                    title = stringResource(R.string.predictive_simulation_title),
                    subtitle = stringResource(R.string.predictive_simulation_summary),
                    checked = predictiveSimulationEnabled,
                    onCheckedChange = { viewModel.setPredictiveSimulationEnabled(it) },
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    iconTint = MaterialTheme.colorScheme.primary
                )
            }

            item(key = "behavior") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.predictive_trend_momentum),
                        checked = trendMomentumEnabled,
                        onCheckedChange = { viewModel.setPredictionTrendMomentumEnabled(it) },
                        position = CardPosition.TOP,
                        enabled = predictiveSimulationEnabled
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.predictive_notification_chart),
                        subtitle = stringResource(R.string.predictive_notification_chart_summary),
                        checked = notificationChartPredictionEnabled,
                        onCheckedChange = { viewModel.setPredictiveSimulationNotificationChartEnabled(it) },
                        position = CardPosition.BOTTOM,
                        enabled = predictiveSimulationEnabled
                    )
                }
            }

            item(key = "horizon") {
                PredictiveSimulationSettingsCard(enabled = predictiveSimulationEnabled) {
                    PredictiveSimulationParameterRow(
                        title = stringResource(R.string.predictive_forecast_horizon),
                        valueLabel = stringResource(R.string.predictive_horizon_value, horizonMinutes),
                        value = horizonMinutes.toFloat(),
                        valueRange = 30f..360f,
                        enabled = predictiveSimulationEnabled,
                        onValueChange = { viewModel.setPredictionHorizonMinutes(it.roundToInt()) }
                    )
                }
            }

            if (journalEnabled) {
                item(key = "model") {
                    SettingsItem(
                        title = stringResource(R.string.predictive_model_tuning),
                        subtitle = if (modelProfile.blocks.size == 1) {
                            stringResource(R.string.predictive_model_profile_summary_single)
                        } else {
                            stringResource(
                                R.string.predictive_model_profile_summary_count,
                                modelProfile.blocks.size
                            )
                        },
                        onClick = {
                            navController.navigate("settings/predictive-simulation/model-profile")
                        },
                        icon = Icons.Default.Schedule,
                        iconTint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PredictiveSimulationSettingsCard(
    enabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.66f),
        shape = cardShape(CardPosition.SINGLE),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            content = content
        )
    }
}

@Composable
internal fun PredictiveSimulationParameterRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled
        )
    }
}

// Components moved to tk.glucodata.ui.components.SettingsComponents.kt

// ============================================================================
// DIALOGS - M3 Expressive Style with proper layout
// ============================================================================

@Composable
private fun UnitPickerDialog(isMmol: Boolean, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(R.string.select_unit),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                listOf(stringResource(R.string.unit_mg) to 2, stringResource(R.string.unit_mmol) to 1).forEach { (label, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onSelect(value) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (if (isMmol) 1 else 2) == value, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun ThemePickerDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(R.string.choose_theme),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                ThemeMode.values().forEach { mode ->
                    val label = when(mode) { ThemeMode.SYSTEM -> stringResource(R.string.theme_system); ThemeMode.LIGHT -> stringResource(R.string.theme_light); ThemeMode.DARK -> stringResource(R.string.theme_dark) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == mode, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun SensorHandoverActionPickerDialog(
    currentAction: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        stringResource(R.string.sensor_handover_action_deactivate) to SensorHandoverRuntime.OLD_ACTION_DEACTIVATE,
        stringResource(R.string.sensor_handover_action_remove) to SensorHandoverRuntime.OLD_ACTION_REMOVE
    )
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(R.string.sensor_handover_old_action_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Text(
                    text = stringResource(R.string.sensor_handover_old_action_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                options.forEach { (label, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onSelect(value) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentAction == value, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
internal fun PreviewWindowPickerDialog(
    currentMode: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        stringResource(R.string.preview_window_expanded_only) to 0,
        stringResource(R.string.preview_window_always) to 1,
        stringResource(R.string.preview_window_never) to 2
    )
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(R.string.preview_window_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Text(
                    text = stringResource(R.string.preview_window_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                options.forEach { (label, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { onSelect(value) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentMode == value, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(onDismiss: () -> Unit) {
    val languages = listOf(
        "System" to null,
        "English" to "en",
        "Belarusian" to "be",
        "Chinese" to "zh",
        "German" to "de",
        "French" to "fr",
        "Hungarian" to "hu",
        "Italian" to "it",
        "Dutch" to "nl",
        "Polish" to "pl",
        "Portuguese" to "pt",
        "Russian" to "ru",
        "Swedish" to "sv",
        "Somali" to "so",
        "Turkish" to "tr",
        "Ukrainian" to "uk",
        "Mongolian" to "mn",
    )
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Text(
                    text = stringResource(R.string.select_language),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    languages.forEach { (name, tag) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable {
                                    val locale = if (tag != null) LocaleListCompat.forLanguageTags(tag) else LocaleListCompat.getEmptyLocaleList()
                                    AppCompatDelegate.setApplicationLocales(locale)
                                    onDismiss()
                                }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if ((tag == null && AppCompatDelegate.getApplicationLocales().isEmpty) ||
                                (tag != null && AppCompatDelegate.getApplicationLocales().toLanguageTags().contains(tag))) {
                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null, tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (isDestructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors(),
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) { Text(if (isDestructive) stringResource(R.string.delete) else stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes(), contentPadding = ButtonDefaults.TextButtonContentPadding) { Text(stringResource(R.string.cancel)) } }
    )
}

/**
 * True when a currently active sensor is genuinely a Libre 2 / Libre 3.
 *
 * Deliberately does NOT reuse the native Libre check used for NFC readiness:
 * native `isLibre2()`/`isLibre3()` are defined by *exclusion* (anything that is
 * not AccuChek/Sibionics/Dexcom is reported as Libre), so Kotlin-driver sensors
 * such as Ottai — which have no native family flag but are mirrored into the
 * native glucose stream — come back as "Libre". Here we require a positive
 * LIBRE2/LIBRE3 kind and additionally drop any sensor claimed by a managed
 * Kotlin BLE driver.
 */
private fun hasActiveLibreSensorForLibreView(): Boolean = runCatching {
    Natives.activeSensors()?.filterNotNull().orEmpty().any { sensorId ->
        // Owned by a Kotlin BLE driver (Ottai, MQ, iCan, AiDex, Anytime, Sibionics) -> not Libre.
        val managed = runCatching { ManagedSensorRuntime.resolveDriver(sensorId) }.getOrNull()
        if (managed != null) return@any false
        val kind = runCatching {
            SensorSourceResolver.resolveSensorKind(sensorId, SensorSourceResolver.SENSOR_KIND_UNKNOWN)
        }.getOrDefault(SensorSourceResolver.SENSOR_KIND_UNKNOWN)
        kind == SensorSourceResolver.SENSOR_KIND_LIBRE2 || kind == SensorSourceResolver.SENSOR_KIND_LIBRE3
    }
}.getOrDefault(false)
