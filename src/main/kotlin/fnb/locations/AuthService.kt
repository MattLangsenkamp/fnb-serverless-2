package fnb.locations

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
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import java.util.*


private const val tableName: String = "fnb-data"

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
object AuthService {
    private val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder
        .standard()
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
     */
    fun signIn(username: String, password: String): Map<String, String?> {

        // search dynamo for match
        val req = GetItemRequest().withKey(mapOf(
            "username" to AttributeValue().apply { s = username }
        )).withTableName(tableName)

        val res = client.getItem(req).item ?: return mapOf(
            "AccessToken" to null,
            "RefreshToken" to null
        )
        if (password != res["password"]?.s.toString()) return mapOf(
            "AccessToken" to null,
            "RefreshToken" to null
        )

        return  mapOf(
            "AccessToken" to this.signAccessToken(username),
            "RefreshToken" to this.signRefreshToken(username)
        )
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
                "AccessToken" to this.signAccessToken(username),
                "RefreshToken" to this.signRefreshToken(username)
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
     * @return a map of AccessToken to the decoded access token
     * and RefreshToken to the decoded refresh token
     */
    fun verifyToken(tokens: Map<String, String>): Map<String, String?> {
        return try {
            mapOf(
                "AccessToken" to verifier.verify(tokens["AccessToken"]).token,
                "RefreshToken" to verifier.verify(tokens["RefreshToken"]).token
            )
        } catch (e: TokenExpiredException) {
            try {
                val refreshTkn = verifier.verify(tokens["RefreshToken"]).token
                val username = "asdf" // TODO: get username from token
                val tokenCount = 0 //TODO
                val tokenPermissionLevel = "fasdf"
                val userInfo = this.getUserInfo(username)

                val returnTokens: Map<String, String?> = if (userInfo["count"]?.n.toString().toInt() == tokenCount) {
                    mapOf(
                        "AccessToken" to verifier.verify(this.signAccessToken(username, tokenPermissionLevel)).token,
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
        }
        catch (e: JWTVerificationException) {
            mapOf( "AccessToken" to null,
                   "RefreshToken" to null
            )
        }
    }

    fun invalidateRefreshToken(username: String): String {
        return ""//TODO
    }

    private fun signAccessToken(username: String, permissionLevel: String? = null): String {
        val date = Calendar.getInstance().apply {
            this.time = Date()
            this.roll(Calendar.MINUTE, 5)
        }.time
        val actualPermissionLevel: String = permissionLevel ?:
            this.getUserInfo(username)["PermissionLevel"]?.s.toString()

        return JWT.create()
            .withIssuer(username)
            .withIssuedAt(date)
            .withClaim("key", username)
            .withClaim("permissionLevel", actualPermissionLevel)
            .sign(algorithm)
    }

    private fun signRefreshToken(username: String,
                                 permissionLevel: String? = null,
                                 count: Int? = null): String {
        val date = Calendar.getInstance().apply {
            this.time = Date()
            this.roll(Calendar.MINUTE, 2880) // two days
        }.time

        val actualPermissionLevel: String = permissionLevel ?:
            this.getUserInfo(username)["permissionLevel"]?.s.toString()

        val actualCount: Int = count ?:
            this.getUserInfo(username)["count"]?.n.toString().toInt()
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

        return LocationsServiceDynamo.client.getItem(req).item
    }
}