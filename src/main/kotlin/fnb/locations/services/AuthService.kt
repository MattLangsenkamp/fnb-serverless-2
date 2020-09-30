package fnb.locations.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
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
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import io.ktor.application.*
import io.ktor.response.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*


private const val tableName: String = "fnb-auth"

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
class AuthService(private val client: AmazonDynamoDB, private val userDataService: UserDataService) {
    /*private val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder
            .standard()
            .withCredentials(ProfileCredentialsProvider("fnb-admin"))
            .build()*/
    private val secret: String = "FakeSecret"
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)
    private val verifier: JWTVerifier = JWT.require(algorithm).build()

    /**
     * @param email the email of the user signing in
     * @param password the password of the user signing in
     *
     * @return EncodedTokens object with no null values if sign in successful
     * EncodedTokens object with two null values if unsuccessful
     */
    fun signIn(call: ApplicationCall, email: String, password: String): EncodedTokens {
        return try {
            // search dynamo for match
            val user = getUserInfo(email = email)

            // hash incoming password and compare it to saved
            if (!BCrypt.verifyer()
                    .verify(
                        password.toByteArray(StandardCharsets.UTF_8),
                        user.password
                    ).verified
            ) {
                error("Password incorrect")
            }

            val permissionLevel = user.permissionLevel
            val count = user.count

            val accessToken = signAccessToken(user.id, permissionLevel.toString())
            val refreshToken = signRefreshToken(user.id, permissionLevel.toString(), count)

            setAccessTokens(call, DecodedTokens(JWT.decode(accessToken), JWT.decode(refreshToken)))

            EncodedTokens(
                AccessToken = accessToken,
                RefreshToken = refreshToken
            )

        } catch (t: Throwable) {
            setAccessTokens(call, DecodedTokens(null, null))
            EncodedTokens(
                AccessToken = null,
                RefreshToken = null
            )
        }
    }

    /**
     * @param email the email of the user signing up
     * @param password the password of the user signing up
     * @param permissionLevel the permission level of the user
     *
     * @return EncodedTokens object with no null values if sign in successful
     * EncodedTokens object with two null values if unsuccessful
     */
    fun signUp(
        call: ApplicationCall,
        email: String,
        username: String,
        password: String,
        permissionLevel: UserPermissionLevel = UserPermissionLevel.USER
    ): EncodedTokens {
        return try {

            val hashedPassword = BCrypt.withDefaults().hash(10, password.toByteArray(StandardCharsets.UTF_8))

            val id: UUID = UUID.randomUUID()
            val item = mapOf(
                "id" to AttributeValue().apply { s = id.toString() },
                "email" to AttributeValue().apply { s = email },
                "password" to AttributeValue().apply { b = ByteBuffer.wrap(hashedPassword) },
                "count" to AttributeValue().apply { n = "0" },
                "permissionLevel" to AttributeValue().apply { s = permissionLevel.toString() }
            )

            // check for uniqueness for email and username
            val emailReq = ScanRequest()
                .withFilterExpression("email = :email")
                .withTableName(tableName)
                .withExpressionAttributeValues(mutableMapOf(":email" to AttributeValue().apply { s = email }))
            if (client.scan(emailReq).items.count() > 0) error("email already in use")

            userDataService.addUserData(id = id.toString(), username = username)

            val req = PutItemRequest()
                .withTableName(tableName)
                .withItem(item)
                .withReturnValues(ReturnValue.ALL_OLD)

            val returnValue = if (client.putItem(req).sdkHttpMetadata.httpStatusCode == 200) {
                EncodedTokens(
                    AccessToken = signAccessToken(id.toString()),
                    RefreshToken = signRefreshToken(id.toString())
                )
            } else {
                EncodedTokens(
                    AccessToken = null,
                    RefreshToken = null
                )
            }

            setAccessTokens(
                call, DecodedTokens(
                    JWT.decode(returnValue.AccessToken),
                    JWT.decode(returnValue.RefreshToken)
                )
            )
            returnValue
        } catch (t: Throwable) {
            setAccessTokens(call, DecodedTokens(null, null))
            EncodedTokens(
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
     * @param call ktor object that has ability to set headers as needed
     *
     * @return a decoded AccessToken if verified. null otherwise
     */
    fun verifyToken(call: ApplicationCall): DecodedJWT? {
        val encodedTokens = getAccessTokens(call)
        val (accessToken, refreshToken) = try {

            val accessToken = verifier.verify(JWT.decode(encodedTokens.AccessToken))
            val refreshToken = verifier.verify(JWT.decode(encodedTokens.RefreshToken))
            Pair(accessToken, refreshToken)

        } catch (e: TokenExpiredException) {
            try {
                val refreshToken = verifier.verify(encodedTokens.RefreshToken)
                val id = refreshToken.getClaim("key").asString()
                val tokenCount = refreshToken.getClaim("count").asInt()
                val tokenPermissionLevel = refreshToken.getClaim("permissionLevel").asString()
                val userInfo = getUserInfo(id)

                val accessToken = if (userInfo.count == tokenCount) {
                    verifier.verify(signAccessToken(id, tokenPermissionLevel))
                } else {
                    null
                }
                val newRefreshToken = JWT.decode(
                    signRefreshToken(
                        id,
                        count = tokenCount,
                        permissionLevel = tokenPermissionLevel
                    )
                )
                Pair(accessToken, newRefreshToken)
            } catch (e: JWTVerificationException) {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }

        setAccessTokens(call, DecodedTokens(AccessToken = accessToken, RefreshToken = refreshToken))
        return accessToken
    }

    /**
     *
     * adds to the count of the specified user so that if the refresh token is inspected it will not match and will not
     * validate
     *
     * @param id user to invalidate
     */
    fun invalidateRefreshToken(id: String) {
        val user = getUserInfo(id)
        val count = user.count
        val newCount = (count + 1) % Int.MAX_VALUE
        val map = mapOf(
            "count" to AttributeValueUpdate().withValue(AttributeValue().apply { n = newCount.toString() })
        )
        val req = UpdateItemRequest()
            .withConditionExpression("#id = :id")
            .withExpressionAttributeValues(mapOf(":id" to AttributeValue().apply { s = id }))
            .withAttributeUpdates(map)
            .withTableName(tableName)
            .withKey(mapOf("id" to AttributeValue().apply { s = id }))
            .withReturnValues(ReturnValue.UPDATED_NEW)
        val res = client.updateItem(req)
        if (res.sdkHttpMetadata.httpStatusCode != 200) error("could not invalidate refresh token")
    }

    /**
     * @param call The ApplicationCall that has access to cookies
     * @return returns the cookies as strings in an EncodedTokens data class
     * EncodedTokens class will have null values if no tokens present
     */
    private fun getAccessTokens(call: ApplicationCall): EncodedTokens {

        val refreshToken = call.request.headers["RefreshToken"] ?: "no-refresh-token"
        val accessToken = call.request.headers["AccessToken"] ?: "no-access-token"

        return EncodedTokens(
            AccessToken = accessToken,
            RefreshToken = refreshToken
        )
    }

    /**
     * @param call The ApplicationCall that has access to cookies
     * @param decodedTokens DecodedTokens class that represents the cookies
     */
    private fun setAccessTokens(call: ApplicationCall, decodedTokens: DecodedTokens) {

        call.response.header("Access-Control-Expose-Headers", "AccessToken, RefreshToken")
        call.response.header("AccessToken", decodedTokens.AccessToken?.token ?: "")
        call.response.header("RefreshToken", decodedTokens.RefreshToken?.token ?: "")

    }

    private fun signAccessToken(id: String, permissionLevel: String? = null): String {
        val date = Calendar.getInstance().apply {
            this.time = Date()
            this.add(Calendar.MINUTE, 5)
        }.time

        val actualPermissionLevel: String = permissionLevel ?: getUserInfo(id).permissionLevel.toString()

        return JWT.create()
            .withIssuer(id)
            .withExpiresAt(date)
            .withClaim("key", id)
            .withClaim("permissionLevel", actualPermissionLevel)
            .sign(algorithm)
    }

    private fun signRefreshToken(
        id: String,
        permissionLevel: String? = null,
        count: Int? = null
    ): String {
        val date = GregorianCalendar.getInstance().apply {
            this.time = Date()
            this.add(Calendar.MINUTE, 8440)
        }.time

        val actualPermissionLevel: String = permissionLevel ?: getUserInfo(id).permissionLevel.toString()

        val actualCount = count ?: getUserInfo(id).count

        return JWT.create()
            .withIssuer(id)
            .withExpiresAt(date)
            .withClaim("key", id)
            .withClaim("count", actualCount)
            .withClaim("permissionLevel", actualPermissionLevel)
            .sign(algorithm)
    }

    /**
     * @param id unique id associated with each user
     * @param email the email of the user to get
     * @return a user object that describes the requested user, and null if no such user exists
     */
    private fun getUserInfo(id: String? = null, email: String? = null): User {


        val userMap = if (id != null) {
            val req = GetItemRequest().withKey(mapOf(
                "id" to AttributeValue().apply { s = id }
            )).withTableName(tableName)
            client.getItem(req).item ?: error("user does not exist")
        } else if (email != null) {
            val req = ScanRequest()
                .withFilterExpression("email = :email")
                .withTableName(tableName)
                .withExpressionAttributeValues(mutableMapOf(":email" to AttributeValue().apply { s = email }))
            client.scan(req).items[0]
        } else {
            error("no user identification provided")
        }

        val hashedPassword = ByteArray(userMap["password"]?.b?.remaining() ?: 0)
        userMap["password"]?.b?.get(hashedPassword)
        return User(
            id = userMap["id"]?.s.toString(),
            password = hashedPassword,
            count = userMap["count"]?.n.toString().toInt(),
            permissionLevel = UserPermissionLevel.valueOf(userMap["permissionLevel"]?.s.toString())
        )

    }

}