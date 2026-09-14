package com.ghaniram.zoya

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Dedicated GitHub connector client enabling Anu to interact with repositories,
 * create and push code files, manage issues, and authenticate personal access tokens.
 */
object GitHubConnectorClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    fun testConnection(token: String): Pair<Boolean, String> = try {
        if (token.isBlank()) Pair(false, "Token is empty") else {
            val request = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Anu-Assistant-Android")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) Pair(true, "Connected as ${JSONObject(body).optString("login", "User")}")
                else Pair(false, "Authentication failed: HTTP ${response.code}")
            }
        }
    } catch (e: Exception) { Pair(false, "Connection error: ${e.message ?: "network failure"}") }

    fun listRepos(token: String): String {
        return try {
            val request = Request.Builder().url("https://api.github.com/user/repos?sort=updated&per_page=10")
                .header("Authorization", "Bearer ${token.trim()}")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Anu-Assistant-Android").build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return "GitHub error: HTTP ${response.code}"
                val array = JSONArray(body)
                val names = mutableListOf<String>()
                for (i in 0 until minOf(array.length(), 10)) names.add(array.getJSONObject(i).optString("full_name"))
                if (names.isEmpty()) "No repositories found." else "Recent repositories:\n" + names.joinToString("\n") { "• $it" }
            }
        } catch (e: Exception) { "Could not fetch repositories: ${e.message ?: "network error"}" }
    }

    fun createRepo(token: String, name: String, description: String = "", isPrivate: Boolean = false): String = try {
        val payload = JSONObject().apply { put("name", name.trim()); put("description", description.trim()); put("private", isPrivate); put("auto_init", true) }
        val request = Request.Builder().url("https://api.github.com/user/repos")
            .header("Authorization", "Bearer ${token.trim()}").header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Anu-Assistant-Android").post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) "Successfully created repository $name: ${JSONObject(body).optString("html_url")}" else "Failed to create repo: HTTP ${response.code} $body"
        }
    } catch (e: Exception) { "Failed to create repo: ${e.message ?: "unknown error"}" }

    fun createOrUpdateFile(token: String, repo: String, path: String, content: String, message: String): String {
        return try {
            val cleanRepo = repo.trim().removePrefix("https://github.com/").removeSuffix(".git")
            val cleanPath = path.trim().removePrefix("/")
            if (cleanRepo.isBlank() || cleanPath.isBlank()) return "GitHub write failed: repository and file path are required"
            val getReq = Request.Builder().url("https://api.github.com/repos/$cleanRepo/contents/$cleanPath")
                .header("Authorization", "Bearer ${token.trim()}").header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Anu-Assistant-Android").build()
            var existingSha: String? = null
            client.newCall(getReq).execute().use { res ->
                if (res.isSuccessful) existingSha = JSONObject(res.body?.string().orEmpty()).optString("sha").ifBlank { null }
            }
            val payload = JSONObject().apply {
                put("message", message.ifBlank { "Update $cleanPath from Anu" })
                put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                if (existingSha != null) put("sha", existingSha)
            }
            val putReq = Request.Builder().url("https://api.github.com/repos/$cleanRepo/contents/$cleanPath")
                .header("Authorization", "Bearer ${token.trim()}").header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Anu-Assistant-Android").put(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
            client.newCall(putReq).execute().use { res ->
                if (res.isSuccessful) "Successfully committed $cleanPath to $cleanRepo"
                else "Commit failed: HTTP ${res.code} ${res.body?.string().orEmpty()}"
            }
        } catch (e: Exception) { "Failed to write file to GitHub: ${e.message ?: "unknown error"}" }
    }

    fun executeAction(token: String, action: String, repo: String, path: String, content: String, message: String): String = when (action.lowercase().trim()) {
        "test", "test_connection", "connect", "connection" -> testConnection(token).second
        "list_repos", "list", "repos", "get_repos" -> listRepos(token)
        "create_repo", "createrepo", "new_repo" -> createRepo(token, repo)
        "read", "read_file", "get_file", "file" -> if (repo.isBlank() || path.isBlank()) "Please specify repository (e.g. username/repo) and file path" else readFile(token, repo, path)
        "commit", "commit_file", "create_file", "write_file", "write", "create", "update", "update_file", "push_file", "save_file" -> if (repo.isBlank() || path.isBlank()) "Please specify repository (e.g. username/repo) and file path" else createOrUpdateFile(token, repo, path, content, message)
        else -> "Unsupported GitHub action '$action'. Supported actions: test, list_repos, read, write, create, update, commit."
    }

    private fun readFile(token: String, repo: String, path: String): String {
        return try {
            val cleanRepo = repo.trim().removePrefix("https://github.com/").removeSuffix(".git")
            val cleanPath = path.trim().removePrefix("/")
            val request = Request.Builder().url("https://api.github.com/repos/$cleanRepo/contents/$cleanPath")
                .header("Authorization", "Bearer ${token.trim()}").header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Anu-Assistant-Android").build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return "GitHub read failed: HTTP ${response.code}"
                val encoded = JSONObject(body).optString("content").replace("\n", "")
                if (encoded.isBlank()) return "GitHub file '$cleanPath' was found but contains no inline text content."
                "Successfully read $cleanPath from $cleanRepo:\n${String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)}"
            }
        } catch (e: Exception) { "Failed to read file from GitHub: ${e.message ?: "unknown error"}" }
    }
}
