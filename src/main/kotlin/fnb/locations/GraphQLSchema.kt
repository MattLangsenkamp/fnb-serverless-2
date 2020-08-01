package fnb.locations
import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.schema.Schema
import com.auth0.jwt.JWT
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
                val call = ctx.get<ApplicationCall>()!!
                val tokens = ctx.get<Map<String, String>>() ?: mapOf("AccessToken" to null, "RefreshToken" to null)
                val decodedTokens = AuthService.verifyToken(tokens)
                if ((decodedTokens["AccessToken"] != null) && (decodedTokens["RefreshToken"] != null)) {
                    val accessToken = decodedTokens["AccessToken"] ?: error("No access Token")
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
                    AuthService.setCookies(call, decodedTokens)
                    Response(message = message,
                            payload = payload)
                } else {
                    AuthService.setCookies(call, decodedTokens)
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
                val call = ctx.get<ApplicationCall>()!!
                val tokens = ctx.get<Map<String, String>>() ?: mapOf("AccessToken" to null, "RefreshToken" to null)
                val decodedTokens = AuthService.verifyToken(tokens)
                if ((decodedTokens["AccessToken"] != null) && (decodedTokens["RefreshToken"] != null)) {
                    val accessToken = decodedTokens["AccessToken"] ?: error("No access Token")

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
                    AuthService.setCookies(call, decodedTokens)
                    Response(message = message,
                            payload = payload)
                } else {
                    AuthService.setCookies(call, decodedTokens)
                    Response(message = "Not Authorized",
                            payload = listOf()) // maybe null tokens here?
                }
            }
        }

        mutation("deleteLocation") {
            resolver { id: String,
                       ctx: Context ->
                val call = ctx.get<ApplicationCall>()!!
                val log = ctx.get<Logger>()!!
                val tokens = ctx.get<Map<String, String>>() ?: mapOf("AccessToken" to null, "RefreshToken" to null)
                val decodedTokens = AuthService.verifyToken(tokens)
                if (decodedTokens["AccessToken"] != null && decodedTokens["RefreshToken"] != null) {
                    val accessToken = decodedTokens["AccessToken"] ?: error("No access Token")
                    log.info(accessToken.getClaim("key").asString())
                    val deletedLocation = LocationsServiceDynamo.deleteLocation(
                            id,
                            accessToken.getClaim("key").asString()
                    )

                    val (payload, message) = if (deletedLocation!= null) {
                        Pair(listOf(deletedLocation), "Successfully deleted location")
                    } else {
                        Pair(listOf(deletedLocation), "Failed to delete location")
                    }
                    log.info(message)
                    AuthService.setCookies(call, decodedTokens)
                    Response(message = message,
                            payload = payload)
                } else {
                    AuthService.setCookies(call, decodedTokens)
                    Response(message = "Not Authorized",
                            payload = listOf()) // maybe null tokens here?
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
                val response: AuthResponse
                val tokens = AuthService.signIn(username, password)
                response = if ((tokens["AccessToken"] != null) && (tokens["RefreshToken"] != null)) {
                    log.info("Successfully signed in")
                    AuthResponse("Successfully signed in",
                            AccessToken = tokens["AccessToken"],
                            RefreshToken = tokens["RefreshToken"])
                } else {
                    log.info("Sign in failed")
                    AuthResponse("Sign in failed",
                            AccessToken = tokens["AccessToken"],
                            RefreshToken = tokens["RefreshToken"])
                }
                //val encodedTokens = mapOf("AccessToken" to JWT.decode(tokens["AccessToken"]),
                  //                          "RefreshToken" to JWT.decode(tokens["RefreshToken"]))
                //AuthService.setCookies(call, encodedTokens)
                response
            }
        }

        mutation("signUp") {
            resolver {
                username: String,
                password:String,
                ctx: Context
                ->
                val call = ctx.get<ApplicationCall>()!!
                val response: AuthResponse
                val tokens = AuthService.signUp(username, password)
                response = if ((tokens["AccessToken"] != null) && (tokens["RefreshToken"] != null)) {
                    val encodedTokens = mapOf("AccessToken" to JWT.decode(tokens["AccessToken"]),
                            "RefreshToken" to JWT.decode(tokens["RefreshToken"]))
                    AuthService.setCookies(call, encodedTokens)
                    AuthResponse("Successfully signed up",
                            AccessToken = tokens["AccessToken"],
                            RefreshToken = tokens["RefreshToken"])
                } else {
                    AuthResponse("Sign up failed",
                            AccessToken = tokens["AccessToken"],
                            RefreshToken = tokens["RefreshToken"])
                    //AuthService.setCookies(call, mapOf("AccessToken" to null),
                            //"RefreshToken" to null))
                }

                response
            }
        }

        type<Location>()
        enum<LocationType>()
        type<Response>()
    }
}