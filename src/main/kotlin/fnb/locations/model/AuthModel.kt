package fnb.locations.model

import com.auth0.jwt.interfaces.DecodedJWT

data class EncodedTokens(
        val AccessToken: String?,
        val RefreshToken: String?)

data class DecodedTokens(
        val AccessToken: DecodedJWT?,
        val RefreshToken: DecodedJWT?)

data class User(
        val id: String,
        val password: ByteArray,
        val count: Int,
        val permissionLevel: UserPermissionLevel
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (id != other.id) return false
        if (!password.contentEquals(other.password)) return false
        if (count != other.count) return false
        if (permissionLevel != other.permissionLevel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + password.contentHashCode()
        result = 31 * result + count
        result = 31 * result + permissionLevel.hashCode()
        return result
    }
}

data class AuthResponse(val message: String,
                        val AccessToken: String?,
                        val RefreshToken: String?
)

enum class UserPermissionLevel {
    USER, ADMIN, SUPER_ADMIN
}