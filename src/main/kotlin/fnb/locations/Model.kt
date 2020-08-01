package fnb.locations


data class Location(val id: String,
                    val name: String,
                    val friendlyLocation: String,
                    val description: String,
                    val latitude: Double,
                    val longitude: Double,
                    val pictureURI: String,
                    val locationOwner: String,
                    val type: LocationType
)

enum class LocationType {
    FREE_FOOD_STAND,
    FREE_STORE,
    GARDEN,
    SHELTER,
    OTHER
}

data class Response(val message: String,
                    val payload: List<Location?>
)

data class AuthResponse(val message: String,
                    val AccessToken: String?,
                    val RefreshToken: String?
)