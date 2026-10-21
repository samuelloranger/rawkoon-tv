package cloud.rawkoon.tv.jellyfin

import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/** Builds image URLs on one server. Tags make each URL change when the image does, so caches never go stale. */
class ImageUrls(private val base: String) {
    fun item(id: UUID, type: ImageType, tag: String) = "$base/Items/$id/Images/${type.serialName}?tag=$tag&quality=90"

    /** The server sends a user's picture at full size whatever size is asked (it has no resize for it); [cloud.rawkoon.tv.data.Images] downsamples on decode. */
    fun user(userId: String, tag: String) = "$base/Users/$userId/Images/Primary?tag=$tag&quality=90"
}
