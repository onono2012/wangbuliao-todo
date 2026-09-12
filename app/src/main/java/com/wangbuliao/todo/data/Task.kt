package com.wangbuliao.todo.data

/** 待办事项实体 */
data class Task(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val category: String = DEFAULT_CATEGORY,
    /** 紧急事项（急需办理） */
    val urgent: Boolean = false,
    /** 已办/待办 */
    val done: Boolean = false,
    /** 提醒时间（epoch millis，0=不提醒） */
    val remindAt: Long = 0,
    /** 提醒是否已发出（防重复通知） */
    val reminded: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    companion object {
        const val DEFAULT_CATEGORY = "工作"
    }
}
