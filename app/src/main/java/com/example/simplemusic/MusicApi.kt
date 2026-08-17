package com.example.simplemusic

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.Response

/**
 * 歌曲数据模型
 */
data class SongInfo(
    val hash: String = "",
    @SerializedName("mixsongid") val mixSongId: Long = 0,
    val title: String = "",
    val author: String = "",
    @SerializedName("album_name") val albumName: String = "",
    @SerializedName("album_id") val albumId: Long = 0,
    val duration: Int = 0,       // 秒
    val filesize: Long = 0,
    @SerializedName("imgurl") val imgUrl: String = "",
    @SerializedName("audio_id") val audioId: Long = 0,
    @SerializedName("album_audio_id") val albumAudioId: Long = 0
)

/**
 * 搜索结果
 */
data class SearchResult(
    val lists: List<SongInfo>? = null,
    val list: List<SongInfo>? = null,
    val total: Int = 0
)

/**
 * 歌曲播放 URL
 */
data class SongUrlResult(
    val url: String = "",
    val status: Int = 0,
    val bitrate: Long = 0,
    val format: String = "",
    val timeLength: Int = 0
)

/**
 * API 响应包装
 */
data class ApiResponse<T>(
    val status: Int = 0,
    val error_code: Int = 0,
    val msg: String = "",
    val data: T? = null
)

/**
 * 所有后端 API 接口
 * 对应原项目 src/renderer/api/ 下的所有文件
 */
object MusicApi {

    private val gson = Gson()

    // ──────────── 搜索 ────────────

    /** 搜索歌曲 */
    suspend fun search(keywords: String, type: String = "song", page: Int = 1, pagesize: Int = 30): SearchResult? {
        val response = ApiClient.get("/search", mapOf(
            "keywords" to keywords,
            "type" to type,
            "page" to page.toString(),
            "pagesize" to pagesize.toString()
        ))
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val apiResp = gson.fromJson(body, ApiResponse::class.java)
        val dataJson = apiResp?.data ?: return null
        // 兼容不同响应格式
        return try {
            gson.fromJson(gson.toJson(dataJson), SearchResult::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** 获取搜索建议 */
    suspend fun searchSuggest(keywords: String): List<String> {
        val response = ApiClient.get("/search/suggest", mapOf("keywords" to keywords))
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            (data?.get("lists") as? List<Map<String, Any>>)?.mapNotNull { it["keyword"] as? String } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 获取搜索热词 */
    suspend fun searchHot(): List<String> {
        val response = ApiClient.get("/search/hot")
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            (data?.get("list") as? List<Map<String, Any>>)?.mapNotNull { it["keyword"] as? String } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ──────────── 歌曲 ────────────

    /** 获取歌曲播放地址 */
    suspend fun getSongUrl(hash: String, quality: String = ""): SongUrlResult? {
        val params = mutableMapOf("hash" to hash)
        if (quality.isNotEmpty()) params["quality"] = quality
        val response = ApiClient.get("/song/url", params)
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] ?: json
            gson.fromJson(gson.toJson(data), SongUrlResult::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** 获取歌曲歌词 */
    suspend fun getLyric(id: String, accesskey: String): String? {
        val response = ApiClient.get("/lyric", mapOf(
            "id" to id,
            "accesskey" to accesskey,
            "decode" to "true",
            "fmt" to "krc"
        ))
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            data?.get("content") as? String ?: json["content"] as? String
        } catch (e: Exception) {
            null
        }
    }

    /** 搜索歌词 */
    suspend fun searchLyric(hash: String): String? {
        val response = ApiClient.get("/search/lyric", mapOf("hash" to hash))
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            val candidates = data?.get("candidates") as? List<*>
            val first = candidates?.firstOrNull() as? Map<*, *>
            val lyricId = first?.get("id")?.toString() ?: return null
            val accesskey = first?.get("accesskey")?.toString() ?: return null
            getLyric(lyricId, accesskey)
        } catch (e: Exception) {
            null
        }
    }

    /** 获取歌曲权限/音质信息 */
    suspend fun getSongPrivilege(hash: String, albumId: String? = null): Map<String, Any>? {
        val params = mutableMapOf("hash" to hash)
        if (albumId != null) params["album_id"] = albumId
        val response = ApiClient.get("/privilege/lite", params)
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(body, Map::class.java)["data"] as? Map<String, Any>
        } catch (e: Exception) {
            null
        }
    }

    // ──────────── 每日推荐 ────────────

    /** 获取每日推荐歌曲 */
    suspend fun getEverydayRecommend(): List<SongInfo> {
        val response = ApiClient.get("/everyday/recommend")
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val lists = data?.get("lists") as? List<Map<String, Any>>
            lists?.map { gson.fromJson(gson.toJson(it), SongInfo::class.java) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ──────────── 排行榜 ────────────

    /** 获取新歌榜 */
    suspend fun getNewSongs(): List<SongInfo> {
        val response = ApiClient.get("/top/song")
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val lists = data?.get("list") as? List<Map<String, Any>>
            lists?.map { gson.fromJson(gson.toJson(it), SongInfo::class.java) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 获取排行榜列表 */
    suspend fun getRankList(): List<Map<String, Any>> {
        val response = ApiClient.get("/rank/list")
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            data?.get("list") as? List<Map<String, Any>> ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 获取排行榜歌曲 */
    suspend fun getRankSongs(rankId: Int, page: Int = 1, pagesize: Int = 100): List<SongInfo> {
        val response = ApiClient.get("/rank/audio", mapOf(
            "rankid" to rankId.toString(),
            "page" to page.toString(),
            "pagesize" to pagesize.toString()
        ))
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val lists = data?.get("list") as? List<Map<String, Any>>
            lists?.map { gson.fromJson(gson.toJson(it), SongInfo::class.java) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ──────────── 歌单 ────────────

    /** 获取推荐歌单 */
    suspend fun getRecommendPlaylists(): List<Map<String, Any>> {
        val response = ApiClient.get("/playlist/recommend")
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            data?.get("list") as? List<Map<String, Any>> ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 获取歌单歌曲 */
    suspend fun getPlaylistSongs(listId: String, page: Int = 1, pagesize: Int = 30): List<SongInfo> {
        val response = ApiClient.get("/playlist/track/all", mapOf(
            "id" to listId,
            "page" to page.toString(),
            "pagesize" to pagesize.toString()
        ))
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val lists = data?.get("list") as? List<Map<String, Any>>
            lists?.map { gson.fromJson(gson.toJson(it), SongInfo::class.java) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ──────────── 歌手 ────────────

    /** 获取歌手歌曲 */
    suspend fun getArtistSongs(artistId: String, page: Int = 1, pagesize: Int = 30): List<SongInfo> {
        val response = ApiClient.get("/artist/audios", mapOf(
            "id" to artistId,
            "page" to page.toString(),
            "pagesize" to pagesize.toString(),
            "sort" to "hot"
        ))
        if (!response.isSuccessful) return emptyList()
        val body = response.body?.string() ?: return emptyList()
        return try {
            val json = gson.fromJson(body, Map::class.java)
            val data = json["data"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val lists = data?.get("list") as? List<Map<String, Any>>
            lists?.map { gson.fromJson(gson.toJson(it), SongInfo::class.java) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}