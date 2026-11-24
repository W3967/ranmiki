package com.lanweixi.core.data

import com.lanweixi.core.data.entity.KvEntity

/**
 * 空库首启：写默认角色与初始化标记。
 * 幂等——重复调用不会重复插入，符合「空库首启无残留状态」的验收。
 */
object DatabaseInitializer {
    const val KEY_SCHEMA_READY = "db.initialized"

    suspend fun seedIfNeeded(db: LanWeixiDatabase) {
        if (db.characterDao().count() == 0) {
            db.characterDao().upsert(DefaultData.defaultCharacter())
        }
        if (db.kvDao().get(KEY_SCHEMA_READY) == null) {
            db.kvDao().put(KvEntity(KEY_SCHEMA_READY, "1"))
        }
    }
}
