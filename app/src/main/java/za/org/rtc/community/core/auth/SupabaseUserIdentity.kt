package za.org.rtc.community.core.auth

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Maps one authenticated Supabase user consistently across Compose and data layers. */
object SupabaseUserIdentity {
    fun displayName(metadata: JsonObject?, email: String?, userId: String): String =
        firstValue(metadata, "display_name", "full_name", "name")
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: userId

    fun handle(metadata: JsonObject?, email: String?, userId: String): String {
        val raw = firstValue(metadata, "username", "user_name")
            ?: email?.substringBefore('@')
            ?: userId.take(8)
        val clean = raw.removePrefix("@").replace(Regex("[^A-Za-z0-9_]"), "").take(30)
        return "@${clean.ifBlank { "member" }}"
    }

    fun avatarUrl(metadata: JsonObject?): String? =
        firstValue(metadata, "avatar_url", "picture")

    private fun firstValue(metadata: JsonObject?, vararg keys: String): String? =
        keys.asSequence()
            .mapNotNull { key -> metadata?.get(key)?.jsonPrimitive?.contentOrNull }
            .map(String::trim)
            .firstOrNull(String::isNotBlank)
}
