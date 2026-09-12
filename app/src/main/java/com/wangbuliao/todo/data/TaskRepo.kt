package com.wangbuliao.todo.data

import com.wangbuliao.todo.AppCtx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 数据仓库：所有 SQLite 操作都在 IO 线程执行 */
object TaskRepo {
    private val db get() = DbHelper.get(AppCtx.app)

    suspend fun tasks(): List<Task> = withContext(Dispatchers.IO) { db.queryTasks() }

    suspend fun get(id: Long): Task? = withContext(Dispatchers.IO) { db.getTask(id) }

    suspend fun categories(): List<String> = withContext(Dispatchers.IO) { db.queryCategories() }

    /** 返回任务 id（新插入返回自增 id，更新返回原 id） */
    suspend fun save(t: Task): Long = withContext(Dispatchers.IO) {
        if (t.id <= 0) db.insert(t) else {
            db.update(t)
            t.id
        }
    }

    suspend fun delete(id: Long) {
        withContext(Dispatchers.IO) { db.delete(id) }
    }

    suspend fun setDone(id: Long, done: Boolean) {
        withContext(Dispatchers.IO) { db.setDone(id, done, System.currentTimeMillis()) }
    }

    suspend fun markReminded(id: Long) {
        withContext(Dispatchers.IO) { db.markReminded(id) }
    }

    suspend fun pendingReminders(): List<Task> =
        withContext(Dispatchers.IO) { db.pendingReminders() }

    suspend fun addCategory(name: String) {
        withContext(Dispatchers.IO) { db.addCategory(name) }
    }
}
