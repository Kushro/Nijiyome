// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// Lenient JSON readers shared by the repositories of this module.

/** Shared JSON helpers of :core:data (lenient reads of provider answers). */
internal fun JsonObject.str(name: String): String? = this[name]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

internal fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

internal fun JsonObject.arr(name: String): JsonArray? = this[name] as? JsonArray

internal fun JsonObject.int(name: String): Int? = str(name)?.toDoubleOrNull()?.toInt()

internal fun JsonObject.long(name: String): Long? = str(name)?.toDoubleOrNull()?.toLong()

internal fun JsonObject.bool(name: String): Boolean? = this[name]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() }

internal fun JsonArray.objectList(): List<JsonObject> = mapNotNull { it as? JsonObject }
