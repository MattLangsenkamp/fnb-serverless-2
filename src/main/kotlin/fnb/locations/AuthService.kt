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
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import java.util.*


private const val tableName: String = "fnb-data"

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
object AuthService {
    val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder
        .standard()
        .build()
    private const val secret: String = "FakeSecret"
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)
    const val expiration = 300
    private val verifier: JWTVerifier = JWT.require(algorithm).build()

    fun signIn(username: String, password: String): String {

        // search dynamo for match
        val req = GetItemRequest().withKey(mapOf(
            "username" to AttributeValue().apply { s = username }
        )).withTableName(tableName)

        val res = client.getItem(req).item ?: return "Invalid credentials"
        if (password != res["password"]?.s.toString()) return "Invalid credentials"

        val date = Calendar.getInstance().apply {
            this.time = Date()
            this.roll(Calendar.MINUTE, 5)
        }.time

        return JWT.create()
            .withIssuer(username)
            .withExpiresAt(date)
            .withClaim("key", res["username"]?.s.toString())
            .withClaim("permissionLevel", res["permissionLevel"]?.s.toString())
            .sign(algorithm)
    }

    fun signUp(username: String, password: String, permissionLevel: String = "USER"): String {
        val item = mapOf<String, AttributeValue>(
            "username" to AttributeValue().apply { s = username },
            "password" to AttributeValue().apply { s = password },
            "permissionLevel" to AttributeValue().apply { s = permissionLevel }
        )

        val req = PutItemRequest()
            .withTableName(tableName)
            .withItem(item)
            .withReturnValues(ReturnValue.ALL_OLD)

        val date = Calendar.getInstance().apply {
            this.time = Date()
            this.roll(Calendar.MINUTE, 5)
        }.time

        var returnValue = "Failed to sign user up"
        if (client.putItem(req).sdkHttpMetadata.httpStatusCode == 200) {
            returnValue = JWT.create()
                .withIssuer(username)
                .withExpiresAt(date)
                .withClaim("key", username)
                .withClaim("permissionLevel", permissionLevel)
                .sign(algorithm)
        }
        return returnValue
    }

    fun verifyToken(token: String): Pair<String, Boolean> {
        return try {
            Pair(verifier.verify(token).toString(), true)
        } catch (e: JWTDecodeException) {
            Pair("Invalid token", false)
        } catch (e: JWTVerificationException) {
            Pair("Authorization failed", false)
        }
    }
}