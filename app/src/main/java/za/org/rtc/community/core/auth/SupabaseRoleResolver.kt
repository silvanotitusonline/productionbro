package za.org.rtc.community.core.auth

import za.org.rtc.community.core.UserRole

object SupabaseRoleResolver {
    fun fromWire(role: String?): UserRole = when (role?.trim()?.uppercase()) {
        "CASE_STAFF" -> UserRole.CASE_STAFF
        "CONTENT_EDITOR" -> UserRole.CONTENT_EDITOR
        "MODERATOR" -> UserRole.MODERATOR
        "EVIDENCE_REVIEWER" -> UserRole.EVIDENCE_REVIEWER
        "SYSTEM_ADMIN", "ADMIN" -> UserRole.SYSTEM_ADMIN
        "RESIDENT", "RESIDENT_A", "RESIDENT_B", null -> UserRole.RESIDENT_A
        else -> UserRole.RESIDENT_A
    }

    fun resolve(roles: Iterable<UserRole>, adminOverride: Boolean = false): UserRole {
        if (adminOverride || roles.any { it == UserRole.SYSTEM_ADMIN }) return UserRole.SYSTEM_ADMIN
        return roles.maxByOrNull(::precedence) ?: UserRole.RESIDENT_A
    }

    private fun precedence(role: UserRole): Int = when (role) {
        UserRole.SYSTEM_ADMIN -> 700
        UserRole.EVIDENCE_REVIEWER -> 600
        UserRole.MODERATOR -> 500
        UserRole.CONTENT_EDITOR -> 400
        UserRole.CASE_STAFF -> 300
        UserRole.RESIDENT_A, UserRole.RESIDENT_B -> 100
        UserRole.ANONYMOUS_PUBLIC -> 0
    }
}
