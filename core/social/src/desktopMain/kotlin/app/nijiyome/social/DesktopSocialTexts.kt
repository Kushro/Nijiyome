// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.social

import java.util.Locale

// Hito 6 (D1): desktop has no Android resources, so the sentences of `values-es/strings.xml` of this module live here too (a
// desktop test checks they stay the same as the resources). The language is the one the app runs in (Locale.getDefault(), which
// the desktop app sets from Ajustes › Idioma).

/** Spanish templates of `social_notification_<key>` (androidMain `values-es/strings.xml`). */
val SPANISH_TEMPLATES: Map<String, String> = mapOf(
    "airing" to "Se emitió el episodio %1\$s de %2\$s.",
    "following" to "%1\$s empezó a seguirte.",
    "activity_message" to "%1\$s te envió un mensaje.",
    "activity_mention" to "%1\$s te mencionó en una actividad.",
    "activity_reply" to "%1\$s respondió a tu actividad.",
    "activity_reply_subscribed" to "%1\$s respondió a una actividad que sigues.",
    "activity_like" to "A %1\$s le gustó tu actividad.",
    "activity_reply_like" to "A %1\$s le gustó tu respuesta.",
    "thread_comment_mention" to "%1\$s te mencionó en un comentario del foro.",
    "thread_comment_reply" to "%1\$s respondió a tu comentario del foro.",
    "thread_subscribed" to "%1\$s comentó en un hilo que sigues.",
    "thread_like" to "A %1\$s le gustó tu hilo del foro.",
    "thread_comment_like" to "A %1\$s le gustó tu comentario del foro.",
    "related_media_addition" to "%1\$s se añadió a AniList.",
    "media_data_change" to "%1\$s se actualizó en AniList.",
    "media_merge" to "%1\$s se fusionó con otra entrada.",
    "media_deletion" to "%1\$s se eliminó de AniList.",
)

private fun spanish(locale: Locale) = locale.language == "es"

/** The notification sentences in the app's language (Spanish or English), looked up on each call. */
class DesktopSocialTexts(private val locale: () -> Locale = Locale::getDefault) : SocialTexts {
    override fun notification(type: String, userName: String?, mediaTitle: String?, episode: Int?, fallback: String): String {
        val templates = if (spanish(locale())) SPANISH_TEMPLATES else ENGLISH_TEMPLATES
        return localizedNotification(templates::get, type, userName, mediaTitle, episode, fallback)
    }
}

/** The system notification of new AniList notifications (`social_notification_title` / `social_notification_unread`). */
object SocialNoticeTexts {
    const val TITLE = "AniList"

    fun unread(count: Int, locale: Locale = Locale.getDefault()): String = when {
        spanish(locale) -> if (count == 1) "1 notificación nueva de AniList" else "$count notificaciones nuevas de AniList"
        else -> if (count == 1) "1 new AniList notification" else "$count new AniList notifications"
    }
}
