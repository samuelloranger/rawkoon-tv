package cloud.rawkoon.tv.data

import org.json.JSONArray
import org.json.JSONObject

/** The string at [key], or null when it is missing, null or empty. */
fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)

fun JSONObject.optFloatOrNull(key: String): Float? = optDoubleOrNull(key)?.toFloat()

fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key).takeUnless { it.isNaN() }

/** The objects of the array at [key], skipping anything else in it; empty when there is no array. */
fun JSONObject.objects(key: String): List<JSONObject> {
    val array: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}
