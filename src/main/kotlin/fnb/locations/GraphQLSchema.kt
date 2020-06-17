package fnb.locations
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.schema.Schema


fun getSchema(): Schema {

    return KGraphQL.schema {

        query("getLocation") {
            resolver { id: String ->
                LocationsServiceDynamo.getLocation(id)
            }
        }

        query("getAllLocations") {
            resolver { ->
                LocationsServiceDynamo.getAllLocations()
            }
        }

        mutation("addLocation") {
            resolver { name: String,
                       friendlyLocation: String,
                       description: String,
                       latitude: Double,
                       longitude: Double,
                       pictureURI: String,
                       type: LocationType
                       ->
                LocationsServiceDynamo.addLocation(
                        name,
                        friendlyLocation,
                        description,
                        latitude,
                        longitude,
                        pictureURI,
                        type
                )
            }
        }

        mutation("updateLocation") {
            resolver { id: String,
                       name: String,
                       friendlyLocation: String,
                       description: String,
                       latitude: Double,
                       longitude: Double,
                       pictureURI: String,
                       type: LocationType ->

                //LocationsServiceDynamo.updateLocation(location) ?: "unable to update location"
                LocationsServiceDynamo.updateLocation(Location(
                    id,
                    name,
                    friendlyLocation,
                    description,
                    latitude,
                    longitude,
                    pictureURI,
                    type
                ))
            }
        }

        mutation("deleteLocation") {
            resolver { id: String ->
                LocationsServiceDynamo.deleteLocation(id)
            }
        }
        type<Location>()
        enum<LocationType>()
    }
}