// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.settings

import app.nijiyome.data.ListErrors
import app.nijiyome.feature.settings.resources.Res
import app.nijiyome.feature.settings.resources.*
import app.nijiyome.model.AppError
import org.jetbrains.compose.resources.StringResource

// parity wave A (worker q, account.oauth.errors): the lists / OAuth failures of Settings › Listas y cuentas and their texts.
// AccountsViewModel and the section itself are in androidMain (they need ListsSyncControl of :core:data, Android only for now).

/** Why a provider cannot be linked / what went wrong, as a stable code the screen maps to a text ([oauthErrorRes]). */
enum class AccountsError { NO_CLIENT_ID, CANCELLED, INVALID_STATE, EXPIRED, EXCHANGE, DENIED, OFFLINE, RATE_LIMITED, HTTP, BAD_FILE, NOT_LINKED, TIMED_OUT, UNKNOWN }

/** Maps a lists / OAuth failure (see `ListErrors` in :core:data) to [AccountsError]. Pure, unit tested. */
fun AppError.toAccountsError(): AccountsError = when (this) {
    AppError.Offline -> AccountsError.OFFLINE
    is AppError.RateLimited -> AccountsError.RATE_LIMITED
    is AppError.Http -> if (code == 401) AccountsError.EXPIRED else AccountsError.HTTP
    is AppError.Extension -> AccountsError.UNKNOWN
    is AppError.Unknown -> when {
        msg.startsWith(ListErrors.NO_CLIENT_ID) -> AccountsError.NO_CLIENT_ID
        msg == ListErrors.CANCELLED -> AccountsError.CANCELLED
        msg == ListErrors.INVALID_STATE -> AccountsError.INVALID_STATE
        msg == ListErrors.EXPIRED -> AccountsError.EXPIRED
        msg == ListErrors.EXCHANGE -> AccountsError.EXCHANGE
        msg == "${ListErrors.DENIED}:${ListErrors.TIMEOUT_ERROR}" -> AccountsError.TIMED_OUT
        msg.startsWith(ListErrors.DENIED) -> AccountsError.DENIED
        msg == ListErrors.BAD_FILE -> AccountsError.BAD_FILE
        msg == ListErrors.NOT_LINKED -> AccountsError.NOT_LINKED
        else -> AccountsError.UNKNOWN
    }
}

internal fun AccountsError.messageRes(): StringResource = when (this) {
    AccountsError.NO_CLIENT_ID -> platformHint(Res.string.settings_accounts_no_client_id)
    AccountsError.CANCELLED -> Res.string.settings_accounts_error_cancelled
    AccountsError.INVALID_STATE -> Res.string.settings_accounts_error_state
    AccountsError.EXPIRED -> Res.string.settings_accounts_error_expired
    AccountsError.EXCHANGE -> Res.string.settings_accounts_error_exchange
    AccountsError.DENIED -> Res.string.settings_accounts_error_denied
    AccountsError.OFFLINE -> Res.string.settings_accounts_error_offline
    AccountsError.RATE_LIMITED -> Res.string.settings_accounts_error_rate
    AccountsError.HTTP -> Res.string.settings_accounts_error_http
    AccountsError.BAD_FILE -> Res.string.settings_accounts_error_file
    AccountsError.NOT_LINKED -> Res.string.settings_accounts_error_not_linked
    AccountsError.TIMED_OUT -> Res.string.settings_accounts_error_timeout
    AccountsError.UNKNOWN -> Res.string.settings_action_failed
}
