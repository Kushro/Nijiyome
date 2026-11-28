package app.nijiyome.ui.state

import app.nijiyome.ui.resources.Res
import app.nijiyome.ui.resources.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import app.nijiyome.designsystem.components.ErrorState
import app.nijiyome.designsystem.components.LoadingState
import app.nijiyome.designsystem.motion.contentReveal
import app.nijiyome.designsystem.motion.rememberRevealGate
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
    val reveal = rememberRevealGate()
    when (state) {
        LoadState.Loading -> {
            reveal.placeholderShown = true
            loading()
        }
        is LoadState.Err -> AppErrorState(
            cause = state.cause,
            onRetry = onRetry,
            modifier = modifier,
            title = stringResource(Res.string.ui_error_title),
        )
        // Content that replaced the placeholder fades in (CONTENT_REVEAL); content that was already loaded just appears.
        is LoadState.Ok -> Box(Modifier.contentReveal(reveal.placeholderShown)) { content(state.value) }
    }
}
