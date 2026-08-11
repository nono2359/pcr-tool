package cn.wthee.pcrtool.utils

import cn.wthee.pcrtool.MyApplication
import org.json.JSONObject

/**
 * 1コマ漫画のキャラクターIDと日本語タイトルの対応表。
 */
object ComicTitleMaster {
    private val titles: Map<Int, String> by lazy {
        runCatching {
            val source = MyApplication.context.assets.open("comic-titles-ja.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val json = JSONObject(source)
            buildMap {
                json.keys().forEach { key ->
                    key.toIntOrNull()?.let { id -> put(id, json.getString(key)) }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun getTitle(url: String?): String? {
        val unitId = url
            ?.substringAfterLast('/')
            ?.substringBefore('.')
            ?.toIntOrNull()
            ?: return null
        return titles[unitId]
    }
}