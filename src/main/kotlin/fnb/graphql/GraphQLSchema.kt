package fnb.graphql

import com.apurebase.kgraphql.KGraphQL
import fnb.services.AuthService
import fnb.services.LocationsServiceDynamo
import fnb.services.UserDataService

class FnBSchema(
    private val authService: AuthService,
    private val locationsService: LocationsServiceDynamo,
    private val userDataService: UserDataService
) {
    val schema = KGraphQL.schema {
        authSchema(authService)
        locationsSchema(locationsService, userDataService)
        userDataSchema(userDataService)
    }
}