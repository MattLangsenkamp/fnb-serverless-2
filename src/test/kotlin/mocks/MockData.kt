package mocks

import fnb.locations.model.Location
import fnb.locations.model.LocationType
import fnb.locations.model.User
import fnb.locations.model.UserPermissionLevel

val user = User(
    username = "Matt Lang",
    password = "fake-pass",
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
    pictureURI = "s3:/sure",
    type = LocationType.FREE_FOOD_STAND
)