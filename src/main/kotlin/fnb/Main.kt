package fnb

import com.apurebase.kgraphql.Context
import com.apurebase.kgraphql.GraphQL
import fnb.di.mainModule
import org.slf4j.Logger
import fnb.graphql.authSchema
import fnb.graphql.locationsSchema
import fnb.graphql.userDataSchema
import fnb.model.Location
import fnb.model.LocationType
import fnb.services.AuthService
import fnb.services.LocationsService
import fnb.services.UserDataService
import io.kotless.dsl.ktor.Kotless
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import org.koin.core.context.startKoin
import org.koin.core.logger.PrintLogger
import org.koin.ktor.ext.inject
import io.ktor.application.log

class Main : Kotless() {

    override fun prepare(app: Application) {
        with(app) {
            main()
        }
    }
}

fun Application.main() {
    startKoin {
        PrintLogger()
        modules(mainModule)
    }
    install(DefaultHeaders)
    install(CallLogging)
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.AccessControlAllowHeaders)
        header(HttpHeaders.ContentType)
        header(HttpHeaders.AccessControlAllowOrigin)
        header("AccessToken")
        header("RefreshToken")
        allowCredentials = true
        anyHost()
    }
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }

    install(GraphQL) {
        val authService: AuthService by inject()
        val locationsService: LocationsService by inject()
        val userDataService: UserDataService by inject()

        context { call ->
            authService.verifyToken(call)?.let {
                +it
            }
            +log
        }
        playground = true
        /*schema {
            query("hello") {
                resolver { -> "World!" }
            }
        }*/

        schema {
            type<Location>()
            enum<LocationType>()
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
            // authSchema(authService)
            // locationsSchema(locationsService, userDataService)
            // userDataSchema(userDataService)
        }
    }
}