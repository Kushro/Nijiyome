// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.metadata

import app.nijiyome.model.MediaArtwork
import app.nijiyome.model.MediaRef

/** 0.3.2: a provider with title artwork (ani.zip / TVDB fanart, banner, poster, clear logo). */
interface ArtworkProvider {
    suspend fun artwork(ref: MediaRef): MediaArtwork?
}
