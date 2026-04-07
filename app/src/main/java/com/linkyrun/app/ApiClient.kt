package com.linkyrun.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL = "https://linkyrun.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class GameInfo(
        val start: String,
        val goal: String,
        val difficulty: String,
        val wiki: String = "namu",
        val dayNum: Int? = null
    )

    data class RankEntry(
        val rank: Int,
        val nickname: String,
        val start: String,
        val goal: String,
        val elapsedMs: Long,
        val hops: Int,
        val difficulty: String,
        val path: List<String> = emptyList()
    )

    /** GET /api/random-game?difficulty=X&wiki=namu */
    fun getRandomGame(difficulty: String, wiki: String = "namu"): GameInfo? {
        return try {
            val req = Request.Builder()
                .url("$BASE_URL/api/random-game?difficulty=$difficulty&wiki=$wiki")
                .build()
            val body = client.newCall(req).execute().body?.string() ?: return null
            val j = JSONObject(body)
            if (j.has("error")) return null
            GameInfo(
                start = j.getString("start"),
                goal = j.getString("goal"),
                difficulty = j.getString("difficulty"),
                wiki = j.optString("wiki", "namu")
            )
        } catch (e: Exception) {
            null
        }
    }

    /** GET /api/daily?wiki=namu */
    fun getDaily(wiki: String = "namu"): GameInfo? {
        return try {
            val req = Request.Builder()
                .url("$BASE_URL/api/daily?wiki=$wiki")
                .build()
            val body = client.newCall(req).execute().body?.string() ?: return null
            val j = JSONObject(body)
            if (j.has("error")) return null
            GameInfo(
                start = j.getString("start"),
                goal = j.getString("goal"),
                difficulty = j.optString("difficulty", "daily"),
                wiki = j.optString("wiki", "namu"),
                dayNum = if (j.has("day_num")) j.getInt("day_num") else null
            )
        } catch (e: Exception) {
            null
        }
    }

    /** POST /api/ranking */
    fun submitRanking(
        nickname: String,
        start: String,
        goal: String,
        elapsedMs: Long,
        hops: Int,
        path: List<String>,
        difficulty: String,
        wiki: String = "namu",
        dayNum: Int? = null
    ): Pair<Boolean, Int?> { // (success, rank)
        return try {
            val json = JSONObject().apply {
                put("nickname", nickname)
                put("start", start)
                put("goal", goal)
                put("elapsed_ms", elapsedMs)
                put("hops", hops)
                put("path", org.json.JSONArray(path))
                put("difficulty", difficulty)
                put("wiki", wiki)
                if (dayNum != null) put("day_num", dayNum)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$BASE_URL/api/ranking")
                .post(body)
                .build()
            val resp = client.newCall(req).execute()
            val d = JSONObject(resp.body?.string() ?: "{}")
            if (!d.optBoolean("ok", false)) return Pair(false, null)

            // 내 순위 조회
            val id = d.optInt("id", -1)
            val rankReq = Request.Builder()
                .url("$BASE_URL/api/ranking?wiki=$wiki&difficulty=$difficulty&limit=50")
                .build()
            val rankBody = JSONObject(client.newCall(rankReq).execute().body?.string() ?: "{}")
            val rankings = rankBody.optJSONArray("rankings")
            var rank: Int? = null
            if (rankings != null && id > 0) {
                for (i in 0 until rankings.length()) {
                    if (rankings.getJSONObject(i).optInt("id") == id) {
                        rank = i + 1
                        break
                    }
                }
            }
            Pair(true, rank)
        } catch (e: Exception) {
            Pair(false, null)
        }
    }

    /** POST /api/challenge → short URL */
    fun createChallenge(start: String, goal: String, wiki: String, hops: Int, ms: Long): String? {
        return try {
            val json = JSONObject().apply {
                put("start", start); put("goal", goal); put("wiki", wiki)
                put("hops", hops); put("ms", ms)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$BASE_URL/api/challenge").post(body).build()
            val d = JSONObject(client.newCall(req).execute().body?.string() ?: "{}")
            val code = d.optString("code", "")
            if (code.isNotEmpty()) "$BASE_URL/go/$code" else null
        } catch (e: Exception) { null }
    }

    /** GET /api/ranking */
    fun getRanking(wiki: String = "namu", difficulty: String = "", limit: Int = 20): List<RankEntry> {
        return try {
            val url = "$BASE_URL/api/ranking?wiki=$wiki&limit=$limit" +
                if (difficulty.isNotEmpty()) "&difficulty=$difficulty" else ""
            val req = Request.Builder().url(url).build()
            val body = JSONObject(client.newCall(req).execute().body?.string() ?: "{}")
            val arr = body.optJSONArray("rankings") ?: return emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val pathArr = o.optJSONArray("path")
                val path = if (pathArr != null)
                    (0 until pathArr.length()).map { pathArr.getString(it) }
                else emptyList()
                RankEntry(
                    rank = i + 1,
                    nickname = o.optString("nickname", "?"),
                    start = o.optString("start_page", o.optString("start", "")),
                    goal = o.optString("goal_page", o.optString("goal", "")),
                    elapsedMs = o.optLong("elapsed_ms", 0),
                    hops = o.optInt("hops", 0),
                    difficulty = o.optString("difficulty", ""),
                    path = path
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
