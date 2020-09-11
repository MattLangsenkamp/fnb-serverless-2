package mocks

import fnb.locations.model.EncodedTokens
import fnb.locations.services.AuthService
import io.ktor.application.ApplicationCall
import io.mockk.every
import io.mockk.mockk

fun auth_service_that_valid_data() {
    val username = user.username
    val password = user.password
    val mock = mockk<AuthService>()
    val call = mockk<ApplicationCall>()
    every { mock.signUp(call = call, username = username, password = password)
    } returns EncodedTokens("fake_access", "fake_refresh")
}

fun auth_service_that_returns_null() {
    val username = user.username
    val password = user.password
    val mock = mockk<AuthService>()
    val call = mockk<ApplicationCall>()
    every { mock.signUp(call = call, username = username, password = password)
    } returns EncodedTokens(null, null)
}