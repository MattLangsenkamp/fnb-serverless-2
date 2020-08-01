package fnb.locations.services
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import io.kotless.dsl.lang.DynamoDBTable
import com.amazonaws.services.dynamodbv2.model.*
import fnb.locations.Location
import fnb.locations.LocationType
import io.kotless.PermissionLevel
import java.util.UUID

private const val tableNameData: String = "fnb-data"

@DynamoDBTable(tableNameData, PermissionLevel.ReadWrite)
object LocationsServiceDynamo {
    val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder
        .standard()
        .withCredentials(ProfileCredentialsProvider("fnb-admin"))
        .build()
    /**
     * Creates a new location entry in DynamoDB
     *
     * @param name name of location
     * @param friendlyLocation friendly location name (ie corner of example lane)
     * @param description description of location
     * @param latitude
     * @param longitude
     * @param pictureURI the location of the associated picture
     * @param type the type of location
     * @return newly created location on success, null on failure
     */
    fun addLocation(name: String,
                    friendlyLocation: String,
                    description: String,
                    latitude: Double,
                    longitude: Double,
                    pictureURI: String,
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
            "pictureURI" to AttributeValue().apply { s = pictureURI },
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
    fun getAllLocations():List<Location?> {
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
    fun updateLocation(location: Location): Location? {
        val map = mapOf(
            "name" to AttributeValueUpdate().withValue(AttributeValue().apply { s = location.name }),
            "friendlyLocation" to AttributeValueUpdate().withValue(AttributeValue().apply { s = location.friendlyLocation }),
            "description" to AttributeValueUpdate().withValue(AttributeValue().apply { s = location.description }),
            "latitude" to AttributeValueUpdate().withValue(AttributeValue().apply { n = location.latitude.toString() }),
            "longitude" to AttributeValueUpdate().withValue(AttributeValue().apply { n = location.longitude.toString() }),
            "pictureURI" to AttributeValueUpdate().withValue(AttributeValue().apply { s = location.pictureURI }),
            "type" to AttributeValueUpdate().withValue(AttributeValue().apply { s = location.type.toString() })
        )
        val req = UpdateItemRequest()
                .withConditionExpression("#locationOwner = :locationOwner")
                .withExpressionAttributeValues(mapOf(":locationOwner" to AttributeValue().apply { s =  location.locationOwner }))
                .withAttributeUpdates(map)
                .withTableName(tableNameData)
                .withKey(mapOf("id" to AttributeValue().apply { s = location.id }))
                .withReturnValues(ReturnValue.UPDATED_NEW)
        val res = client.updateItem(req)

        if (res.sdkHttpMetadata.httpStatusCode != 200) error("not authorized")

        return constructLocation(res.attributes)
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
                .withExpressionAttributeValues(mapOf(":locationOwner" to AttributeValue().apply { s =  locationOwner }))
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
                    res["pictureURI"]?.s.toString(),
                    res["locationOwner"]?.s.toString(),
                    LocationType.valueOf(res["type"]?.s.toString())
            )
        }
        return returnLocation
    }
}