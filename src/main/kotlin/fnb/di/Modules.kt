package fnb.di

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import fnb.locations.FnBSchema
import fnb.locations.services.AuthService
import fnb.locations.services.LocationsServiceDynamo
import org.koin.dsl.module

val mainModule = module(createdAtStart = true) {
    single<AmazonDynamoDB> { AmazonDynamoDBClientBuilder
            .standard()
            .withCredentials(ProfileCredentialsProvider("fnb-admin"))
            .build() }
    single<LocationsServiceDynamo> { LocationsServiceDynamo(get())}
    single<AuthService> { AuthService(get()) }
    single<FnBSchema> { FnBSchema(get<AuthService>(), get<LocationsServiceDynamo>()) }
}