package app.nijiyome.ui.state

import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.model.AppError
import app.nijiyome.model.AppException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Maps a failure of a repository call to an [AppError]. Never swallows cancellation: callers must rethrow it first
 * (`catch (e: CancellationException) { throw e }`) or use [runCatchingApp].
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppException -> error
    is IOException -> AppError.Offline
    else -> AppError.Unknown(message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName)
}

/** Like [runCatching] but rethrows [CancellationException] and returns the failure already mapped to [AppError]. */
suspend inline fun <T> runCatchingApp(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Human readable, localized cause (02 §5: "AniList limited requests", "extension X failed: timeout"). */
@Composable
fun AppError.userMessage(): String = when (this) {
    AppError.Offline -> stringResource(Res.string.ui_error_offline)
    is AppError.RateLimited -> stringResource(Res.string.ui_error_rate_limited, providerLabel(provider))
    is AppError.Extension -> stringResource(Res.string.ui_error_extension, id, msg)
    is AppError.Http -> stringResource(Res.string.ui_error_http, code)
    is AppError.Unknown -> stringResource(Res.string.ui_error_unknown, msg)
}

/** BUG-146: the name a metadata provider id is shown with ("anilist" → "AniList limitó las solicitudes…"); unknown ids as they are. */
fun providerLabel(id: String): String = when (id.lowercase()) {
    "anilist" -> "AniList"
    "kitsu" -> "Kitsu"
    "mal", "myanimelist" -> "MyAnimeList"
    "shikimori" -> "Shikimori"
    "tenrai" -> "Tenrai"
    else -> id
}

/** Non-composable twin of [userMessage], for snackbars raised from events (runs inside a coroutine). */
suspend fun AppError.userMessageText(): String = when (this) {
    AppError.Offline -> org.jetbrains.compose.resources.getString(Res.string.ui_error_offline)
    is AppError.RateLimited -> org.jetbrains.compose.resources.getString(Res.string.ui_error_rate_limited, providerLabel(provider))
    is AppError.Extension -> org.jetbrains.compose.resources.getString(Res.string.ui_error_extension, id, msg)
    is AppError.Http -> org.jetbrains.compose.resources.getString(Res.string.ui_error_http, code)
    is AppError.Unknown -> org.jetbrains.compose.resources.getString(Res.string.ui_error_unknown, msg)
}
