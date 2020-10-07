package fnb.graphql

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.schema.dsl.SchemaBuilder
import com.auth0.jwt.interfaces.DecodedJWT
import fnb.model.*
import fnb.services.UserDataService
import org.slf4j.Logger


fun SchemaBuilder.userDataSchema(
    userDataService: UserDataService
) {
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
        resolver { ctx: Context
            ->
            val log = ctx.get<Logger>()!!
            log.info("Getting all user Datas")
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
}