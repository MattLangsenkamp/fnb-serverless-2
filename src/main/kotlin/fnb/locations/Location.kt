package fnb.locations
import org.joda.time.DateTime

data class Location(val id: String,
                    val name: String,
                    val friendlyLocation: String,
                    val description: String,
                    val latitude: Double,
                    val longitude: Double,
                    val pictureURI: String,
                    val type: LocationType
    )

enum class LocationType {
    FREE_FOOD_STAND,
    FREE_STORE,
    GARDEN,
    SHELTER,
    OTHER
}