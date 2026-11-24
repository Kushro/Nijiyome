// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.social

import android.content.Context

/** Templates from the app's resources (`values` / `values-es`), so the sentence follows the app language. */
class AndroidSocialTexts(private val context: Context) : SocialTexts {
    override fun notification(type: String, userName: String?, mediaTitle: String?, episode: Int?, fallback: String): String =
        localizedNotification(::template, type, userName, mediaTitle, episode, fallback)

    private fun template(key: String): String? {
        val id = RES[key] ?: return null
        return runCatching { context.getString(id) }.getOrNull()
    }

    private companion object {
        val RES: Map<String, Int> = mapOf(
            "airing" to R.string.social_notification_airing,
            "following" to R.string.social_notification_following,
            "activity_message" to R.string.social_notification_activity_message,
            "activity_mention" to R.string.social_notification_activity_mention,
            "activity_reply" to R.string.social_notification_activity_reply,
            "activity_reply_subscribed" to R.string.social_notification_activity_reply_subscribed,
            "activity_like" to R.string.social_notification_activity_like,
            "activity_reply_like" to R.string.social_notification_activity_reply_like,
            "thread_comment_mention" to R.string.social_notification_thread_comment_mention,
            "thread_comment_reply" to R.string.social_notification_thread_comment_reply,
            "thread_subscribed" to R.string.social_notification_thread_subscribed,
            "thread_like" to R.string.social_notification_thread_like,
            "thread_comment_like" to R.string.social_notification_thread_comment_like,
            "related_media_addition" to R.string.social_notification_related_media_addition,
            "media_data_change" to R.string.social_notification_media_data_change,
            "media_merge" to R.string.social_notification_media_merge,
            "media_deletion" to R.string.social_notification_media_deletion,
        )
    }
}
