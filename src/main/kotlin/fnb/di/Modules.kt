package fnb.di

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import fnb.graphql.FnBSchema
import fnb.services.AuthService
import fnb.services.LocationsServiceDynamo
import fnb.services.UserDataService
import org.koin.dsl.module

val mainModule = module(createdAtStart = true) {
    single<AmazonDynamoDB> { AmazonDynamoDBClientBuilder
            .standard()
            .withCredentials(ProfileCredentialsProvider("fnb-admin"))
            .build() }
    single<UserDataService> { UserDataService(get()) }
    single<LocationsServiceDynamo> { LocationsServiceDynamo(get())}
    single<AuthService> { AuthService(get(), get<UserDataService>()) }
    single<FnBSchema> { FnBSchema(get<AuthService>(), get<LocationsServiceDynamo>(), get<UserDataService>()) }
}