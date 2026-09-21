package za.org.rtc.community.ui.navigation

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.org.rtc.community.navigation.RouteAccessPolicy
import za.org.rtc.community.navigation.RtcRoute
import za.org.rtc.community.core.AdministratorMfaStatus
import za.org.rtc.community.core.SessionAuthority
import za.org.rtc.community.core.UserRole

class AccountProfileNavigationTest {
    @Test
    fun `resident profile route is distinct from staff My Work route`() {
        assertNotEquals(RtcRoute.MY_WORK, RtcRoute.ACCOUNT_PROFILE)
        assertTrue(
            RouteAccessPolicy.evaluate(
                route = RtcRoute.ACCOUNT_PROFILE,
                role = UserRole.RESIDENT_A,
                authority = SessionAuthority.SUPABASE_AUTH,
                mfaStatus = AdministratorMfaStatus.NOT_REQUIRED,
            ).allowed,
        )
    }
}
