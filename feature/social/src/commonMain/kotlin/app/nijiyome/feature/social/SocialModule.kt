// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.feature.social

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Parity wave B (16 §3.8, worker t): ViewModels of the social screens. The name is FINAL (NijiyomeApplication). */
val socialFeatureModule = module {
    viewModel { params -> ProfileViewModel(userName = params.getOrNull<String>(), repo = get()) }
    viewModel { params -> ActivityThreadViewModel(activityId = params.get<Int>(), repo = get(), lookup = getOrNull()) }
    viewModel { NotificationsViewModel(repo = get()) }
    viewModel { params -> SocialSlotViewModel(anilistId = params.get<Int>(), repo = get()) }
}
