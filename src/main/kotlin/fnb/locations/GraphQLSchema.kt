package fnb.locations
import fnb.locations.model.*
import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.schema.Schema
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import fnb.locations.model.Location
import fnb.locations.model.LocationType
import fnb.locations.model.Response
import fnb.locations.services.AuthService
import fnb.locations.services.LocationsServiceDynamo
import io.ktor.application.ApplicationCall
import org.slf4j.Logger

fun getSchema(): Schema {
    return KGraphQL.schema {

        query("getLocation") {
            resolver { id: String,
                       ctx: Context ->
                val log = ctx.get<Logger>()!!
                val location = LocationsServiceDynamo.getLocation(id)
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
            resolver {  ctx: Context ->
                val log = ctx.get<Logger>()!!
                val locations = LocationsServiceDynamo.getAllLocations()
                val (payload, message) = if (locations.isNotEmpty()) {
                    Pair(locations, "Successfully retrieved locations")
                } else {
                    Pair(locations, "Could not fetch locations")
                }
                log.info(message)
                Response(
                        message = message,
                        payload = payload
                )
            }
        }

        mutation("addLocation") {
            resolver { name: String,
                       friendlyLocation: String,
                       description: String,
                       latitude: Double,
                       longitude: Double,
                       pictureURI: String,
                       type: LocationType,
                       ctx: Context ->
                val log = ctx.get<Logger>()!!
                val accessToken = ctx.get<DecodedJWT>()

                if (accessToken != null) {
                    val locationOwner = accessToken.getClaim("key").asString()
                    val addedLocation = LocationsServiceDynamo.addLocation(
                            name,
                            friendlyLocation,
                            description,
                            latitude,
                            longitude,
                            pictureURI,
                            locationOwner,
                            type
                    )
                    val (payload, message) = if (addedLocation != null) {
                        Pair(listOf(addedLocation), "Successfully added location")
                    } else {
                        Pair(listOf(addedLocation), "Failed to add location")
                    }
                    log.info(message)
                    Response(message = message,
                            payload = payload)
                } else {
                    Response(message = "Not Authorized",
                            payload = listOf()) // maybe null tokens here?
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
                       pictureURI: String,
                       type: LocationType,
                       ctx: Context ->

                val log = ctx.get<Logger>()!!
                val accessToken = ctx.get<DecodedJWT>()

                if (accessToken != null) {
                    val updatedLocation = LocationsServiceDynamo.updateLocation(Location(
                            id,
                            name,
                            friendlyLocation,
                            description,
                            latitude,
                            longitude,
                            pictureURI,
                            accessToken.getClaim("key").asString(),
                            type
                        )
                    )
                    val (payload, message) = if (updatedLocation!= null) {
                        Pair(listOf(updatedLocation), "Successfully updated location")
                    } else {
                        Pair(listOf(updatedLocation), "Failed to update location")
                    }
                    log.info(message)
                    Response(message = message,
                            payload = payload)
                } else {
                    Response(message = "Not Authorized",
                            payload = listOf()) // maybe null tokens here?
                }
            }
        }

        mutation("deleteLocation") {
            resolver { id: String,
                       ctx: Context ->
                val log = ctx.get<Logger>()!!
                val accessToken = ctx.get<DecodedJWT>()
                if (accessToken != null) {
                    log.info(accessToken.getClaim("key").asString())
                    val deletedLocation = LocationsServiceDynamo.deleteLocation(
                            id,
                            accessToken.getClaim("key").asString()
                    )

                    val message = if (deletedLocation!= null) {
                        "Successfully deleted location"
                    } else {
                        "Failed to delete location"
                    }
                    log.info(message)
                    Response(message = message,
                            payload = listOf(deletedLocation))
                } else {
                    Response(message = "Not Authorized",
                            payload = listOf())
                }
            }
        }

        mutation("signIn") {
            resolver {
                username: String,
                password:String,
                ctx: Context
                ->
                val call = ctx.get<ApplicationCall>()!!
                val log = ctx.get<Logger>()!!
                val tokens = AuthService.signIn(username, password)
                val message = if ((tokens.AccessToken != null) && (tokens.RefreshToken != null)) {
                    "Successfully signed in"
                } else {
                    "Sign in failed"
                }
                log.info(message)
                AuthResponse(message,
                            AccessToken = tokens.AccessToken,
                            RefreshToken = tokens.RefreshToken)
            }
        }

        mutation("signUp") {
            resolver {
                username: String,
                password:String,
                ctx: Context
                ->
                val call = ctx.get<ApplicationCall>()!!
                val tokens = AuthService.signUp(username, password)
                val message = if ((tokens.AccessToken != null) && (tokens.RefreshToken != null)) {
                    "sign up successful"
                } else {
                    "Sign up failed"
                }
                AuthResponse(message,
                            AccessToken = tokens.AccessToken,
                            RefreshToken = tokens.RefreshToken)
            }
        }

        type<Location>()
        enum<LocationType>()
        type<Response>()
    }
}