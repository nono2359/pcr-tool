package cn.wthee.pcrtool.ui.spine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.webkit.WebResourceResponse
import androidx.datastore.preferences.core.edit
import cn.wthee.pcrtool.data.preferences.SettingPreferencesKeys
import cn.wthee.pcrtool.ui.dataStoreSetting
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpineModelProvider(private val context: Context) {
    private val root = File(context.cacheDir, "spine-models")
    private val baseUrl = "https://wthee.xyz"
    private val roomMotionMasterUrl = "https://raw.githubusercontent.com/nono2359/pcr-tool/HEAD/app/src/main/assets/spine/data/room-motion-names-ja.json"

    fun handle(uri: Uri): WebResourceResponse? = when {
        uri.path == "/api/units" -> jsonResponse(runCatching { unitIndex() })
        uri.path == "/api/enemies" -> jsonResponse(runCatching { enemyIndex() })
        uri.path == "/api/clan-battle-bosses" -> jsonResponse(runCatching { clanBattleIndex() })
        uri.path == "/api/models/ensure" -> jsonResponse(runCatching { ensure(uri) })
        uri.path == "/api/room-motions" -> jsonResponse(runCatching { roomMotionIndex() })
        uri.path?.startsWith("/spine-cache/") == true -> runCatching { cachedResponse(uri.path!!) }.getOrElse { errorResponse(it) }
        else -> null
    }

    private fun ensure(uri: Uri): JSONObject {
        val type = uri.getQueryParameter("type")?.toIntOrNull() ?: 1
        val unitId = uri.getQueryParameter("unitId")?.toIntOrNull()
        val enemyId = uri.getQueryParameter("enemyId")?.toIntOrNull()
        require((unitId == null) != (enemyId == null)) { "unitIdかenemyIdの一方を指定してください" }
        val entry = if (unitId != null) buildUnit(unitId, type, uri.getQueryParameter("motionId") ?: "COMMON") else buildEnemy(enemyId!!)
        return JSONObject().put("entry", entry)
    }

    private fun roomMotionIndex(): JSONObject {
        val html = download("$baseUrl/redive/jp/resource/spine/cysp_room/").decodeToString()
        val ids = Regex("ROOM_SPINEUNIT_ANIMATION_([0-9]{6}|COMMON)\\.cysp")
            .findAll(html).map { it.groupValues[1] }.toMutableSet()
        ids += "COMMON"
        val names = roomMotionNames()
        val ordered = ids.sortedWith(compareBy<String> { it != "COMMON" }.thenBy { it })
        val missingIds = ordered.filter { it != "COMMON" && names[it].isNullOrBlank() }
        runBlocking {
            context.dataStoreSetting.edit {
                it[SettingPreferencesKeys.SP_FURNITURE_MASTER_MISSING_COUNT] = missingIds.size
                it[SettingPreferencesKeys.SP_FURNITURE_MASTER_MISSING_IDS] = missingIds.joinToString(",")
            }
        }
        val motions = JSONArray()
        ordered.forEach { id ->
            motions.put(JSONObject().put("id", id).put("name", names[id] ?: JSONObject.NULL))
        }
        return JSONObject()
            .put("motions", motions)
            .put("missingCount", missingIds.size)
            .put("missingIds", JSONArray(missingIds))
    }

    private fun roomMotionNames(): Map<String, String> {
        val bundled = context.assets.open("spine/data/room-motion-names-ja.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val source = runCatching {
            download("$roomMotionMasterUrl?timestamp=${System.currentTimeMillis()}").decodeToString()
        }.getOrDefault(bundled)
        val motions = JSONObject(source).getJSONArray("motions")
        return buildMap {
            for (index in 0 until motions.length()) {
                val item = motions.getJSONObject(index)
                val name = item.optString("name")
                if (name.isNotBlank()) put(item.getString("id"), name)
            }
            put("COMMON", "共通モーション")
        }
    }
    private fun unitIndex(): JSONObject {
        val classMap = JSONObject(download("$baseUrl/spine/classMap.json").decodeToString())
        val names = mutableMapOf<Int, Pair<String, Boolean>>()
        database().use { db ->
            db.rawQuery("SELECT u.unit_id, u.unit_name, EXISTS(SELECT 1 FROM unit_rarity r WHERE r.unit_id=u.unit_id AND r.rarity=6) FROM unit_data u", null).use { cursor ->
                while (cursor.moveToNext()) names[cursor.getInt(0)] = cursor.getString(1) to (cursor.getInt(2) != 0)
            }
        }
        val items = mutableListOf<JSONObject>()
        val keys = classMap.keys()
        while (keys.hasNext()) {
            val id = keys.next().toIntOrNull() ?: continue
            val info = classMap.getJSONObject(id.toString())
            val local = names[id]
            items += JSONObject().put("id", id).put("name", local?.first ?: info.optString("name", id.toString())).put("hasRarity6", local?.second ?: info.optBoolean("hasRarity6"))
        }
        val array = JSONArray()
        items.sortedBy { it.getInt("id") }.forEach { array.put(it) }
        return JSONObject().put("units", array)
    }

    private fun enemyIndex(): JSONObject {
        val classMap = JSONObject(download("$baseUrl/spine/classMapEnemy.json").decodeToString())
        val names = mutableMapOf<Int, String>()
        database().use { db ->
            db.rawQuery("SELECT unit_id, MIN(name) FROM enemy_parameter WHERE unit_id BETWEEN 200000 AND 399999 GROUP BY unit_id", null).use { cursor ->
                while (cursor.moveToNext()) names[cursor.getInt(0)] = cursor.getString(1)
            }
        }
        val items = mutableListOf<JSONObject>()
        val keys = classMap.keys()
        while (keys.hasNext()) {
            val id = keys.next().toIntOrNull() ?: continue
            val info = classMap.getJSONObject(id.toString())
            items += JSONObject().put("id", id).put("name", names[id] ?: info.optString("name", id.toString()))
        }
        val array = JSONArray()
        items.sortedBy { it.getInt("id") }.forEach { array.put(it) }
        return JSONObject().put("enemies", array)
    }

    private fun clanBattleIndex(): JSONObject {
        val sql = """
            SELECT m.clan_battle_id, s.start_time, s.end_time, m.phase,
                   m.boss_id_1, ep1.unit_id, ep1.name,
                   m.boss_id_2, ep2.unit_id, ep2.name,
                   m.boss_id_3, ep3.unit_id, ep3.name,
                   m.boss_id_4, ep4.unit_id, ep4.name,
                   m.boss_id_5, ep5.unit_id, ep5.name
            FROM clan_battle_2_map_data m
            JOIN clan_battle_schedule s ON s.clan_battle_id=m.clan_battle_id
            LEFT JOIN wave_group_data w1 ON w1.wave_group_id=m.wave_group_id_1
            LEFT JOIN wave_group_data w2 ON w2.wave_group_id=m.wave_group_id_2
            LEFT JOIN wave_group_data w3 ON w3.wave_group_id=m.wave_group_id_3
            LEFT JOIN wave_group_data w4 ON w4.wave_group_id=m.wave_group_id_4
            LEFT JOIN wave_group_data w5 ON w5.wave_group_id=m.wave_group_id_5
            LEFT JOIN enemy_parameter ep1 ON ep1.enemy_id=w1.enemy_id_1
            LEFT JOIN enemy_parameter ep2 ON ep2.enemy_id=w2.enemy_id_1
            LEFT JOIN enemy_parameter ep3 ON ep3.enemy_id=w3.enemy_id_1
            LEFT JOIN enemy_parameter ep4 ON ep4.enemy_id=w4.enemy_id_1
            LEFT JOIN enemy_parameter ep5 ON ep5.enemy_id=w5.enemy_id_1
            WHERE m.clan_battle_id=(SELECT MAX(clan_battle_id) FROM clan_battle_2_map_data)
            ORDER BY m.phase, m.lap_num_from LIMIT 1
        """.trimIndent()
        database().use { db ->
            db.rawQuery(sql, null).use { cursor ->
                require(cursor.moveToFirst()) { "クランバトルデータがありません" }
                val bosses = JSONArray()
                for (index in 0 until 5) {
                    val offset = 4 + index * 3
                    bosses.put(JSONObject().put("id", cursor.getInt(offset + 1)).put("name", "${index + 1}ボス　${cursor.getString(offset + 2)}").put("bossNumber", index + 1).put("bossId", cursor.getInt(offset)))
                }
                return JSONObject().put("clanBattleId", cursor.getInt(0)).put("startTime", cursor.getString(1)).put("endTime", cursor.getString(2)).put("phase", cursor.getInt(3)).put("bosses", bosses)
            }
        }
    }
    private fun database(): SQLiteDatabase = SQLiteDatabase.openDatabase(context.getDatabasePath("redive_jp.db").path, null, SQLiteDatabase.OPEN_READONLY)

    private fun unitName(unitId: Int, fallback: String): String = runCatching {
        database().use { db -> db.rawQuery("SELECT unit_name FROM unit_data WHERE unit_id=?", arrayOf(unitId.toString())).use { if (it.moveToFirst()) it.getString(0) else fallback } }
    }.getOrDefault(fallback)

    private fun enemyName(unitId: Int, fallback: String): String = runCatching {
        database().use { db -> db.rawQuery("SELECT name FROM enemy_parameter WHERE unit_id=? LIMIT 1", arrayOf(unitId.toString())).use { if (it.moveToFirst()) it.getString(0) else fallback } }
    }.getOrDefault(fallback)
    private fun buildUnit(unitId: Int, type: Int, motionId: String): JSONObject {
        require(type == 1 || type == 2)
        val baseUnitId = unitId - unitId % 100 + 1
        val info = JSONObject(download("$baseUrl/spine/classMap.json").decodeToString()).optJSONObject(baseUnitId.toString())
            ?: error("unitId $baseUnitId はclassMapにありません")
        val modelId = if (unitId != baseUnitId) unitId else baseUnitId + if (info.optBoolean("hasRarity6")) 60 else 30
        val className = if (info.optBoolean("hasSpecialBase")) baseUnitId.toString() else "%02d".format(info.getInt("type"))
        val relative = if (type == 1) "unit/$unitId/battle" else "unit/$unitId/room/$motionId"
        val directory = File(root, relative).apply { mkdirs() }
        val skeleton = File(directory, "model.skel")
        val atlas = File(directory, "model.atlas")
        val texture = File(directory, "$modelId.png")
        if (!skeleton.isFile || !atlas.isFile || !texture.isFile) {
            val spineRoot = "$baseUrl/redive/jp/resource/spine"
            if (type == 1) {
                val skeletonBase = if (info.optBoolean("hasSpecialBase")) baseUnitId.toString() else "000000"
                val base = download("$spineRoot/cysp/${skeletonBase}_CHARA_BASE.cysp")
                val common = download("$spineRoot/cysp/${className}_COMMON_BATTLE.cysp")
                val unitAnimation = download("$spineRoot/cysp/${baseUnitId}_BATTLE.cysp")
                skeleton.writeBytes(buildSkeleton(base, listOf(common, unitAnimation)))
                atlas.writeBytes(download("$spineRoot/cysp/$modelId.atlas"))
                texture.writeBytes(download("$spineRoot/png/$modelId.png"))
            } else {
                require(motionId == "COMMON" || Regex("\\d{6}").matches(motionId))
                val base = download("$spineRoot/cysp_room/ROOM_SPINEUNIT_ANIMATION_BASE.cysp")
                val ids = if (motionId == "COMMON") listOf("COMMON") else listOf("COMMON", motionId)
                skeleton.writeBytes(buildSkeleton(base, ids.map { download("$spineRoot/cysp_room/ROOM_SPINEUNIT_ANIMATION_$it.cysp") }))
                atlas.writeBytes(download("$spineRoot/cysp_room/$modelId.atlas"))
                texture.writeBytes(download("$spineRoot/png_room/$modelId.png"))
            }
        }
        return entry(unitName(baseUnitId, info.optString("name", baseUnitId.toString())), modelId, relative, if (type == 1) "${className}_idle" else "cmn_idol_def_N")
    }

    private fun buildEnemy(enemyId: Int): JSONObject {
        val info = JSONObject(download("$baseUrl/spine/classMapEnemy.json").decodeToString()).optJSONObject(enemyId.toString())
            ?: error("enemyId $enemyId はclassMapにありません")
        val relative = "enemy/$enemyId"
        val directory = File(root, relative).apply { mkdirs() }
        val skeleton = File(directory, "model.skel")
        val atlas = File(directory, "model.atlas")
        val texture = File(directory, "$enemyId.png")
        if (!skeleton.isFile || !atlas.isFile || !texture.isFile) {
            val spineRoot = "$baseUrl/redive/jp/resource/spine"
            skeleton.writeBytes(buildSkeleton(download("$spineRoot/cysp/${enemyId}_CHARA_BASE.cysp"), listOf(download("$spineRoot/cysp/${enemyId}_BATTLE.cysp"))))
            atlas.writeText(download("$spineRoot/cysp/$enemyId.atlas").decodeToString().lineSequence().joinToString("\n") { it.trimEnd() } + "\n")
            texture.writeBytes(download("$spineRoot/png/$enemyId.png"))
        }
        return entry(enemyName(enemyId, info.optString("name", enemyId.toString())), enemyId, relative, "${enemyId}_idle")
    }

    private fun entry(name: String, modelId: Int, relative: String, animation: String) = JSONObject()
        .put("name", "$name（$modelId）").put("format", "binary")
        .put("skeleton", "/spine-cache/$relative/model.skel").put("atlas", "/spine-cache/$relative/model.atlas")
        .put("animation", animation).put("premultipliedAlpha", true)

    private fun buildSkeleton(base: ByteArray, animations: List<ByteArray>): ByteArray {
        require(base.size >= 64)
        val parsed = animations.map { data ->
            require(data.size >= 16)
            val count = ByteBuffer.wrap(data, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val offset = (count + 1) * 32
            require(offset <= data.size)
            count to data.copyOfRange(offset, data.size)
        }
        val count = parsed.sumOf { it.first }
        require(count <= 127)
        return base.copyOfRange(64, base.size) + byteArrayOf(count.toByte()) + parsed.fold(ByteArray(0)) { result, item -> result + item.second }
    }

    private fun download(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "PCR-Tool-Spine-Viewer/1")
        return try { connection.inputStream.use { it.readBytes() } } finally { connection.disconnect() }
    }

    private fun cachedResponse(path: String): WebResourceResponse {
        val file = File(root, path.removePrefix("/spine-cache/")).canonicalFile
        require(file.path.startsWith(root.canonicalPath + File.separator) && file.isFile)
        val mime = when (file.extension.lowercase()) { "png" -> "image/png"; "atlas" -> "text/plain"; else -> "application/octet-stream" }
        return WebResourceResponse(mime, null, file.inputStream())
    }

    private fun jsonResponse(result: Result<JSONObject>): WebResourceResponse {
        val ok = result.isSuccess
        val json = result.getOrElse { JSONObject().put("error", it.message ?: it.javaClass.simpleName) }
        return WebResourceResponse("application/json", "UTF-8", if (ok) 200 else 502, if (ok) "OK" else "Bad Gateway", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(json.toString().toByteArray()))
    }

    private fun errorResponse(error: Throwable) = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream((error.message ?: "not found").toByteArray()))
    private fun repairName(value: String): String {
        if (value.any { it.code > 255 }) return value
        return runCatching { String(value.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) }.getOrDefault(value)
    }
}
