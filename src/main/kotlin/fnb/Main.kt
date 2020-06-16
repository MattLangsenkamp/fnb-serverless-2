package fnb
import com.fasterxml.jackson.databind.ObjectMapper
import fnb.locations.getSchema
import io.kotless.dsl.ktor.Kotless
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.request.receiveText
import io.ktor.response.respondText
import io.ktor.routing.post
import io.ktor.routing.routing
import org.slf4j.LoggerFactory

class Main : Kotless() {
    private val logger = LoggerFactory.getLogger(Main::class.java)

    override fun prepare(app: Application) {
        fun String.asJson() = ObjectMapper().readTree(this)
        app.routing {
            //graphql()
            val schema = getSchema()
            post("graphql") {
                with(call) {
                    val rawText = receiveText()
                        .asJson()["query"]
                        .toString()
                        .replace("\\n", "")

                    val result = schema.executeBlocking(rawText)
                    respondText { result }
                }
            }
        }
    }
}
