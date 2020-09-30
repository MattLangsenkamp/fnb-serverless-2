package fnb.locations.services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.KeyAttribute
import com.amazonaws.services.dynamodbv2.document.PrimaryKey
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec
import com.amazonaws.services.dynamodbv2.model.*
import fnb.locations.model.Location
import fnb.locations.model.LocationType
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.mapOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.toList

private const val tableNameData: String = "fnb-data"

@DynamoDBTable(tableNameData, PermissionLevel.ReadWrite)
class LocationsServiceDynamo(private val client: AmazonDynamoDB) {
    /* val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder
        .standard()
        .withCredentials(ProfileCredentialsProvider("fnb-admin"))
        .build() */
    /**
     * Creates a new location entry in DynamoDB
     *
     * @param name name of location
     * @param friendlyLocation friendly location name (ie corner of example lane)
     * @param description description of location
     * @param latitude
     * @param longitude
     * @param picture the location of the associated picture
     * @param type the type of location
     * @return newly created location on success, null on failure
     */
    fun addLocation(
        name: String,
        friendlyLocation: String,
        description: String,
        latitude: Double,
        longitude: Double,
        picture: String,
        locationOwner: String,
        type: LocationType
    ): Location? {
        val id: UUID = UUID.randomUUID()
        val item = mapOf(
            "id" to AttributeValue().apply { s = id.toString() },
            "name" to AttributeValue().apply { s = name },
            "friendlyLocation" to AttributeValue().apply { s = friendlyLocation },
            "description" to AttributeValue().apply { s = description },
            "latitude" to AttributeValue().apply { n = latitude.toString() },
            "longitude" to AttributeValue().apply { n = longitude.toString() },
            "picture" to AttributeValue().apply { s = picture },
            "locationOwner" to AttributeValue().apply { s = locationOwner },
            "type" to AttributeValue().apply { s = type.toString() }
        )
        val req = PutItemRequest().withTableName(tableNameData).withItem(item).withReturnValues(ReturnValue.ALL_OLD)

        val res = client.putItem(req).sdkHttpMetadata.httpStatusCode

        return if (res == 200) constructLocation(item) else null
    }

    /**
     * Retrieves a location based on the id provided
     *
     * @param id String representing the id
     * @return Location Data Class representing intended location
     * or null if location with specified id does not exist
     */
    fun getLocation(id: String): Location? {
        val req = GetItemRequest().withKey(mapOf(
            "id" to AttributeValue().apply { s = id }
        )).withTableName(tableNameData)

        val res = client.getItem(req).item

        return constructLocation(res)
    }

    /**
     * Retrieves all locations
     *
     * @return A list of Location data classes
     */
    fun getAllLocations(): List<Location?> {
        val req = ScanRequest().withTableName(tableNameData)
        val res = client.scan(req).items
        val locationList: ArrayList<Location?> = ArrayList()
        for (item in res) {
            locationList.add(constructLocation(item))
        }
        return locationList.toList()
    }

    /**
     * updates the specified location, or creates a new one if the provided id is not attached to a entry
     *
     * @param location the location to update, 0 or more fields my be changed with the exception of the id
     * @return the new location object on success, null on failure
     */
    fun updateLocation(
        id: String,
        name: String? = null,
        friendlyLocation: String? = null,
        description: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        picture: String? = null,
        locationOwner: String? = null,
        type: LocationType? = null,
        actionRequester: String
    ): Location? {


        val dynamoDB = DynamoDB(client)
        val table = dynamoDB.getTable(tableNameData)
        val updateExpression = StringBuilder("SET")
        val nameMap = mutableMapOf<String, String>()
        val valueMap = mutableMapOf<String, Any>()

        if (name != null) {
            val str = " #n=:n,"
            updateExpression.append(str)
            nameMap["#n"] = "name"
            valueMap[":n"] = name
        }
        if (friendlyLocation != null) {
            val str = " #f=:f,"
            updateExpression.append(str)
            nameMap["#f"] = "friendlyLocation"
            valueMap[":f"] = friendlyLocation
        }
        if (description != null) {
            val str = " #d=:d,"
            updateExpression.append(str)
            nameMap["#d"] = "description"
            valueMap[":d"] = description
        }
        if (latitude != null) {
            val str = " #la=:la,"
            updateExpression.append(str)
            nameMap["#la"] = "latitude"
            valueMap[":la"] = latitude
        }
        if (longitude != null) {
            val str = " #lo=:lo,"
            updateExpression.append(str)
            nameMap["#lo"] = "longitude"
            valueMap[":lo"] = longitude
        }

        if (picture != null) {
            val str = " #p=:p,"
            updateExpression.append(str)
            nameMap["#p"] = "picture"
            valueMap[":p"] = picture
        }
        if (type != null) {
            val str = " #ty=:ty,"
            updateExpression.append(str)
            nameMap["#ty"] = "type"
            valueMap[":ty"] = type.toString()
        }
        if (locationOwner != null) {
            val str = " #loc=:loc,"
            updateExpression.append(str)
            nameMap["#loc"] = "locationOwner"
            valueMap[":loc"] = locationOwner
        }

        valueMap[":actReq"] = actionRequester

        val updateItemSpec = UpdateItemSpec()
            .withPrimaryKey(PrimaryKey(KeyAttribute("id", id)))
            .withUpdateExpression(updateExpression.toString().dropLast(1))
            .withConditionExpression("#loc = :actReq")
            .withNameMap(nameMap)
            .withValueMap(valueMap)
            .withReturnValues(ReturnValue.ALL_NEW);
        val retVal = table.updateItem(updateItemSpec)
        val newLocation = retVal.item.asMap()
        return Location(
            newLocation["id"].toString(),
            newLocation["name"].toString(),
            newLocation["friendlyLocation"].toString(),
            newLocation["description"].toString(),
            newLocation["latitude"].toString().toDouble(),
            newLocation["longitude"].toString().toDouble(),
            newLocation["picture"].toString(),
            newLocation["locationOwner"].toString(),
            LocationType.valueOf(newLocation["type"].toString())
        )
    }


    /**
     * Deletes a location entry from dynamo
     *
     * @param id guid identifier for entry to delete
     * @param locationOwner the id of the user that owns the location
     * @return the deleted location if successful, null otherwise
     */
    fun deleteLocation(id: String, locationOwner: String): Location? {
        val req = DeleteItemRequest()
            .withConditionExpression("locationOwner = :locationOwner")
            .withExpressionAttributeValues(mapOf(":locationOwner" to AttributeValue().apply { s = locationOwner }))
            .withKey(mapOf("id" to AttributeValue().apply { s = id }))
            .withTableName(tableNameData)
            .withReturnValues(ReturnValue.ALL_OLD)
        val res = client.deleteItem(req).attributes
        //if res is success return 1
        return constructLocation(res)
    }

    private fun constructLocation(res: Map<String, AttributeValue>?): Location? {
        var returnLocation: Location? = null
        if (res != null) {
            returnLocation = Location(
                res["id"]?.s.toString(),
                res["name"]?.s.toString(),
                res["friendlyLocation"]?.s.toString(),
                res["description"]?.s.toString(),
                res["latitude"]?.n.toString().toDouble(),
                res["longitude"]?.n.toString().toDouble(),
                res["picture"]?.s.toString(),
                res["locationOwner"]?.s.toString(),
                LocationType.valueOf(res["type"]?.s.toString())
            )
        }
        return returnLocation
    }
}