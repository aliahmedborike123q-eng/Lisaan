package com.lisaan.app.core.database.converters

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class RoomTypeConverters {

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list == null) return "[]"
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(data)
            List(jsonArray.length()) { index -> jsonArray.getString(index) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromBooleanList(list: List<Boolean>?): String {
        if (list == null) return "[]"
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toBooleanList(data: String?): List<Boolean> {
        if (data.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(data)
            List(jsonArray.length()) { index -> jsonArray.getBoolean(index) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String {
        if (map == null) return "{}"
        val jsonObject = JSONObject()
        map.forEach { (k, v) -> jsonObject.put(k, v) }
        return jsonObject.toString()
    }

    @TypeConverter
    fun toStringMap(data: String?): Map<String, String> {
        if (data.isNullOrBlank()) return emptyMap()
        return try {
            val jsonObject = JSONObject(data)
            val result = mutableMapOf<String, String>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = jsonObject.optString(key, "")
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
