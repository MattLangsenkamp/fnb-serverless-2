package mocks

import fnb.model.Location
import fnb.model.LocationType
import fnb.model.User
import fnb.model.UserPermissionLevel

val user = User(
    username = "Matt Lang",
    password = "fake".toByteArray(),
    count = 10,
    permissionLevel = UserPermissionLevel.USER
)

val location = Location(
    id = "0000-0000",
    name = "first food stand",
    friendlyLocation = "corner of X and Y",
    description = "A place to get and leave free food",
    locationOwner = "matt lang",
    latitude = 0.0,
    longitude = 0.0,
    picture = "s3:/sure",
    type = LocationType.FREE_FOOD_STAND
)