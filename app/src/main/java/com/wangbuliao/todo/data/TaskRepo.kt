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

    /** 贪睡改期：重设提醒时间并清除已提醒标记 */
    suspend fun setRemindAt(id: Long, remindAt: Long) {
        withContext(Dispatchers.IO) { db.setRemindAt(id, remindAt, System.currentTimeMillis()) }
    }

    suspend fun pendingReminders(): List<Task> =
        withContext(Dispatchers.IO) { db.pendingReminders() }

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) { db.pendingCount() }

    suspend fun addCategory(name: String) {
        withContext(Dispatchers.IO) { db.addCategory(name) }
    }

    /**
     * 随手记：文本直接入库（分类「随手记」）。
     * 标题=首行前 30 字，备注=全文。App 内对话框与悬浮窗面板共用。
     */
    suspend fun quickNote(text: String): Long = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        val firstLine = trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "随手记"
        val title = if (firstLine.length > 30) firstLine.take(30) + "…" else firstLine
        db.addCategory(Task.QUICK_CATEGORY)
        val now = System.currentTimeMillis()
        db.insert(
            Task(
                title = title,
                note = trimmed,
                category = Task.QUICK_CATEGORY,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
