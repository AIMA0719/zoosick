package com.myinfocar.aicoachstock.data.local.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Room TypeConverters.
 *
 * - Instant ↔ Long (epoch millis)
 * - List/Map ↔ JSON (kotlinx-serialization)
 * - enum은 Entity 필드를 String으로 두고 Mapper에서 변환 (TypeConverter 안 씀).
 */
class AppTypeConverters {

    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun stringListToJson(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        json.decodeFromString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun stringMapToJson(value: Map<String, String>): String =
        json.encodeToString(MapSerializer(String.serializer(), String.serializer()), value)

    @TypeConverter
    fun jsonToStringMap(value: String): Map<String, String> =
        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), value)

    @TypeConverter
    fun contextRefsToJson(value: Map<String, List<String>>): String =
        json.encodeToString(
            MapSerializer(String.serializer(), ListSerializer(String.serializer())),
            value,
        )

    @TypeConverter
    fun jsonToContextRefs(value: String): Map<String, List<String>> =
        json.decodeFromString(
            MapSerializer(String.serializer(), ListSerializer(String.serializer())),
            value,
        )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
