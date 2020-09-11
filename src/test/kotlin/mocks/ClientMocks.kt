import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.GetItemRequest
import io.kotless.PermissionLevel
import io.mockk.every
import io.mockk.mockk
import mocks.location
import mocks.user

fun return_client_witch_valid_data(): AmazonDynamoDB {
    val mock = mockk<AmazonDynamoDB>()

    val req = GetItemRequest().withKey(mapOf(
                "username" to AttributeValue().apply { s = user.username}
        )).withTableName("fnb-auth")

    every { mock.getItem(req).item } returns mutableMapOf(
        "user" to AttributeValue(user.username),
        "count" to AttributeValue(user.count.toString()),
        "permissionLevel" to AttributeValue(user.permissionLevel.toString()),
        "password" to AttributeValue(user.password)
        )


    return mock
}

fun return_client_witch_null(): AmazonDynamoDB {
    val mock = mockk<AmazonDynamoDB>()
    return mock
}