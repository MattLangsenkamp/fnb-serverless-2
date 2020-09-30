package fnb.locations

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.KGraphQL
import com.auth0.jwt.interfaces.DecodedJWT
import fnb.locations.model.*
import fnb.locations.services.AuthService
import fnb.locations.services.LocationsServiceDynamo
import fnb.locations.services.UserDataService
import io.ktor.application.*
import org.slf4j.Logger

class FnBSchema(
    private val authService: AuthService,
    private val locationsService: LocationsServiceDynamo,
    private val userDataService: UserDataService
) {
    val schema = KGraphQL.schema {

        query("getLocation") {
            resolver { id: String,
                       ctx: Context ->
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

        mutation("signIn") {
            resolver { email: String,
                       password: String,
                       ctx: Context
                ->
                val call = ctx.get<ApplicationCall>()!!
                val log = ctx.get<Logger>()!!
                val tokens = authService.signIn(call, email, password)
                val message = if ((tokens.AccessToken != null) && (tokens.RefreshToken != null)) {
                    "Successfully signed in"
                } else {
                    "Sign in failed"
                }
                log.info(message)
                AuthResponse(
                    message,
                    AccessToken = tokens.AccessToken,
                    RefreshToken = tokens.RefreshToken
                )
            }
        }

        mutation("signUp") {
            resolver { email: String,
                       username: String,
                       password: String,
                       ctx: Context
                ->
                val call = ctx.get<ApplicationCall>()!!
                val tokens = authService.signUp(call, email, username, password)
                val message = if ((tokens.AccessToken != null) && (tokens.RefreshToken != null)) {
                    "sign up successful"
                } else {
                    "Sign up failed"
                }
                AuthResponse(
                    message,
                    AccessToken = tokens.AccessToken,
                    RefreshToken = tokens.RefreshToken
                )
            }
        }

        query("getUserData") {
            resolver { id: String,
                       ctx: Context
                ->
                val log = ctx.get<Logger>()!!
                log.info("Retrieved user data for id: $id")
                val userData = userDataService.getUserData(id)
                UserDataResponse(
                    message = "Successfully retrieved user data",
                    payload = listOf(userData)
                )
            }
        }

        query("getAllUserDatas") {
            resolver { id: String,
                       ctx: Context
                ->
                val log = ctx.get<Logger>()!!
                log.info("Retrieved user data for id: $id")
                val userData = userDataService.getAllUsers()
                UserDataResponse(
                    message = "Successfully retrieved all user datas",
                    payload = userData
                )
            }
        }

        mutation("updateUserData") {
            resolver { id: String,
                       username: String,
                       contact: String,
                       description: String,
                       picture: String,
                       locations: List<String>,
                       ctx: Context
                ->
                val accessToken = ctx.get<Any>()
                val log = ctx.get<Logger>()!!

                if (accessToken != null && accessToken is DecodedJWT) {
                    val loggedInUser = accessToken.getClaim("key").asString()
                    log.info("Retrieved user data for id: $id")
                    val userData = userDataService.updateUserData(
                        id,
                        username,
                        contact,
                        description,
                        picture,
                        locations,
                        loggedInUser
                    )
                    UserDataResponse(
                        message = "Successfully retrieved all user datas",
                        payload = listOf(userData)
                    )
                } else {
                    UserDataResponse(
                        message = "Not Authorized",
                        payload = listOf()
                    ) // maybe null tokens here?
                }
            }
        }

        mutation("deleteUserData") {
            resolver { id: String,
                       ctx: Context
                ->
                val accessToken = ctx.get<Any>()
                if (accessToken != null && accessToken is DecodedJWT) {
                    val loggedInUser = accessToken.getClaim("key").asString()
                    val log = ctx.get<Logger>()!!
                    log.info("Retrieved user data for id: $id")
                    val userData = userDataService.deleteUserData(id, loggedInUser)
                    UserDataResponse(
                        message = "Successfully deleted user data",
                        payload = listOf(userData)
                    )
                } else {
                    UserDataResponse(
                        message = "Not Authorized",
                        payload = listOf()
                    ) // maybe null tokens here?
                }
            }
        }

        type<UserData>()
        type<Location>()
        enum<LocationType>()
        type<Response>()
        type<AuthResponse>()
        type<UserDataResponse>()
    }
}