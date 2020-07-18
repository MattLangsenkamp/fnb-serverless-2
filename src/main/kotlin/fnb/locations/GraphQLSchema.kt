package fnb.locations
import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.KGraphQL
import com.apurebase.kgraphql.schema.Schema
import org.slf4j.Logger

fun getSchema(): Schema {

    return KGraphQL.schema {

        query("getLocation") {
            resolver { id: String,
                       ctx: Context ->
                val log = ctx.get<Logger>()
                val tokens = ctx.get<Map<String, String>>() ?: mapOf("AccessToken" to null, "RefreshToken" to null)
                val location = LocationsServiceDynamo.getLocation(id)
                val message = if (location != null) {
                    "Successfully fetched location with id: $id"
                } else {
                    "Unable to fetch location with id: $id"
                }
                Response(
                        message = message,
                        payload = listOf(location),
                        AccessToken = tokens["AccessToken"],
                        RefreshToken = tokens["RefreshToken"]
                )
            }
        }

        query("getAllLocations") {
            resolver {  ctx: Context ->
                val log = ctx.get<Logger>()
                val tokens = ctx.get<Map<String, String>>() ?: mapOf("AccessToken" to null, "RefreshToken" to null)
                val locations = LocationsServiceDynamo.getAllLocations()
                val (payload, message) = if (locations.isNotEmpty()) {
                    Pair(locations, "Successfully retrieved locations")
                } else {
                    Pair(locations, "Could not fetch locations")
                }
                Response(
                        message = message,
                        payload = payload,
                        AccessToken = tokens["AccessToken"],
                        RefreshToken = tokens["RefreshToken"]
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
                val log = ctx.get<Logger>()
                val tokens = ctx.get<Map<String, String>>() ?: mapOf("AccessToken" to null, "RefreshToken" to null)
                val decodedTokens = AuthService.verifyToken(tokens)
                if ((decodedTokens["AccessToken"] != null) && (decodedTokens["RefreshToken"] != null)) {
                    val accessToken = decodedTokens["AccessToken"] ?: error("No access Token")
                    val owner = accessToken.getClaim("key").asString()
                    val addedLocation = LocationsServiceDynamo.addLocation(
                            name,
                            friendlyLocation,
                            description,
                            latitude,
                            longitude,
                            pictureURI,
                            owner,
                            type
                    )
                    val (payload, message) = if (addedLocation != null) {
                        Pair(listOf(addedLocation), "Successfully added location")
                    } else {
                        Pair(listOf(addedLocation), "Failed to add location")
                    }
                    val recodedTokens = AuthService.reEncodeTokens(decodedTokens)
                    Response(message = message,
                            payload = payload,
                            AccessToken = recodedTokens["AccessToken"],
                            RefreshToken = recodedTokens["RefreshToken"])
                } else {
                    val recodedTokens = AuthService.reEncodeTokens(decodedTokens)
                    Response(message = "Not Authorized",
                            payload = listOf(),
                            AccessToken = recodedTokens["AccessToken"],
                            RefreshToken = recodedTokens["RefreshToken"]) // maybe null tokens here?
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

                val log = ctx.get<Logger>()
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
                    val recodedTokens = AuthService.reEncodeTokens(decodedTokens)
                    Response(message = message,
                            payload = payload,
                            AccessToken = recodedTokens["AccessToken"],
                            RefreshToken = recodedTokens["RefreshToken"])
                } else {
                    //val recodedTokens = AuthService.reEncodeTokens(decodedTokens)
                    Response(message = "Not Authorized",
                            payload = listOf(),
                            AccessToken = null,
                            RefreshToken = null) // maybe null tokens here?
                }
            }
        }

        mutation("deleteLocation") {
            resolver { id: String,
                       ctx: Context ->
                val log = ctx.get<Logger>()
                val tokens = ctx.get<Map<String, String>>() ?: mapOf("AccessToken" to null, "RefreshToken" to null)
                val decodedTokens = AuthService.verifyToken(tokens)
                if (decodedTokens["AccessToken"] != null && decodedTokens["RefreshToken"] != null) {
                    val accessToken = decodedTokens["AccessToken"] ?: error("No access Token")
                    val deletedLocation = LocationsServiceDynamo.deleteLocation(
                            id,
                            accessToken.getClaim("key").asString()
                    )

                    val (payload, message) = if (deletedLocation!= null) {
                        Pair(listOf(deletedLocation), "Successfully deleted location")
                    } else {
                        Pair(listOf(deletedLocation), "Failed to delete location")
                    }
                    val recodedTokens = AuthService.reEncodeTokens(decodedTokens)
                    Response(message = message,
                            payload = payload,
                            AccessToken = recodedTokens["AccessToken"],
                            RefreshToken = recodedTokens["RefreshToken"])
                } else {
                    val recodedTokens = AuthService.reEncodeTokens(decodedTokens)
                    Response(message = "Not Authorized",
                            payload = listOf(),
                            AccessToken = recodedTokens["AccessToken"],
                            RefreshToken = recodedTokens["RefreshToken"]) // maybe null tokens here?
                }
            }
        }

        mutation("signIn") {
            resolver {
                username: String,
                password:String
                ->
                val response: Response
                val tokens = AuthService.signIn(username, password)
                response = if ((tokens["AccessToken"] != null) && (tokens["RefreshToken"] != null)) {
                    Response("Successfully signed in",
                            payload = listOf(),
                            AccessToken = tokens["AccessToken"],
                            RefreshToken = tokens["RefreshToken"])
                } else {
                    Response("Sign in failed",
                            payload = listOf(),
                            AccessToken = tokens["AccessToken"],
                            RefreshToken = tokens["RefreshToken"])
                }
                response
            }
        }

        mutation("signUp") {
            resolver {
                username: String,
                password:String
                ->
                val response: Response
                val tokens = AuthService.signUp(username, password)
                response = if ((tokens["AccessToken"] != null) && (tokens["RefreshToken"] != null)) {
                    Response("Successfully signed up",
                            payload = listOf(),
                            AccessToken = tokens["AccessToken"],
                            RefreshToken = tokens["RefreshToken"])
                } else {
                    Response("Sign up failed",
                            payload = listOf(),
                            AccessToken = tokens["AccessToken"],
                            RefreshToken = tokens["RefreshToken"])
                }
                response
            }
        }

        type<Location>()
        enum<LocationType>()
        type<Response>()
    }
}