package com.lanweixi.core.data.agent

import android.content.Context
import com.lanweixi.domain.prompt.AgentProfile
import com.lanweixi.domain.prompt.AgentProviders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * P198：DataStore 委托必须是**进程级单例**。
 *
 * 原来写在类里（`private val Context.dataStore by preferencesDataStore(...)`），
 * 每 `AgentStore(context)` 一次就新建一个 DataStore；群聊页同时打开群列表与群聊时，
 * 第二个实例读同一文件直接抛
 * `IllegalStateException: There are multiple DataStores active for the same file`（已实测崩过）。
 * 放到文件顶层（委托对象只创建一次）才是官方推荐写法。
 */
private val Context.agentsDataStore by preferencesDataStore(name = "lanweixi_agents")

/**
 * P194：工具人 agent 的存储。
 *
 * 用 DataStore 存 JSON，不开新表——数量少、结构简单，
 * 不值得为它写 Room 迁移。
 */
class AgentStore(private val context: Context) {

    private val store get() = context.agentsDataStore

    fun list(): List<AgentProfile> = runBlocking {
        val raw = store.data.first()[AGENTS].orEmpty()
        decode(raw)
    }

    fun upsert(profile: AgentProfile) = runBlocking {
        val current = list().toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        store.edit { it[AGENTS] = encode(current) }
    }

    fun delete(id: String) = runBlocking {
        val next = list().filterNot { it.id == id }
        store.edit { it[AGENTS] = encode(next) }
    }

    fun byId(id: String): AgentProfile? = list().firstOrNull { it.id == id }

    // ── 群会话 ──

    fun listGroups(): List<com.lanweixi.domain.prompt.GroupSession> = runBlocking {
        val raw = store.data.first()[GROUPS].orEmpty()
        decodeGroups(raw)
    }

    fun upsertGroup(group: com.lanweixi.domain.prompt.GroupSession) = runBlocking {
        val current = listGroups().toMutableList()
        val idx = current.indexOfFirst { it.id == group.id }
        if (idx >= 0) current[idx] = group else current.add(group)
        store.edit { it[GROUPS] = encodeGroups(current) }
    }

    fun deleteGroup(id: String) = runBlocking {
        val next = listGroups().filterNot { it.id == id }
        store.edit { it[GROUPS] = encodeGroups(next) }
    }

    private fun encodeGroups(list: List<com.lanweixi.domain.prompt.GroupSession>): String {
        val arr = JSONArray()
        list.forEach { g ->
            arr.put(
                JSONObject().apply {
                    put("id", g.id)
                    put("title", g.title)
                    put("createdAt", g.createdAt)
                    put("memberIds", JSONArray(g.memberIds))
                },
            )
        }
        return arr.toString()
    }

    private fun decodeGroups(raw: String): List<com.lanweixi.domain.prompt.GroupSession> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val members = o.optJSONArray("memberIds")
                com.lanweixi.domain.prompt.GroupSession(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    memberIds = if (members == null) emptyList() else
                        (0 until members.length()).map { members.getString(it) },
                    createdAt = o.optLong("createdAt"),
                )
            }.filter { it.id.isNotBlank() }
        }.getOrElse { emptyList() }
    }

    // ── JSON ──

    private fun encode(list: List<AgentProfile>): String {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject().apply {
                    put("id", a.id)
                    put("name", a.name)
                    put("avatarUri", a.avatarUri ?: "")
                    put("provider", a.provider)
                    put("apiKey", a.apiKey)
                    put("baseUrl", a.baseUrl)
                    put("model", a.model)
                    put("workPrompt", a.workPrompt)
                    put("color", a.color)
                },
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String): List<AgentProfile> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AgentProfile(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    avatarUri = o.optString("avatarUri").ifBlank { null },
                    provider = o.optString("provider", AgentProviders.DEEPSEEK),
                    apiKey = o.optString("apiKey"),
                    baseUrl = o.optString("baseUrl", AgentProviders.DEFAULT_BASE_URL),
                    model = o.optString("model", AgentProviders.DEFAULT_MODEL),
                    workPrompt = o.optString("workPrompt"),
                    color = o.optString("color", AgentProviders.COLORS.first()),
                )
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        }.getOrElse { emptyList() }
    }

    private companion object {
        val AGENTS = stringPreferencesKey("agents_json")
        val GROUPS = stringPreferencesKey("groups_json")
    }
}
