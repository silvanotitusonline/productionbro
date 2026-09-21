package za.org.rtc.community.core.auth

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseUserIdentityTest {
    @Test
    fun `display name prefers application display name`() {
        val metadata = buildJsonObject {
            put("display_name", "Resident Name")
            put("full_name", "Google Name")
            put("name", "Provider Name")
        }

        assertEquals("Resident Name", SupabaseUserIdentity.displayName(metadata, "resident@example.com", "user-1"))
    }

    @Test
    fun `display name maps Google full name and falls back safely`() {
        val google = buildJsonObject { put("full_name", "Google Resident") }
        assertEquals("Google Resident", SupabaseUserIdentity.displayName(google, "resident@example.com", "user-1"))
        assertEquals("resident", SupabaseUserIdentity.displayName(null, "resident@example.com", "user-1"))
        assertEquals("user-1", SupabaseUserIdentity.displayName(null, null, "user-1"))
    }

    @Test
    fun `handle and avatar map provider metadata`() {
        val metadata = buildJsonObject {
            put("username", "resident_one")
            put("picture", "https://example.com/avatar.png")
        }

        assertEquals("@resident_one", SupabaseUserIdentity.handle(metadata, "resident@example.com", "user-1"))
        assertEquals("https://example.com/avatar.png", SupabaseUserIdentity.avatarUrl(metadata))
    }
}
