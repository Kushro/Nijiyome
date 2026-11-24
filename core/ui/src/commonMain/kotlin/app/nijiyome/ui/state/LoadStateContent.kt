package app.nijiyome.ui.state

import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.LoadingState
import app.nijiyome.model.LoadState

/** Renders the three states of a [LoadState] with the shared Loading / Error components. */
@Composable
fun <T> LoadStateContent(
    state: LoadState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { LoadingState(modifier) },
    content: @Composable (T) -> Unit,
) {
    when (state) {
        LoadState.Loading -> loading()
        is LoadState.Err -> ErrorState(
            message = state.cause.userMessage(),
            retryLabel = stringResource(Res.string.ui_retry),
            onRetry = onRetry,
            modifier = modifier,
            title = stringResource(Res.string.ui_error_title),
        )
        is LoadState.Ok -> content(state.value)
    }
}
