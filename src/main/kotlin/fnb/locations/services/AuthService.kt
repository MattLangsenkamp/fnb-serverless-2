package fnb.locations.services

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.*
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import fnb.locations.model.DecodedTokens
import fnb.locations.model.EncodedTokens
import fnb.locations.model.User
import fnb.locations.model.UserPermissionLevel
import fnb.logging.MyLogger
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import io.ktor.application.ApplicationCall
import io.ktor.response.header
import io.ktor.util.date.GMTDate
import io.ktor.util.date.Month
import org.koin.core.KoinComponent
import org.koin.core.get
import java.util.*


private const val tableName: String = "fnb-auth"

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
class AuthService(private val client: AmazonDynamoDB) {
    /*private val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder
            .standard()
            .withCredentials(ProfileCredentialsProvider("fnb-admin"))
            .build()*/
    private val secret: String = "FakeSecret"
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)
    private val verifier: JWTVerifier = JWT.require(algorithm).build()

    /**
     * @param username the username of the user signing up
     * @param password the password of the user signing up
     *
     * @return EncodedTokens object with no null values if sign in successful
     * EncodedTokens object with two null values if unsuccessful
     */
    fun signIn(call: ApplicationCall, username: String, password: String): EncodedTokens {
        return try {
            // search dynamo for match
            val user = getUserInfo(username)

            if (password != user.password) error("Password incorrect")

            val permissionLevel = user.permissionLevel
            val count = user.count

            val accessToken = signAccessToken(username, permissionLevel.toString())
            val refreshToken = signRefreshToken(username, permissionLevel.toString(), count)

            setCookies(call, DecodedTokens(JWT.decode(accessToken), JWT.decode(refreshToken)))

            EncodedTokens(
                    AccessToken = accessToken,
                    RefreshToken = refreshToken
            )

        } catch (t: Throwable) {
            setCookies(call, DecodedTokens(null, null))
            EncodedTokens(
                    AccessToken = null,
                    RefreshToken = null
            )
        }
    }

    /**
     * @param username the username of the user signing up
     * @param password the password of the user signing up
     * @param permissionLevel the permission level of the user
     *
     * @return EncodedTokens object with no null values if sign in successful
     * EncodedTokens object with two null values if unsuccessful
     */
    fun signUp(call: ApplicationCall,
               username: String,
               password: String,
               permissionLevel: UserPermissionLevel = UserPermissionLevel.USER): EncodedTokens {
        return try {
           val item = mapOf(
                "username" to AttributeValue().apply { s = username },
                "password" to AttributeValue().apply { s = password },
                "count" to AttributeValue().apply { n = "0" },
                "permissionLevel" to AttributeValue().apply { s = permissionLevel.toString() }
            )

            val req = PutItemRequest()
                .withTableName(tableName)
                .withItem(item)
                .withReturnValues(ReturnValue.ALL_OLD)


            var returnValue = EncodedTokens (
                    AccessToken = null,
                    RefreshToken = null
            )

            if (client.putItem(req).sdkHttpMetadata.httpStatusCode == 200) {
                returnValue = EncodedTokens(
                    AccessToken = signAccessToken(username),
                    RefreshToken = signRefreshToken(username)
                )
            }

            setCookies(call, DecodedTokens(
                    JWT.decode(returnValue.AccessToken),
                    JWT.decode(returnValue.RefreshToken))

            )
            returnValue
        } catch (t: Throwable) {
            setCookies(call, DecodedTokens(null, null))
            EncodedTokens (
                    AccessToken = null,
                    RefreshToken = null
            )
        }
    }

    /**
     * Method verifies that tokens are correct and returns access tokens decoded.
     * If access token and refresh token are valid decoded access token is returned.
     * if access token is invalid and refresh token is valid a new access token is granted.
     * and both tokens are reset
     * If the refresh token and access token are invalid null is returns
     * cookies are set to the cookies if they are verified, null otherwise
     *
     * @param encodedTokens a map of AccessToken to the provided access token
     * and RefreshToken to the provided refresh token
     *
     * @return a decoded AccessToken if verified. null otherwise
     */
    fun verifyToken(call: ApplicationCall): DecodedJWT? {
        val encodedTokens = getCookiesOrAccessTokens(call)
        val (accessToken, refreshToken) = try {

            val accessToken = verifier.verify(JWT.decode(encodedTokens.AccessToken))
            val refreshToken = verifier.verify(JWT.decode(encodedTokens.RefreshToken))
            Pair(accessToken, refreshToken)

        } catch (e: TokenExpiredException) {
            try {
                val refreshToken = verifier.verify(encodedTokens.RefreshToken)
                val username = refreshToken.getClaim("key").asString()
                val tokenCount = refreshToken.getClaim("count").asInt()
                val tokenPermissionLevel = refreshToken.getClaim("permissionLevel").asString()
                val userInfo = getUserInfo(username)

                val accessToken = if (userInfo.count == tokenCount) {
                    verifier.verify(signAccessToken(username, tokenPermissionLevel))
                } else {
                    null
                }
                val newRefreshToken = JWT.decode(
                        signRefreshToken(
                                username,
                                count = tokenCount,
                                permissionLevel = tokenPermissionLevel)
                )
                Pair(accessToken, newRefreshToken)
            } catch (e: JWTVerificationException) {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }

        setCookies(call, DecodedTokens(AccessToken = accessToken, RefreshToken = refreshToken))
        return accessToken
    }

    /**
     *
     * adds to the count of the specified user so that if the refresh token is inspected it will not match and will not
     * validate
     *
     * @param username user to invalidate
     */
    fun invalidateRefreshToken(username: String) {
        val user = getUserInfo(username)
        val count = user.count
        val newCount = (count + 1) % Int.MAX_VALUE
        val map = mapOf(
            "count" to AttributeValueUpdate().withValue(AttributeValue().apply { n = newCount.toString() })
        )
        val req = UpdateItemRequest()
                .withConditionExpression("#username = :username")
                .withExpressionAttributeValues(mapOf(":username" to AttributeValue().apply { s =  username }))
                .withAttributeUpdates(map)
                .withTableName(tableName)
                .withKey(mapOf("username" to AttributeValue().apply { s = username}))
                .withReturnValues(ReturnValue.UPDATED_NEW)
        val res = client.updateItem(req)
        if (res.sdkHttpMetadata.httpStatusCode != 200) error("could not invalidate refresh token")
    }

    /**
     * @param call The ApplicationCall that has access to cookies
     * @return returns the cookies as strings in an EncodedTokens data class
     * EncodedTokens class will have null values if no tokens present
     */
    private fun getCookiesOrAccessTokens(call: ApplicationCall): EncodedTokens {

        val accessPayload = call.request.cookies["fnb-AccessToken-Payload"] ?: "<empty>"
        val accessSig = call.request.cookies["fnb-AccessToken-Signature"] ?: "<empty>"
        val refreshPayload = call.request.cookies["fnb-RefreshToken-Payload"] ?: "<empty>"
        val refreshSig = call.request.cookies["fnb-RefreshToken-Signature"] ?: "<empty>"

        var accessToken = "$accessPayload.$accessSig"
        var refreshToken = "$refreshPayload.$refreshSig"

        if ("<empty>" in refreshToken) { refreshToken = call.request.headers["RefreshToken"] ?: "no-refresh-token" }
        if ("<empty>" in accessToken) { accessToken = call.request.headers["AccessToken"] ?: "no-access-token" }
        MyLogger.logger?.info("yooooooo")
        return EncodedTokens(
                AccessToken = accessToken,
                RefreshToken = refreshToken
        )
    }

    /**
     * @param call The ApplicationCall that has access to cookies
     * @param decodedTokens DecodedTokens class that represents the cookies
     */
    private fun setCookies(call: ApplicationCall, decodedTokens: DecodedTokens) {
        val expDate =  GMTDate(dayOfMonth = 1,
            month = Month.JANUARY,
            year = 2022,
            seconds = 1,
            minutes = 1,
            hours = 1)
        call.response.header("Access-Control-Expose-Headers", "AccessToken, RefreshToken")
        call.response.header("AccessToken", decodedTokens.AccessToken?.token ?: "")
        call.response.header("RefreshToken", decodedTokens.RefreshToken?.token ?: "")
        if (decodedTokens.AccessToken != null) {
            call.response.cookies.append("fnb-AccessToken-Payload",
                    "${decodedTokens.AccessToken.header}.${decodedTokens.AccessToken.payload.toString()}",
                    extensions = mapOf("SameSite" to "None"), expires = expDate)
            call.response.cookies.append("fnb-AccessToken-Signature",
                    decodedTokens.AccessToken.signature, httpOnly = true,
                    extensions = mapOf("SameSite" to "None"), expires = expDate)

        } else {
            call.response.cookies.append("fnb-AccessToken-Payload",
                    "")
            call.response.cookies.append("fnb-AccessToken-Signature",
                    "", httpOnly = true)
        }

        if (decodedTokens.RefreshToken != null) {
            call.response.cookies.append("fnb-RefreshToken-Payload",
                    "${decodedTokens.RefreshToken.header}.${decodedTokens.RefreshToken.payload.toString()}",
                    extensions = mapOf("SameSite" to "None"), expires = expDate)
            call.response.cookies.append("fnb-RefreshToken-Signature",
                    decodedTokens.RefreshToken.signature, httpOnly = true,
                    extensions = mapOf("SameSite" to "None"),
            expires = expDate)
        } else {
            call.response.cookies.append("fnb-RefreshToken-Payload",
                    "")
            call.response.cookies.append("fnb-RefreshToken-Signature",
                    "", httpOnly = true)
        }
    }

    private fun signAccessToken(username: String, permissionLevel: String? = null): String {
        val date = Calendar.getInstance().apply {
            this.time = Date()
            this.add(Calendar.MINUTE, 5)
        }.time

        val actualPermissionLevel: String = permissionLevel ?:
            getUserInfo(username).permissionLevel.toString()

        return JWT.create()
            .withIssuer(username)
            .withExpiresAt(date)
            .withClaim("key", username)
            .withClaim("permissionLevel", actualPermissionLevel)
            .sign(algorithm)
    }

    private fun signRefreshToken(username: String,
                                 permissionLevel: String? = null,
                                 count: Int? = null): String {
        val date = GregorianCalendar.getInstance().apply {
            this.time = Date()
            this.add(Calendar.MINUTE, 8440)
        }.time

        val actualPermissionLevel: String = permissionLevel ?:
            getUserInfo(username).permissionLevel.toString()

        val actualCount = count ?:
            getUserInfo(username).count

        return JWT.create()
            .withIssuer(username)
            .withExpiresAt(date)
            .withClaim("key", username)
            .withClaim("count", actualCount)
            .withClaim("permissionLevel", actualPermissionLevel)
            .sign(algorithm)
    }

    /**
     * @param username unique username associated with each user
     * @return a user object that describes the requested user, and null if no such user exists
     */
    private fun getUserInfo(username: String): User {

        val req = GetItemRequest().withKey(mapOf(
                "username" to AttributeValue().apply { s = username }
        )).withTableName(tableName)

        val userMap = client.getItem(req).item ?: error("user does not exist")

        return User(
                username = userMap["username"]?.s.toString(),
                password = userMap["password"]?.s.toString(),
                count = userMap["count"]?.n.toString().toInt(),
                permissionLevel = UserPermissionLevel.valueOf(userMap["permissionLevel"]?.s.toString())
        )

    }

}