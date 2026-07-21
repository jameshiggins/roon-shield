package com.roonshield.roon.services

import com.roonshield.roon.RoonExtension
import com.roonshield.roon.moo.Moo
import com.roonshield.roon.moo.requestComplete
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Roon Image service (com.roonlabs.image:1). Returns raw JPEG/PNG bytes for a given image_key. */
class RoonImage(private val moo: Moo) {

    suspend fun getImage(
        imageKey: String,
        width: Int,
        height: Int,
        scale: String = "fit",
        format: String = "image/jpeg",
    ): ByteArray? {
        val body = buildJsonObject {
            put("image_key", imageKey)
            put("scale", scale)
            put("width", width)
            put("height", height)
            put("format", format)
        }.toString().toByteArray()
        val msg = moo.requestComplete("${RoonExtension.SVC_IMAGE}/get_image", body)
        return msg.body
    }
}
