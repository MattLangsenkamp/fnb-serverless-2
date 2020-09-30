package fnb.locations.services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.document.KeyAttribute
import com.amazonaws.services.dynamodbv2.document.PrimaryKey
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec
import com.amazonaws.services.dynamodbv2.model.*
import fnb.locations.model.UserData
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import java.util.*


private const val tableName: String = "fnb-user-data"

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
            .withString("username", username ?: "")
            .withString("contact", contact ?: "")
            .withString("description", description ?: "")
            .withString("picture", picture ?: "")
            .withList("locations", locations ?: listOf(""))

        val res = table.putItem(item)

        return UserData(
            id = id,
            username = username ?: "",
            contact = contact ?: "",
            description = description ?: "",
            picture = picture ?: "",
            locations = locations ?: listOf("")
        )
    }

    fun updateUserData(
        id: String,
        username: String? = null,
        contact: String? = null,
        description: String? = null,
        picture: String? = null,
        locations: List<String>? = null,
        actionRequester: String
    ): UserData {
        if (id != actionRequester) error("User not permitted to update userdata")
        if (username != null) {
            val emailReq = ScanRequest()
                .withFilterExpression("username = :username and id <> :id")
                .withTableName(tableName)
                .withExpressionAttributeValues(
                    mutableMapOf(
                        ":username" to AttributeValue().apply { s = username },
                        ":id" to AttributeValue().apply { s = id }
                    )
                )
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
        val updateItemSpec = UpdateItemSpec()
            .withPrimaryKey(PrimaryKey(KeyAttribute("id", id)))
            .withUpdateExpression(updateExpression.toString().dropLast(1))
            .withNameMap(nameMap)
            .withValueMap(valueMap)
            .withReturnValues(ReturnValue.ALL_NEW);
        val retVal = table.updateItem(updateItemSpec)

        return constructUserData(retVal.item.asMap())
    }

    fun deleteUserData(id: String, actionRequester: String): UserData {
        if (id != actionRequester) error("User not permitted to delete userdata")
        val req = DeleteItemRequest()
            .withKey(mapOf("id" to AttributeValue().apply { s = id }))
            .withTableName(tableName)
            .withReturnValues(ReturnValue.ALL_OLD)
        val res = client.deleteItem(req).attributes
        //if res is success return 1
        return constructUserData(res)
    }

    fun getUserData(id: String): UserData {
        val req = GetItemRequest().withKey(mapOf(
            "id" to AttributeValue().apply { s = id }
        )).withTableName(tableName)

        val res = client.getItem(req).item
        return constructUserDataAttributes(res)
    }

    fun getAllUsers(): List<UserData> {
        val req = ScanRequest().withTableName(tableName)
        val res = client.scan(req).items
        val userDataList: ArrayList<UserData> = ArrayList()
        for (item in res) {
            userDataList.add(constructUserData(item))
        }
        return userDataList.toList()
    }

    fun addLocationToUserData(id: String, locationId: String, actionRequester: String): UserData {
        val userData = getUserData(id)
        val locations = userData.locations.toMutableList()
        if (!locations.add(locationId)) error("unable to add location")
        return updateUserData(
            id = userData.id,
            username = userData.username,
            contact = userData.contact,
            description = userData.description,
            picture = userData.picture,
            locations = locations.toList(),
            actionRequester = actionRequester
        )
    }

    private fun constructUserData(res: Map<String, Any>?): UserData {
        if (res == null) error("no such user to update")
        val id: String = res["id"] as String? ?: error("no attribute not present")
        val username = res["username"] as String? ?: error("no attribute not present")
        val contact = res["contact"] as String? ?: error("no attribute not present")
        val description = res["description"] as String? ?: error("no attribute not present")
        val picture = res["picture"] as String? ?: error("no attribute not present")
        val locations = res["locations"] as List<*>? ?: error("no attribute present")

        return UserData(
            id = id,
            username = username,
            contact = contact,
            description = description,
            picture = picture,
            locations = locations.filterIsInstance<String>()
        )
    }

    private fun constructUserDataAttributes(res: Map<String, AttributeValue>?): UserData {
        if (res == null) error("no such user to update")
        val id: String = res["id"]?.s.toString() as String? ?: error("no attribute not present")
        val username = res["username"]?.s.toString() as String? ?: error("no attribute not present")
        val contact = res["contact"]?.s.toString() as String? ?: error("no attribute not present")
        val description = res["description"]?.s.toString() as String? ?: error("no attribute not present")
        val picture = res["picture"]?.s.toString() as String? ?: error("no attribute not present")
        val locations = res["locations"]?.l as List<*>? ?: error("no attribute present")
        val attrLocations = locations.filterIsInstance<AttributeValue>()
        val stringLocations = attrLocations.map { it.s }

        return UserData(
            id = id,
            username = username,
            contact = contact,
            description = description,
            picture = picture,
            locations = stringLocations
        )
    }
}