package fnb.locations
import com.apurebase.kgraphql.context
import com.apurebase.kgraphql.schema.Schema
import com.google.gson.Gson
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import io.ktor.routing.Route
import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.response.respondText
import io.ktor.routing.post
import org.slf4j.Logger

private const val tableName: String = "fnb-data"

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
data class GraphQLRequest(val query: String = "", val variables: Map<String, Any> = emptyMap())

fun GraphQLErrors.asMap(): Map<String, Map<String, String>> {
    return mapOf("errors"
            to mapOf("message"
                to "Caught ${e.javaClass.simpleName}: ${e.message?.replace("\"", "")}")
    )
}

data class GraphQLErrors(val e: Exception)

fun Route.graphql(log: Logger, gson: Gson, schema: Schema) {
    post("/graphql") {
        val request = call.receive<GraphQLRequest>()
        val accessTkn = call.request.headers["AccessToken"] ?: "no-access-token"
        val refreshTkn = call.request.headers["RefreshToken"] ?: "no-refresh-token"

        val tokens = mapOf(
                "AccessToken" to accessTkn,
                "RefreshToken" to refreshTkn
        )

        val ctx = context {
            +tokens
            +log
        }

        val query = request.query
        log.info("the graphql query: $query")

        val variables = gson.toJson(request.variables)
        log.info("the graphql variables: $variables")

        try {
            val result = schema.execute(query, variables = variables, context = ctx)
            call.respondText(result)
        } catch (e: Exception) {
            call.respondText(gson.toJson(GraphQLErrors(e).asMap()))
        }
    }
}