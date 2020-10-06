package fnb.graphql

import com.apurebase.kgraphql.KGraphQL
import fnb.services.AuthService
import fnb.services.LocationsService
import fnb.services.UserDataService

class FnBSchema(
    private val authService: AuthService,
    private val locationsService: LocationsService,
    private val userDataService: UserDataService
) {
    val schema = KGraphQL.schema {
        authSchema(authService)
        locationsSchema(locationsService, userDataService)
        userDataSchema(userDataService)
    }
}