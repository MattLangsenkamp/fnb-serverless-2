package fnb.locations
import com.apurebase.kgraphql.context
import com.apurebase.kgraphql.schema.Schema
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.google.gson.Gson
import fnb.locations.services.AuthService
import fnb.logging.MyLogger
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import io.ktor.routing.Route
import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.response.respondText
import io.ktor.routing.post
import org.koin.ktor.ext.inject
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

fun Route.graphql(log: Logger, gson: Gson, schema: Schema, authService: AuthService) {
    post("/graphql") {
        val request = call.receive<GraphQLRequest>()
        // the k-graphql schema will try and grab a Decoded Jwt. if the tokens are not present or invalid then
        // it will pick up a null object instead.
        val tokens = authService.verifyToken(call) ?: "invalid"
        MyLogger.logger = log
        MyLogger.logger?.info("hey")
        val ctx = context {
            +tokens
            +log
            +call
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