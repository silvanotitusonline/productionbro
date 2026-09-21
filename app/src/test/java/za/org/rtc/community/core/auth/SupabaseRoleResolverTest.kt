package za.org.rtc.community.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test
import za.org.rtc.community.core.UserRole

class SupabaseRoleResolverTest {
    @Test
    fun `resident wire roles resolve to resident`() {
        assertEquals(UserRole.RESIDENT_A, SupabaseRoleResolver.resolve(listOf(SupabaseRoleResolver.fromWire("RESIDENT"))))
    }

    @Test
    fun `staff claim is preserved for authenticated staff session`() {
        assertEquals(UserRole.CASE_STAFF, SupabaseRoleResolver.resolve(listOf(SupabaseRoleResolver.fromWire("CASE_STAFF"))))
        assertEquals(UserRole.MODERATOR, SupabaseRoleResolver.resolve(listOf(SupabaseRoleResolver.fromWire("MODERATOR"))))
    }

    @Test
    fun `multiple roles select highest precedence instead of downgrading to resident`() {
        assertEquals(
            UserRole.CONTENT_EDITOR,
            SupabaseRoleResolver.resolve(
                listOf(UserRole.RESIDENT_A, UserRole.CASE_STAFF, UserRole.CONTENT_EDITOR),
            ),
        )
    }

    @Test
    fun `admin override and wire admin claim resolve to system admin`() {
        assertEquals(UserRole.SYSTEM_ADMIN, SupabaseRoleResolver.resolve(emptyList(), adminOverride = true))
        assertEquals(UserRole.SYSTEM_ADMIN, SupabaseRoleResolver.fromWire("admin"))
    }
}
