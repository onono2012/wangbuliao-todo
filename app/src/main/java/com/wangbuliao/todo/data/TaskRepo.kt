package com.wangbuliao.todo.data

import com.wangbuliao.todo.AppCtx
import com.wangbuliao.todo.reminder.AlarmScheduler
import com.wangbuliao.todo.util.RepeatRule
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
        val id = if (t.id <= 0) db.insert(t) else {
            db.update(t)
            t.id
        }
        widgetRefresh()
        id
    }

    suspend fun delete(id: Long) {
        withContext(Dispatchers.IO) { db.delete(id) }
        widgetRefresh()
    }

    /**
     * 切换已办状态。重复任务（repeat>0 且有提醒时间）勾选完成时不进已办，
     * 自动把提醒时间滚动到下一周期（保持未完成）并重排闹钟。
     * @return 滚动后的新任务状态；普通完成/取消完成返回 null
     */
    suspend fun setDone(id: Long, done: Boolean): Task? {
        val now = System.currentTimeMillis()
        val rolled = withContext(Dispatchers.IO) {
            if (done) {
                val t = db.getTask(id)
                val next = if (t != null) RepeatRule.next(t.remindAt, t.repeat, now) else 0L
                if (t != null && next > 0) {
                    db.rollRepeat(id, next, now)
                    t.copy(done = false, remindAt = next, reminded = false, updatedAt = now)
                } else {
                    db.setDone(id, true, now)
                    null
                }
            } else {
                db.setDone(id, false, now)
                null
            }
        }
        // 统一闹钟管理：滚动后排下一期；普通完成后取消闹钟（schedule 对 done/无提醒任务内部执行 cancel）
        try {
            val latest = rolled ?: withContext(Dispatchers.IO) { db.getTask(id) }
            latest?.let { AlarmScheduler.schedule(AppCtx.app, it) }
        } catch (_: Exception) {
        }
        widgetRefresh()
        return rolled
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
    /** 随手记·媒体（拍照/相册/录音）：悬浮窗透明页调用 */
    suspend fun quickMedia(
        title: String,
        note: String = "",
        images: List<String> = emptyList(),
        audioPath: String = "",
        audioDur: Long = 0
    ): Long = withContext(Dispatchers.IO) {
        db.addCategory(Task.QUICK_CATEGORY)
        val now = System.currentTimeMillis()
        db.insert(
            Task(
                title = title,
                note = note,
                category = Task.QUICK_CATEGORY,
                createdAt = now,
                updatedAt = now,
                images = images,
                audioPath = audioPath,
                audioDur = audioDur
            )
        ).also { widgetRefresh() }
    }

    /** v1.5.5 快速记录：category=空则用「随手记」；remindAt>0 同时排闹钟 */
    suspend fun quickNote(
        text: String,
        audioPath: String = "",
        audioDur: Long = 0,
        images: List<String> = emptyList(),
        category: String = "",
        remindAt: Long = 0,
        urgent: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        // 无文字但有附件时也允许保存（标题回退）
        val firstLine = trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?: when {
                audioPath.isNotEmpty() -> "语音速记"
                images.isNotEmpty() -> "图片速记"
                else -> "随手记"
            }
        val title = if (firstLine.length > 30) firstLine.take(30) + "…" else firstLine
        val cat = category.ifBlank { Task.QUICK_CATEGORY }
        db.addCategory(cat)
        val now = System.currentTimeMillis()
        db.insert(
            Task(
                title = title,
                note = trimmed,
                category = cat,
                urgent = urgent,
                remindAt = remindAt,
                createdAt = now,
                updatedAt = now,
                audioPath = audioPath,
                audioDur = audioDur,
                images = images
            )
        ).also { widgetRefresh() }
    }

    /** 数据变化后刷新桌面小组件（失败绝不影响主流程） */
    private fun widgetRefresh() {
        try {
            com.wangbuliao.todo.widget.WblWidgetProvider.updateAll(AppCtx.app)
        } catch (_: Exception) {
        }
    }
}
