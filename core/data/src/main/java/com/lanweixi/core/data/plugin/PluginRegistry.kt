package com.lanweixi.core.data.plugin

import android.content.Context
import android.content.SharedPreferences
import com.lanweixi.domain.plugin.PluginManifest
import com.lanweixi.domain.plugin.PluginPermissions
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** 已安装插件（manifest 每次从目录现读，避免缓存过期）。 */
data class InstalledPlugin(
    val manifest: PluginManifest,
    val dirPath: String,
    val installedAt: Long,
    val enabled: Boolean,
    val grants: Set<String>,
) {
    val permissionsGranted: Boolean
        get() = manifest.permissions.all { it.permissionId in grants }
}

/**
 * 插件注册表：已安装清单 + 授权状态。
 *
 * P1 只做**登记与授权**，不负责加载插件代码（那属于后续的宿主层）。
 * 插件目录丢失时自动跳过，不阻塞列表。
 */
class PluginRegistry(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("lanweixi_plugins", Context.MODE_PRIVATE)

    private val grantsPrefs: SharedPreferences =
        appContext.getSharedPreferences("lanweixi_plugin_grants", Context.MODE_PRIVATE)

    /** 所有已安装插件（按安装时间倒序）。 */
    fun installed(): List<InstalledPlugin> {
        val raw = prefs.getString(KEY_INSTALLED, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<InstalledPlugin>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            val dirPath = obj.optString("dirPath", "")
            val installedAt = obj.optLong("installedAt", 0L)
            val enabled = obj.optBoolean("enabled", true)
            val dir = File(dirPath)
            if (id.isBlank() || !dir.isDirectory) {
                // 目录没了：跳过（下次 save 时自然清理）
                continue
            }
            val parsed = PluginManifestParser.parseDirectory(dir)
            if (parsed !is PluginManifestParser.Result.Success || !parsed.isValid) continue
            out += InstalledPlugin(
                manifest = parsed.manifest,
                dirPath = dirPath,
                installedAt = installedAt,
                enabled = enabled,
                grants = grantsOf(id),
            )
        }
        return out.sortedByDescending { it.installedAt }
    }

    /** 从目录安装（要求目录内含 `plugin.manifest.json`）。 */
    fun install(dir: File): Result<String> {
        val parsed = PluginManifestParser.parseDirectory(dir)
        return when (parsed) {
            is PluginManifestParser.Result.Failure ->
                Result.failure(IllegalArgumentException(parsed.errors.joinToString("；")))
            is PluginManifestParser.Result.Success -> {
                if (!parsed.isValid) {
                    Result.failure(IllegalArgumentException(parsed.errors.joinToString("；")))
                } else {
                    val manifest = parsed.manifest
                    val existing = installed().filterNot { it.manifest.id == manifest.id }
                    val entry = JSONObject()
                        .put("id", manifest.id)
                        .put("dirPath", dir.absolutePath)
                        .put("installedAt", System.currentTimeMillis())
                        .put("enabled", true)
                    val arr = JSONArray()
                    existing.forEach {
                        arr.put(
                            JSONObject()
                                .put("id", it.manifest.id)
                                .put("dirPath", it.dirPath)
                                .put("installedAt", it.installedAt)
                                .put("enabled", it.enabled),
                        )
                    }
                    arr.put(entry)
                    prefs.edit().putString(KEY_INSTALLED, arr.toString()).apply()
                    Result.success(manifest.id)
                }
            }
        }
    }

    /**
     * 卸载。
     *
     * @param deleteSandbox true = 连同插件目录一起删；false = 只取消登记
     */
    fun uninstall(id: String, deleteSandbox: Boolean) {
        val list = installed()
        val target = list.firstOrNull { it.manifest.id == id }
        val arr = JSONArray()
        list.filterNot { it.manifest.id == id }.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.manifest.id)
                    .put("dirPath", it.dirPath)
                    .put("installedAt", it.installedAt)
                    .put("enabled", it.enabled),
            )
        }
        prefs.edit().putString(KEY_INSTALLED, arr.toString()).apply()
        grantsPrefs.edit().remove(id).apply()
        if (deleteSandbox && target != null) {
            runCatching { File(target.dirPath).deleteRecursively() }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val list = installed()
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.manifest.id)
                    .put("dirPath", it.dirPath)
                    .put("installedAt", it.installedAt)
                    .put("enabled", if (it.manifest.id == id) enabled else it.enabled),
            )
        }
        prefs.edit().putString(KEY_INSTALLED, arr.toString()).apply()
    }

    // ───────────── 授权 ─────────────

    fun grantsOf(id: String): Set<String> {
        val raw = grantsPrefs.getString(id, null) ?: return emptySet()
        return raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** 授权（只接受白名单内的权限）。返回是否成功。 */
    fun grant(id: String, permissionId: String): Boolean {
        if (!PluginPermissions.isKnown(permissionId)) return false
        val next = grantsOf(id) + permissionId
        grantsPrefs.edit().putString(id, next.joinToString("\n")).apply()
        return true
    }

    fun revoke(id: String, permissionId: String) {
        val next = grantsOf(id) - permissionId
        if (next.isEmpty()) {
            grantsPrefs.edit().remove(id).apply()
        } else {
            grantsPrefs.edit().putString(id, next.joinToString("\n")).apply()
        }
    }

    fun isGranted(id: String, permissionId: String): Boolean =
        permissionId in grantsOf(id)

    companion object {
        private const val KEY_INSTALLED = "installed"
    }
}
