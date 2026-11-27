package tk.glucodata.update

import androidx.annotation.StringRes
import tk.glucodata.R

@StringRes
internal fun updateErrorResource(error: UpdateError): Int = when (error) {
        UpdateError.NETWORK -> R.string.app_updates_error_network
        UpdateError.RATE_LIMITED -> R.string.app_updates_error_rate_limited
        UpdateError.PARSE -> R.string.app_updates_error_parse
        UpdateError.NO_ARTIFACT -> R.string.app_updates_error_no_artifact
        UpdateError.CHECKSUM -> R.string.app_updates_error_checksum
        UpdateError.SIGNATURE -> R.string.app_updates_error_signature
        UpdateError.PACKAGE_MISMATCH -> R.string.app_updates_error_package
        UpdateError.DOWNGRADE -> R.string.app_updates_error_downgrade
        UpdateError.STORAGE -> R.string.app_updates_error_storage
        UpdateError.INSTALL_PERMISSION -> R.string.app_updates_error_install_permission
        UpdateError.INSTALL_FAILED -> R.string.app_updates_error_install_failed
        UpdateError.CANCELLED -> R.string.app_updates_error_cancelled
    }
