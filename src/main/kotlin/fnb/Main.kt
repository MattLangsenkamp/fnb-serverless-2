package fnb

import com.apurebase.kgraphql.GraphQL
import fnb.di.mainModule
import fnb.graphql.authSchema
import fnb.graphql.locationsSchema
import fnb.graphql.userDataSchema
import fnb.services.AuthService
import fnb.services.LocationsService
import fnb.services.UserDataService
import io.kotless.dsl.ktor.Kotless
import io.ktor.application.*
import io.ktor.features.*
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


    install(GraphQL) {
        val authService: AuthService by inject()
        val locationsService: LocationsService by inject()
        val userDataService: UserDataService by inject()

        context { call ->
            authService.verifyToken(call)?.let {
                +it
            }
            +log
            +call
        }
        playground = true

        schema {
             authSchema(authService)
             locationsSchema(locationsService, userDataService)
             userDataSchema(userDataService)
        }
    }
}