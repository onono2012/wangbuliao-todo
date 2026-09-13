package com.wangbuliao.todo.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TASKS)
        db.execSQL(CREATE_CATEGORIES)
        presetCategories(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 数据兼容规则：升级必须 try-catch 兜底，保全旧数据，失败时降级兼容启动，严禁闪退
        try {
            if (oldVersion < 2) {
                // v2: 记事本化——置顶/录音/图片
                safeExec(db, "ALTER TABLE tasks ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                safeExec(db, "ALTER TABLE tasks ADD COLUMN audio_path TEXT NOT NULL DEFAULT ''")
                safeExec(db, "ALTER TABLE tasks ADD COLUMN audio_dur INTEGER NOT NULL DEFAULT 0")
                safeExec(db, "ALTER TABLE tasks ADD COLUMN images TEXT NOT NULL DEFAULT ''")
            }
            if (oldVersion < 3) {
                // v3: 重复任务（0=不重复 1=每天 2=每周 3=每月）
                safeExec(db, "ALTER TABLE tasks ADD COLUMN repeat INTEGER NOT NULL DEFAULT 0")
            }
            db.execSQL(CREATE_TASKS)
            db.execSQL(CREATE_CATEGORIES)
            presetCategories(db)
        } catch (e: Exception) {
            Log.e(TAG, "onUpgrade failed, keep old data", e)
        }
    }

    /** 单条 DDL 容错执行（列已存在等场景不中断升级） */
    private fun safeExec(db: SQLiteDatabase, sql: String) {
        try {
            db.execSQL(sql)
        } catch (e: Exception) {
            Log.w(TAG, "safeExec skipped: $sql (${e.message})")
        }
    }

    private fun presetCategories(db: SQLiteDatabase) {
        PRESET.forEachIndexed { i, name ->
            val v = ContentValues().apply {
                put("name", name)
                put("sort", i)
            }
            db.insertWithOnConflict("categories", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun queryTasks(): List<Task> {
        val out = mutableListOf<Task>()
        readableDatabase.query("tasks", null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) out.add(c.toTask())
        }
        return out
    }

    fun getTask(id: Long): Task? {
        readableDatabase.query("tasks", null, "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            return if (c.moveToFirst()) c.toTask() else null
        }
    }

    fun insert(t: Task): Long = writableDatabase.insert("tasks", null, t.toValues())

    fun update(t: Task): Int =
        writableDatabase.update("tasks", t.toValues(), "id=?", arrayOf(t.id.toString()))

    fun delete(id: Long): Int =
        writableDatabase.delete("tasks", "id=?", arrayOf(id.toString()))

    fun setDone(id: Long, done: Boolean, now: Long): Int {
        val v = ContentValues().apply {
            put("done", if (done) 1 else 0)
            put("updated_at", now)
        }
        return writableDatabase.update("tasks", v, "id=?", arrayOf(id.toString()))
    }

    fun markReminded(id: Long): Int {
        val v = ContentValues().apply { put("reminded", 1) }
        return writableDatabase.update("tasks", v, "id=?", arrayOf(id.toString()))
    }

    fun setRemindAt(id: Long, remindAt: Long, now: Long): Int {
        val v = ContentValues().apply {
            put("remind_at", remindAt)
            put("reminded", 0)
            put("updated_at", now)
        }
        return writableDatabase.update("tasks", v, "id=?", arrayOf(id.toString()))
    }

    /** 重复任务完成本期：不进已办，提醒时间滚动到下一周期并清除已提醒标记 */
    fun rollRepeat(id: Long, nextRemindAt: Long, now: Long): Int {
        val v = ContentValues().apply {
            put("done", 0)
            put("remind_at", nextRemindAt)
            put("reminded", 0)
            put("updated_at", now)
        }
        return writableDatabase.update("tasks", v, "id=?", arrayOf(id.toString()))
    }

    fun pendingReminders(): List<Task> = queryTasks().filter { !it.done && it.remindAt > 0 }

    fun pendingCount(): Int = queryTasks().count { !it.done }

    fun queryCategories(): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.query("categories", arrayOf("name"), null, null, null, null, "sort ASC, name ASC").use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    fun addCategory(name: String) {
        val nextSort = readableDatabase.rawQuery(
            "SELECT IFNULL(MAX(sort), -1) + 1 FROM categories", null
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        val v = ContentValues().apply {
            put("name", name)
            put("sort", nextSort)
        }
        writableDatabase.insertWithOnConflict("categories", null, v, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun Cursor.toTask() = Task(
        id = getLong(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")) ?: "",
        note = getString(getColumnIndexOrThrow("note")) ?: "",
        category = getString(getColumnIndexOrThrow("category")) ?: Task.DEFAULT_CATEGORY,
        urgent = getInt(getColumnIndexOrThrow("urgent")) == 1,
        done = getInt(getColumnIndexOrThrow("done")) == 1,
        remindAt = getLong(getColumnIndexOrThrow("remind_at")),
        reminded = getInt(getColumnIndexOrThrow("reminded")) == 1,
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
        pinned = colInt("pinned") == 1,
        audioPath = colStr("audio_path"),
        audioDur = colLong("audio_dur"),
        images = colStr("images").split("\n").filter { it.isNotBlank() },
        repeat = colInt("repeat")
    )

    /** 兼容读取：列不存在（极端降级场景）返回默认值，严禁闪退 */
    private fun Cursor.colInt(name: String): Int =
        try {
            val i = getColumnIndex(name)
            if (i < 0) 0 else getInt(i)
        } catch (e: Exception) {
            0
        }

    private fun Cursor.colLong(name: String): Long =
        try {
            val i = getColumnIndex(name)
            if (i < 0) 0L else getLong(i)
        } catch (e: Exception) {
            0L
        }

    private fun Cursor.colStr(name: String): String =
        try {
            val i = getColumnIndex(name)
            if (i < 0) "" else getString(i) ?: ""
        } catch (e: Exception) {
            ""
        }

    private fun Task.toValues() = ContentValues().apply {
        put("title", title)
        put("note", note)
        put("category", category)
        put("urgent", if (urgent) 1 else 0)
        put("done", if (done) 1 else 0)
        put("remind_at", remindAt)
        put("reminded", if (reminded) 1 else 0)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("pinned", if (pinned) 1 else 0)
        put("audio_path", audioPath)
        put("audio_dur", audioDur)
        put("images", images.joinToString("\n"))
        put("repeat", repeat)
    }

    companion object {
        private const val TAG = "WblDb"
        private const val DB_NAME = "wbl.db"
        private const val DB_VERSION = 3
        val PRESET = listOf("工作", "生活", "学习", Task.QUICK_CATEGORY, "其他")

        private const val CREATE_TASKS = """CREATE TABLE IF NOT EXISTS tasks(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            note TEXT NOT NULL DEFAULT '',
            category TEXT NOT NULL DEFAULT '工作',
            urgent INTEGER NOT NULL DEFAULT 0,
            done INTEGER NOT NULL DEFAULT 0,
            remind_at INTEGER NOT NULL DEFAULT 0,
            reminded INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL DEFAULT 0,
            pinned INTEGER NOT NULL DEFAULT 0,
            audio_path TEXT NOT NULL DEFAULT '',
            audio_dur INTEGER NOT NULL DEFAULT 0,
            images TEXT NOT NULL DEFAULT '',
            repeat INTEGER NOT NULL DEFAULT 0)"""

        private const val CREATE_CATEGORIES = """CREATE TABLE IF NOT EXISTS categories(
            name TEXT PRIMARY KEY,
            sort INTEGER NOT NULL DEFAULT 0)"""

        @Volatile
        private var instance: DbHelper? = null

        fun get(context: Context): DbHelper =
            instance ?: synchronized(this) {
                instance ?: DbHelper(context).also { instance = it }
            }
    }
}
