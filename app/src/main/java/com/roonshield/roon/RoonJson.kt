package com.roonshield.roon

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Shared JSON codec for Roon MOO payloads. Roon uses snake_case keys; models are written
 * in idiomatic camelCase and mapped automatically. Unknown keys are ignored because Roon's
 * objects carry many fields we don't model.
 */
@OptIn(ExperimentalSerializationApi::class)
val RoonJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
    namingStrategy = JsonNamingStrategy.SnakeCase
}
