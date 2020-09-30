package fnb.model

data class UserData(
    val id: String,
    val username: String,
    val contact: String,
    val description: String,
    val picture: String,
    val locations: List<String>
)

data class UserDataResponse(
    val message: String,
    val payload: List<UserData>
)
