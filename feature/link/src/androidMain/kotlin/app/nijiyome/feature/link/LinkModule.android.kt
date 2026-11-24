package app.nijiyome.feature.link

import android.os.Build

/** Android: this phone's model names it on the PC; BUG-132: the AVC decoder's limits disable the profiles it cannot play. */
val linkFeatureModule = linkFeatureModule(
    deviceName = { Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android" },
    decodeLimits = { app.nijiyome.link.AvcDecodeLimits.device() },
)
