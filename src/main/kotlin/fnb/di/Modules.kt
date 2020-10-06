package fnb.di

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import fnb.services.AuthService
import fnb.services.ImageService
import fnb.services.LocationsService
import fnb.services.UserDataService
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.DynamoDBTable
import io.kotless.dsl.lang.SSMParameters
import org.koin.dsl.module

private const val tableName: String = "fnb-auth"

@DynamoDBTable(tableName, PermissionLevel.ReadWrite)
@SSMParameters("fnb-jwt-secret", PermissionLevel.Read)
val mainModule = module(createdAtStart = true) {
    single<AmazonDynamoDB> {
        AmazonDynamoDBClientBuilder
            .standard()
            .withCredentials(ProfileCredentialsProvider("fnb-admin"))
            .build()
    }
    single<AWSSimpleSystemsManagement> {
        AWSSimpleSystemsManagementClientBuilder
            .standard()
            .withCredentials(ProfileCredentialsProvider("fnb-admin"))
            .build();
    }
    single { ImageService() }
    single<UserDataService> { UserDataService(get<AmazonDynamoDB>(), get<ImageService>()) }
    single<LocationsService> { LocationsService(get<AmazonDynamoDB>(), get<ImageService>()) }
    single<AuthService> {
        AuthService(
            get<AmazonDynamoDB>(),
            get<AWSSimpleSystemsManagement>(),
            get<UserDataService>()
        )
    }
}