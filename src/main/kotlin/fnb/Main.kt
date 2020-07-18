package fnb
import com.google.gson.Gson
import fnb.locations.getSchema
import fnb.locations.graphql
import io.kotless.dsl.ktor.Kotless
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.gson
import io.ktor.routing.routing

class Main : Kotless() {

    override fun prepare(app: Application) {
        with(app) {
            main()
        }
    }
}

fun Application.main() {

    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }
    routing {
        graphql(log, Gson(), getSchema())
    }
}