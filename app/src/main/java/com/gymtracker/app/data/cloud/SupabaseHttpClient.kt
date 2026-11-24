package com.gymtracker.app.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SupabaseAuthResult {
    data class Session(
        val userId: String,
        val email: String?,
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtSec: Long
    ) : SupabaseAuthResult

    data class ConfirmationRequired(val email: String?) : SupabaseAuthResult
}

@Singleton
class SupabaseHttpClient @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun signUp(
        baseUrl: String,
        publishableKey: String,
        email: String,
        password: String
    ): SupabaseAuthResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("email", JsonPrimitive(email.trim()))
            put("password", JsonPrimitive(password))
        }.toString()

        val resp = request(
            method = "POST",
            fullUrl = "${cleanUrl(baseUrl)}/auth/v1/signup",
            publishableKey = publishableKey,
            bearerToken = null,
            body = body
        )
        parseAuthResponse(resp.body, fallbackEmail = email.trim())
    }

    suspend fun signIn(
        baseUrl: String,
        publishableKey: String,
        email: String,
        password: String
    ): SupabaseAuthResult.Session = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("email", JsonPrimitive(email.trim()))
            put("password", JsonPrimitive(password))
        }.toString()

        val resp = request(
            method = "POST",
            fullUrl = "${cleanUrl(baseUrl)}/auth/v1/token?grant_type=password",
            publishableKey = publishableKey,
            bearerToken = null,
            body = body
        )
        when (val parsed = parseAuthResponse(resp.body, fallbackEmail = email.trim())) {
            is SupabaseAuthResult.Session -> parsed
            is SupabaseAuthResult.ConfirmationRequired ->
                throw IOException("Email confirmation required before sign-in")
        }
    }

    suspend fun refreshToken(
        baseUrl: String,
        publishableKey: String,
        refreshToken: String
    ): SupabaseAuthResult.Session = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("refresh_token", JsonPrimitive(refreshToken))
        }.toString()

        val resp = request(
            method = "POST",
            fullUrl = "${cleanUrl(baseUrl)}/auth/v1/token?grant_type=refresh_token",
            publishableKey = publishableKey,
            bearerToken = null,
            body = body
        )
        when (val parsed = parseAuthResponse(resp.body, fallbackEmail = null)) {
            is SupabaseAuthResult.Session -> parsed
            is SupabaseAuthResult.ConfirmationRequired ->
                throw IOException("Session refresh did not return an access token")
        }
    }

    suspend fun upsertRows(
        baseUrl: String,
        publishableKey: String,
        accessToken: String,
        table: String,
        rowsJson: JsonArray,
        onConflict: String
    ) = withContext(Dispatchers.IO) {
        if (rowsJson.isEmpty()) return@withContext
        val encodedConflict = URLEncoder.encode(onConflict, StandardCharsets.UTF_8.name())
        val url = "${cleanUrl(baseUrl)}/rest/v1/$table?on_conflict=$encodedConflict"
        request(
            method = "POST",
            fullUrl = url,
            publishableKey = publishableKey,
            bearerToken = accessToken,
            body = rowsJson.toString(),
            extraHeaders = mapOf(
                "Prefer" to "resolution=merge-duplicates,return=minimal"
            )
        )
    }

    suspend fun selectAllRows(
        baseUrl: String,
        publishableKey: String,
        accessToken: String,
        table: String,
        select: String = "*",
        orderBy: String? = null
    ): List<JsonObject> = withContext(Dispatchers.IO) {
        val all = mutableListOf<JsonObject>()
        var offset = 0
        val pageSize = 1000
        val cleanBase = cleanUrl(baseUrl)
        val encodedSelect = URLEncoder.encode(select, StandardCharsets.UTF_8.name())
        val orderQuery = orderBy?.let {
            "&order=" + URLEncoder.encode(it, StandardCharsets.UTF_8.name())
        }.orEmpty()

        while (true) {
            val resp = request(
                method = "GET",
                fullUrl = "$cleanBase/rest/v1/$table?select=$encodedSelect$orderQuery",
                publishableKey = publishableKey,
                bearerToken = accessToken,
                body = null,
                extraHeaders = mapOf(
                    "Range-Unit" to "items",
                    "Range" to "$offset-${offset + pageSize - 1}"
                )
            )
            val arr = json.parseToJsonElement(resp.body).jsonArray
            arr.forEach { elem ->
                all += elem.jsonObject
            }
            if (arr.size < pageSize) break
            offset += pageSize
        }
        all
    }

    private fun parseAuthResponse(rawBody: String, fallbackEmail: String?): SupabaseAuthResult {
        val obj = json.parseToJsonElement(rawBody).jsonObject
        val accessToken = obj["access_token"]?.jsonPrimitive?.contentOrNull
        val refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull
        val expiresAt = obj["expires_at"]?.jsonPrimitive?.longOrNull
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L

        val userObj = obj["user"] as? JsonObject ?: obj
        val userId = userObj["id"]?.jsonPrimitive?.contentOrNull
        val email = userObj["email"]?.jsonPrimitive?.contentOrNull ?: fallbackEmail

        if (!accessToken.isNullOrBlank() && !userId.isNullOrBlank()) {
            val computedExp = expiresAt ?: ((System.currentTimeMillis() / 1000L) + expiresIn)
            return SupabaseAuthResult.Session(
                userId = userId,
                email = email,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtSec = computedExp
            )
        }
        return SupabaseAuthResult.ConfirmationRequired(email = email)
    }

    private data class HttpResponse(val code: Int, val body: String)

    private fun request(
        method: String,
        fullUrl: String,
        publishableKey: String,
        bearerToken: String?,
        body: String?,
        extraHeaders: Map<String, String> = emptyMap()
    ): HttpResponse {
        val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", publishableKey.trim())
            setRequestProperty("Accept", "application/json")
            // Never send sb_publishable_* on Authorization header (Supabase API key spec).
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                conn.outputStream.use { os ->
                    os.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code: ${extractErrorMessage(text)}")
            }
            return HttpResponse(code, text.ifBlank { "[]" })
        } finally {
            conn.disconnect()
        }
    }

    private fun extractErrorMessage(raw: String): String {
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        val msg = parsed?.firstString("msg", "error_description", "message", "hint", "error")
        return msg ?: raw.take(240).ifBlank { "Unknown server error" }
    }

    private fun JsonObject.firstString(vararg keys: String): String? {
        for (k in keys) {
            val el: JsonElement = this[k] ?: continue
            val s = (el as? JsonPrimitive)?.contentOrNull
            if (!s.isNullOrBlank()) return s
        }
        return null
    }

    private fun cleanUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/').also {
            if (it.isBlank()) throw IOException("Supabase URL is not configured")
        }
}
