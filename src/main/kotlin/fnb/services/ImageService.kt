package fnb.services

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.ObjectMetadata
import io.kotless.PermissionLevel
import io.kotless.dsl.lang.S3Bucket
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.random.Random

private const val bucketName = "fnb-pictures"

@S3Bucket(bucketName, PermissionLevel.ReadWrite)
class ImageService() {
    private val cloudFrontDistro = "http://d27dyo2pqh75q0.cloudfront.net/" // prob should resolve this programatically at some point
    private val s3Client = AmazonS3ClientBuilder.standard().withRegion("us-east-1")
        .withCredentials(ProfileCredentialsProvider("fnb-admin"))
        .build()

    fun uploadImage(base64EncodedImage: String, entityId: String, imageType: ImageType): String {
        val timeStamp = GregorianCalendar.getInstance().apply {
            this.time = Date()
        }.time.toString()
        val imageFileType = base64EncodedImage
            .substringAfter("data:")
            .substringBefore(";base64")
        val imageDataBytes = base64EncodedImage.substring(base64EncodedImage.indexOf(",") + 1);
        val stream = ByteArrayInputStream(Base64.getDecoder().decode(imageDataBytes.toByteArray()));
        val fileName = "$imageType/$entityId/$timeStamp"
        val meta = ObjectMetadata()
        meta.contentType = imageFileType

        s3Client.putObject(bucketName, fileName, stream, meta)
        return "$cloudFrontDistro$fileName"
    }


}

enum class ImageType {
    LOCATION, USER
}