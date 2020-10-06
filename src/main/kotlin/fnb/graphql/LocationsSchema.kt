package fnb.graphql

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.auth0.jwt.interfaces.DecodedJWT
import fnb.model.*
import fnb.services.LocationsService
import fnb.services.UserDataService
import org.slf4j.Logger


fun SchemaBuilder.locationsSchema(
    locationsService: LocationsService,
    userDataService: UserDataService
) {
    query("getLocation") {
        resolver { id: String,
                   ctx: Context
            ->
            val log = ctx.get<Logger>()!!
            val location = locationsService.getLocation(id)
            val message = if (location != null) {
                "Successfully fetched location with id: $id"
            } else {
                "Unable to fetch location with id: $id"
            }
            log.info(message)
            Response(
                message = message,
                payload = listOf(location)
            )
        }
    }

    query("getAllLocations") {
        resolver { ctx: Context ->
            val log = ctx.get<Logger>()!!
            val locations = locationsService.getAllLocations()
            val (payload, message) = if (locations.isNotEmpty()) {
                Pair(locations, "Successfully retrieved locations")
            } else {
                Pair(locations, "Could not fetch locations")
            }
            log.info(message)
            locations
        }
    }

    mutation("addLocation") {
        resolver { name: String,
                   friendlyLocation: String,
                   description: String,
                   latitude: Double,
                   longitude: Double,
                   picture: String,
                   type: LocationType,
                   ctx: Context ->
            val log = ctx.get<Logger>()!!
            val accessToken = ctx.get<Any>()

            if (accessToken != null && accessToken is DecodedJWT) {
                val locationOwner = accessToken.getClaim("key").asString()
                val addedLocation = locationsService.addLocation(
                    name,
                    friendlyLocation,
                    description,
                    latitude,
                    longitude,
                    picture,
                    locationOwner,
                    type
                ) ?: error("i need to refactor this")
                userDataService.addLocationToUserData(locationOwner, addedLocation.id, locationOwner)
                val (payload, message) = Pair(listOf(addedLocation), "Successfully added location")

                log.info(message)
                Response(
                    message = message,
                    payload = payload
                )
            } else {
                Response(
                    message = "Not Authorized",
                    payload = listOf()
                )
            }
        }
    }

    mutation("updateLocation") {
        resolver { id: String,
                   name: String,
                   friendlyLocation: String,
                   description: String,
                   latitude: Double,
                   longitude: Double,
                   picture: String,
                   locationOwner: String,
                   ctx: Context ->

            val log = ctx.get<Logger>()!!
            val accessToken = ctx.get<Any>()

            if (accessToken != null && accessToken is DecodedJWT) {
                val updatedLocation = locationsService.updateLocation(
                    id,
                    name,
                    friendlyLocation,
                    description,
                    latitude,
                    longitude,
                    picture,
                    locationOwner,
                    LocationType.FREE_FOOD_STAND,
                    accessToken.getClaim("key").asString()
                )
                val (payload, message) = if (updatedLocation != null) {
                    Pair(listOf(updatedLocation), "Successfully updated location")
                } else {
                    Pair(listOf(updatedLocation), "Failed to update location")
                }
                log.info(message)
                Response(
                    message = message,
                    payload = payload
                )
            } else {
                Response(
                    message = "Not Authorized",
                    payload = listOf()
                ) // maybe null tokens here?
            }
        }
    }

    mutation("deleteLocation") {
        resolver { id: String,
                   ctx: Context ->
            val log = ctx.get<Logger>()!!
            val accessToken = ctx.get<Any>()
            if (accessToken != null && accessToken is DecodedJWT) {
                log.info(accessToken.getClaim("key").asString())
                val deletedLocation = locationsService.deleteLocation(
                    id,
                    accessToken.getClaim("key").asString()
                )

                val message = if (deletedLocation != null) {
                    "Successfully deleted location"
                } else {
                    "Failed to delete location"
                }
                log.info(message)
                Response(
                    message = message,
                    payload = listOf(deletedLocation)
                )
            } else {
                Response(
                    message = "Not Authorized",
                    payload = listOf()
                )
            }
        }
    }
    type<Location>()
}