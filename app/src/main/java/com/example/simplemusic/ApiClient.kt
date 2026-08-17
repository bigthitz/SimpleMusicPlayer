package com.example.simplemusic

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 设备信息（对应原项目 deviceStore.info）
 */
data class DeviceInfo(
    var dfid: String = "",
    var mid: String = "",
    var uuid: String = "",
    var guid: String = "",
    var serverDev: String = "",
    var mac: String = "",
    var appid: String = "",
    var clientver: String = ""
)

/**
 * 统一的 API 客户端
 * 对应原项目 src/renderer/utils/request.ts
 */
object ApiClient {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 后端 API 地址，启动时由用户配置 */
    var apiBaseUrl: String = ""

    /** 设备信息 */
    var device = DeviceInfo()

    /** 用户认证信息 */
    var token: String = ""
    var userId: String = ""
    var t1: String = ""

    private var deviceRegistered = false

    /**
     * 构建 Authorization header（复现原项目 buildAuthHeader）
     */
    private fun buildAuthHeader(skipAuth: Boolean = false): String {
        if (skipAuth) return ""
        val parts = mutableListOf<String>()
        if (token.isNotEmpty()) parts.add("token=$token")
        if (userId.isNotEmpty()) parts.add("userid=$userId")
        if (t1.isNotEmpty()) parts.add("t1=$t1")
        if (device.dfid.isNotEmpty()) parts.add("dfid=${device.dfid}")
        if (device.mid.isNotEmpty()) parts.add("KUGOU_API_MID=${device.mid}")
        if (device.uuid.isNotEmpty()) parts.add("uuid=${device.uuid}")
        if (device.guid.isNotEmpty()) parts.add("KUGOU_API_GUID=${device.guid}")
        if (device.serverDev.isNotEmpty()) parts.add("KUGOU_API_DEV=${device.serverDev}")
        if (device.mac.isNotEmpty()) parts.add("KUGOU_API_MAC=${device.mac}")
        return parts.joinToString(";")
    }

    /**
     * 注册设备（对应原项目 registerDevice + ensureDevice）
     */
    suspend fun registerDevice(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = request(
                method = "GET",
                path = "/register/dev",
                skipAuth = true
            )
            val body = response.body?.string() ?: return@withContext Result.failure(IOException("空响应"))
            val json = gson.fromJson(body, Map::class.java) ?: return@withContext Result.failure(IOException("JSON 解析失败"))
            val data = json["data"] as? Map<*, *> ?: return@withContext Result.failure(IOException("缺少 data 字段"))

            device.dfid = data["dfid"] as? String ?: ""
            device.mid = data["mid"] as? String ?: ""
            device.uuid = data["uuid"] as? String ?: ""
            device.guid = data["guid"] as? String ?: ""
            device.serverDev = data["serverDev"] as? String ?: ""
            device.mac = data["mac"] as? String ?: ""
            device.appid = data["appid"] as? String ?: ""
            device.clientver = data["clientver"] as? String ?: ""

            deviceRegistered = device.dfid.isNotEmpty()
            if (deviceRegistered) Result.success(Unit) else Result.failure(IOException("注册失败：未获取到 dfid"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 确保设备已注册
     */
    suspend fun ensureDevice(): Result<Unit> {
        if (deviceRegistered) return Result.success(Unit)
        return registerDevice()
    }

    /**
     * 发起 HTTP 请求（对应原项目 httpOnlineRequest + ipcRequest）
     */
    suspend fun request(
        method: String,
        path: String,
        params: Map<String, String?> = emptyMap(),
        body: Any? = null,
        skipAuth: Boolean = false
    ): Response = withContext(Dispatchers.IO) {
        // 自动注册设备（跳过注册接口本身）
        if (!skipAuth && !deviceRegistered) {
            ensureDevice()
        }

        val urlBuilder = StringBuilder(apiBaseUrl.trimEnd('/'))
        urlBuilder.append(path)

        // 构建 URL 参数
        if (params.isNotEmpty()) {
            urlBuilder.append("?")
            params.forEach { (key, value) ->
                if (value != null) {
                    urlBuilder.append("$key=${java.net.URLEncoder.encode(value, "UTF-8")}&")
                }
            }
        }
        val url = urlBuilder.toString().trimEnd('&', '?')

        val requestBuilder = Request.Builder().url(url)

        // 注入 Authorization
        val auth = buildAuthHeader(skipAuth)
        if (auth.isNotEmpty()) {
            requestBuilder.addHeader("Authorization", auth)
        }

        // 请求体
        if (body != null && method != "GET") {
            val jsonBody = gson.toJson(body)
            requestBuilder.addHeader("Content-Type", "application/json")
            requestBuilder.method(method, jsonBody.toRequestBody("application/json".toMediaType()))
        } else {
            requestBuilder.method(method, null)
        }

        val request = requestBuilder.build()
        client.newCall(request).execute()
    }

    /**
     * 便捷 GET 请求
     */
    suspend fun get(
        path: String,
        params: Map<String, String?> = emptyMap(),
        skipAuth: Boolean = false
    ): Response = request("GET", path, params, skipAuth = skipAuth)

    /**
     * 便捷 POST 请求
     */
    suspend fun post(
        path: String,
        params: Map<String, String?> = emptyMap(),
        body: Any? = null,
        skipAuth: Boolean = false
    ): Response = request("POST", path, params, body, skipAuth)

    /**
     * 解析响应 JSON 为指定类型
     */
    inline fun <reified T> parseResponse(response: Response): T? {
        return try {
            val bodyStr = response.body?.string() ?: return null
            gson.fromJson(bodyStr, T::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查响应是否为认证过期（对应原项目 checkAuthExpiration）
     */
    fun isAuthExpired(response: Response): Boolean {
        return try {
            val body = response.body?.string() ?: return false
            response.body?.let { response.newBuilder().build() }
            val json = gson.fromJson(body, Map::class.java) ?: return false
            val errorCode = (json["error_code"] as? Number)?.toInt() ?: 0
            val msg = json["msg"] as? String ?: ""
            errorCode == 20018 || msg.contains("登录已过期")
        } catch (e: Exception) {
            false
        }
    }
}