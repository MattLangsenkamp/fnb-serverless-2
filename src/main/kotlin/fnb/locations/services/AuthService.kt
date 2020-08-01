package fnb.locations.services

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.GetItemRequest
import com.amazonaws.services.dynamodbv2.model.PutItemRequest
import com.amazonaws.services.dynamodbv2.model.ReturnValue
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import io.ktor.application.ApplicationCall
import java.util.*


private const val tableName: String = "fnb-auth"

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
object AuthService {
    private val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder
            .standard()
            .withCredentials(ProfileCredentialsProvider("fnb-admin"))
            .build()
    private const val secret: String = "FakeSecret"
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)
    private val verifier: JWTVerifier = JWT.require(algorithm).build()

    /**
     * @param username the username of the user signing up
     * @param password the password of the user signing up
     *
     * @return a map of AccessToken to the encoded access token
     * and RefreshToken to the encoded refresh token
     * a map of AccessToken to null
     * and RefreshToken to null token if unsuccessful
     */
    fun signIn(username: String, password: String): Map<String, String?> {
        return try {
            // search dynamo for match
            val user = getUserInfo(username)

            if (password != user["password"]?.s.toString()) return mapOf(
                    "AccessToken" to null,
                    "RefreshToken" to null
            )

            val permissionLevel = user["permissionLevel"]
            val count = user["count"]

            val accessToken = if (permissionLevel != null) {
                signAccessToken(username, permissionLevel.s)
            } else {
                signAccessToken(username)
            }

            val refreshToken = if ((permissionLevel != null) && (count != null)) {
                signRefreshToken(username, permissionLevel.s, count.n.toInt())
            } else {
                signRefreshToken(username)
            }
            mapOf(
                    "AccessToken" to accessToken,
                    "RefreshToken" to refreshToken
            )
        } catch (t: Throwable) {
            mapOf(
                    "AccessToken" to null,
                    "RefreshToken" to null
            )
        }
    }

    /**
     * @param username the username of the user signing in
     * @param password the password of the user signing in
     *
     * @return a map of AccessToken to the encoded access token
     * and RefreshToken to the encoded refresh token if successful
     * a map of AccessToken to null
     * and RefreshToken to null token if unsuccessful
     */
    fun signUp(username: String, password: String, permissionLevel: String = "USER"): Map<String, String?> {
        val item = mapOf(
            "username" to AttributeValue().apply { s = username },
            "password" to AttributeValue().apply { s = password },
            "count" to AttributeValue().apply { n = "0" },
            "permissionLevel" to AttributeValue().apply { s = permissionLevel }
        )

        val req = PutItemRequest()
            .withTableName(tableName)
            .withItem(item)
            .withReturnValues(ReturnValue.ALL_OLD)


        var returnValue: Map<String, String?> = mapOf(
            "AccessToken" to null,
            "RefreshToken" to null
        )

        if (client.putItem(req).sdkHttpMetadata.httpStatusCode == 200) {
            returnValue = mapOf(
                "AccessToken" to signAccessToken(username),
                "RefreshToken" to signRefreshToken(username)
            )
        }
        return returnValue
    }

    /**
     * Method verifies that access token is correct and returns both tokens decoded.
     * If access token is invalid the refresh token is checked.
     * if the refresh token is valid a new access token is granted.
     * If the refresh token and access token are invalid null values are returns
     *
     * @param tokens a map of AccessToken to the provided access token
     * and RefreshToken to the provided refresh token
     *
     * @return a map of AccessToken to the decoded access token in JsonNode form
     * and RefreshToken to the decoded refresh token in JsonNode form
     * a map of AccessToken to null
     * and RefreshToken to null token if unsuccessful
     */
    fun verifyToken(tokens: Map<String, String?>): Map<String, DecodedJWT?> {
        return try {
            mapOf(
                "AccessToken" to verifier.verify(JWT.decode(tokens["AccessToken"])),
                "RefreshToken" to verifier.verify(JWT.decode(tokens["RefreshToken"]))
            )
        } catch (e: TokenExpiredException) {
            try {
                val refreshTkn = verifier.verify(tokens["RefreshToken"])
                val username = refreshTkn.getClaim("key").asString()
                val tokenCount = refreshTkn.getClaim("count").asInt()
                val tokenPermissionLevel = refreshTkn.getClaim("permissionLevel").asString()
                val userInfo = getUserInfo(username)

                val returnTokens: Map<String, DecodedJWT?> = if (userInfo["count"]?.n.toString().toInt() == tokenCount) {
                    mapOf(
                        "AccessToken" to verifier.verify(
                                signAccessToken(username, tokenPermissionLevel)
                        ),
                        "RefreshToken" to refreshTkn
                    )
                } else {
                    mapOf(
                        "AccessToken" to null,
                        "RefreshToken" to null
                    )
                }

                returnTokens
            } catch (e: JWTVerificationException) {
                mapOf( "AccessToken" to null,
                       "RefreshToken" to null
                )
            }
        } catch (e: Exception) {
            mapOf( "AccessToken" to null,
                   "RefreshToken" to null
            )
        }
    }

    fun setCookies(call: ApplicationCall, tokens: Map<String, DecodedJWT?>) {
        call.response.cookies.append("fnb-AccessToken-Payload",
                tokens["AccessToken"]?.header + '.' + tokens["AccessToken"]?.payload.toString())
        call.response.cookies.append("fnb-AccessToken-Signature",
                tokens["AccessToken"]?.signature.toString(), httpOnly = true)

        call.response.cookies.append("fnb-RefreshToken-Payload",
                tokens["RefreshToken"]?.header + '.' + tokens["RefreshToken"]?.payload.toString())
        call.response.cookies.append("fnb-RefreshToken-Signature",
                tokens["RefreshToken"]?.signature.toString(), httpOnly = true)
    }

    fun invalidateRefreshToken(username: String): String {
        val user = getUserInfo(username)
        val count = user["count"]?.n.toString().toInt()
        val newCount = (count + 1) % Int.MAX_VALUE
        return ""//TODO
    }

    fun getCookiesOrAccessTokens(call: ApplicationCall):Map<String, String> {

        val accessPayload = call.request.cookies["fnb-AccessToken-Payload"] ?: "<empty>"
        val accessSig = call.request.cookies["fnb-AccessToken-Signature"] ?: "<empty>"
        val refreshPayload = call.request.cookies["fnb-RefreshToken-Payload"] ?: "<empty>"
        val refreshSig = call.request.cookies["fnb-RefreshToken-Signature"] ?: "<empty>"

        var accessToken = "$accessPayload.$accessSig"
        var refreshToken = "$refreshPayload.$refreshSig"

        if ("<empty>" in refreshToken) { refreshToken = call.request.headers["RefreshToken"] ?: "no-refresh-token" }
        if ("<empty>" in accessToken) { accessToken = call.request.headers["AccessToken"] ?: "no-access-token" }

        return mapOf("AccessToken" to accessToken,
                   "RefreshToken" to refreshToken)
    }

    /**
     * @param tokens a map of AccessToken to the decoded token as json
     * and Refresh token to the decoded token as json
     *
     * @return a map of AccessToken to the encoded token as a string
     * and Refresh token to the encoded token as string
     */
    fun reEncodeTokens(tokens: Map<String, DecodedJWT?>): Map<String, String> {
        val refreshToken = tokens["RefreshToken"] ?: error("No access token provided")
        val accessToken = tokens["AccessToken"] ?: error("No refresh token provided")
        return mapOf(
                "RefreshToken" to signRefreshToken(
                        refreshToken.getClaim("key").asString(),
                        refreshToken.getClaim("permissionLevel").asString(),
                        refreshToken.getClaim("count").asInt()
                ),
                "AccessToken" to signAccessToken(
                        accessToken.getClaim("key").asString(),
                        accessToken.getClaim("permissionLevel").asString()
                )
        )
    }

    private fun signAccessToken(username: String, permissionLevel: String? = null): String {
        val date = Calendar.getInstance().apply {
            this.time = Date()
            this.add(Calendar.MINUTE, 5)
        }.time
        val actualPermissionLevel: String = permissionLevel ?:
            getUserInfo(username)["PermissionLevel"]?.s.toString()

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
            getUserInfo(username)["permissionLevel"]?.s.toString()

        val actualCount: Int = count ?:
            getUserInfo(username)["count"]?.n.toString().toInt()

        return JWT.create()
            .withIssuer(username)
            .withExpiresAt(date)
            .withClaim("key", username)
            .withClaim("count", actualCount)
            .withClaim("permissionLevel", actualPermissionLevel)
            .sign(algorithm)
    }

    private fun getUserInfo(username: String): Map<String, AttributeValue> {
        val req = GetItemRequest().withKey(mapOf(
            "username" to AttributeValue().apply { s = username }
        )).withTableName(tableName)

        return LocationsServiceDynamo.client.getItem(req).item ?: error("user does not exist")
    }

}