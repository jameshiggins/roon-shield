package com.roonshield.roon

/** Extension identity + Roon service names. Service versions are pinned exactly — a wrong
 *  version is a silent registration failure. Transport is v2; browse/image/status/ping are v1. */
object RoonExtension {
    const val EXTENSION_ID = "com.roonshield.remote"
    const val DISPLAY_NAME = "Roon Shield"
    const val DISPLAY_VERSION = "0.1.0"
    const val PUBLISHER = "Roon Shield"
    const val EMAIL = "roonshield@example.com"

    // Consumed (required) services
    const val SVC_TRANSPORT = "com.roonlabs.transport:2"
    const val SVC_BROWSE = "com.roonlabs.browse:1"
    const val SVC_IMAGE = "com.roonlabs.image:1"

    // Provided services
    const val SVC_PING = "com.roonlabs.ping:1"
    const val SVC_STATUS = "com.roonlabs.status:1"

    // Registry + discovery
    const val SVC_REGISTRY = "com.roonlabs.registry:1"
    const val SOOD_SERVICE_ID = "00720724-5143-4a9b-abac-0e50cba674bb"

    val REQUIRED_SERVICES = listOf(SVC_TRANSPORT, SVC_BROWSE, SVC_IMAGE)
    val PROVIDED_SERVICES = listOf(SVC_PING, SVC_STATUS)
    val OPTIONAL_SERVICES = emptyList<String>()
}
