package fnb.graphql

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import fnb.model.*
import fnb.services.AuthService
import io.ktor.application.*
import org.slf4j.Logger


fun SchemaBuilder.authSchema(
    authService: AuthService
) {
    mutation("signIn") {
        resolver { email: String,
                   password: String,
                   ctx: Context
            ->
            val call = ctx.get<ApplicationCall>()!!
            val log = ctx.get<Logger>()!!
            val tokens = authService.signIn(call, email, password)
            val message = if ((tokens.AccessToken != null) && (tokens.RefreshToken != null)) {
                "Successfully signed in"
            } else {
                "Sign in failed"
            }
            log.info(message)
            AuthResponse(
                message,
                AccessToken = tokens.AccessToken,
                RefreshToken = tokens.RefreshToken
            )
        }
    }

    mutation("signUp") {
        resolver { email: String,
                   username: String,
                   password: String,
                   ctx: Context
            ->
            val call = ctx.get<ApplicationCall>()!!
            val tokens = authService.signUp(call, email, username, password)
            val message = if ((tokens.AccessToken != null) && (tokens.RefreshToken != null)) {
                "sign up successful"
            } else {
                "Sign up failed"
            }
            AuthResponse(
                message,
                AccessToken = tokens.AccessToken,
                RefreshToken = tokens.RefreshToken
            )
        }
    }
    type<UserData>()
    enum<LocationType>()
    type<Response>()
    type<AuthResponse>()
    type<UserDataResponse>()
}