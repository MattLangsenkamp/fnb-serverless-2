package fnb.locations.model

import com.auth0.jwt.interfaces.DecodedJWT

data class EncodedTokens(
        val AccessToken: String?,
        val RefreshToken: String?)

data class DecodedTokens(
        val AccessToken: DecodedJWT?,
        val RefreshToken: DecodedJWT?)

data class User(
        val username: String,
        val password: String,
        val count: Int,
        val permissionLevel: UserPermissionLevel
)

data class AuthResponse(val message: String,
                        val AccessToken: String?,
                        val RefreshToken: String?
)

enum class UserPermissionLevel {
    USER, ADMIN, SUPER_ADMIN
}