package fnb.locations.services

import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import io.ktor.application.ApplicationCall
import io.ktor.response.header
import io.ktor.util.date.GMTDate
import io.ktor.util.date.Month
import java.util.*
import at.favre.lib.crypto.bcrypt.BCrypt
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.AttributeValueUpdate
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest
import fnb.locations.model.*
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.util.moveToByteArray
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import kotlin.random.Random


private const val tableName: String = "fnb-users"

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
class UserDataService(private val client: AmazonDynamoDB) {
    fun addUserData(
        id: String,
        username: String? = null,
        contact: String? = null,
        description: String? = null,
        picture: String? = null,
        locations: List<String>? = null
    ): UserData {
        val dynamoDB = DynamoDB(client)
        val table = dynamoDB.getTable(tableName)
        val item = Item()
            .withString("id", id)
            .withString("username", username)
            .withString("contact", contact)
            .withString("description", description)
            .withString("picture", picture)
            .withList("locations", locations)


        val res = table.putItem(item)
        val resItem = res.item.asMap()
        val locs = resItem["locations"] as List<String>

        return UserData(
            id = resItem["id"].toString(),
            username = resItem["username"].toString(),
            contact = resItem["contact"].toString(),
            description = resItem["description"].toString(),
            picture = resItem["picture"].toString(),
            locations = locs
        )
    }

    fun updateUserData(
        id: String,
        username: String? = null,
        contact: String? = null,
        description: String? = null,
        picture: String? = null,
        locations: List<String>? = null
    ): UserData {

        if (username != null) {
            val emailReq = ScanRequest()
                .withFilterExpression("username==:email")
                .withTableName(tableName)
                .withExpressionAttributeValues(mutableMapOf(":username" to AttributeValue().apply { s = username }))
            if (client.scan(emailReq).items.count() > 0) error("username already in use")
        }
        val dynamoDB = DynamoDB(client)
        val table = dynamoDB.getTable(tableName)
        val updateExpression = StringBuilder("SET")
        val nameMap = mutableMapOf<String, String>()
        val valueMap = mutableMapOf<String, Any>()

        if (username != null) {
            val str = " #u=:u,"
            updateExpression.append(str)
            nameMap["#u"] = "username"
            valueMap[":u"] = username
        }
        if (contact != null) {
            val str = " #c=:c,"
            updateExpression.append(str)
            nameMap["#c"] = "contact"
            valueMap[":c"] = contact
        }
        if (description != null) {
            val str = " #d=:d,"
            updateExpression.append(str)
            nameMap["#d"] = "description"
            valueMap[":d"] = description
        }
        if (picture != null) {
            val str = " #p=:p,"
            updateExpression.append(str)
            nameMap["#p"] = "picture"
            valueMap[":p"] = picture
        }
        if (locations != null) {
            val str = " #l=:l,"
            updateExpression.append(str)
            nameMap["#l"] = "locations"
            valueMap[":l"] = locations
        }

        val retVal = table.updateItem(
            "id",
            id,
            updateExpression.toString().dropLast(1),
            nameMap,
            valueMap
        )

        retVal.item

        return constructUserData(retVal.item.asMap())

    }

    fun deleteUserData(id: String): UserData {
        return UserData("temp", "temp", "temp", "temp", "df", listOf("fd"))
    }

    fun getUserData(id: String): UserData {
        return UserData("temp", "temp", "temp", "temp", "df", listOf("fd"))
    }

    fun getAllUsers(): List<UserData> {
        return listOf(UserData("temp", "temp", "temp", "temp", "df", listOf("fd")))
    }

    fun addLocationToUserData(id: String, locationId: String): UserData {
        val userData = getUserData(id)
        val locations = userData.locations.toMutableList()
        if (!locations.add(locationId)) error("unable to add location")
        return updateUserData(id=userData.id,
            username = userData.username,
            contact = userData.contact,
            description = userData.description,
            picture = userData.picture,
            locations = locations.toList())
    }

    private fun constructUserData(res: Map<String, Any>?): UserData {
        if (res == null) error("no such user to update")
        val id: String = res["id"] as String? ?: error("no attribute not present")
        val username = res["username"] as String? ?: error("no attribute not present")
        val contact = res["contact"] as String? ?: error("no attribute not present")
        val description = res["description"] as String? ?: error("no attribute not present")
        val picture = res["picture"] as String? ?: error("no attribute not present")
        val locations = res["locations"] as List<*>? ?: error("no attribute present")

        return UserData(id, username, contact, "temp", "df", listOf("fd"))
    }
}