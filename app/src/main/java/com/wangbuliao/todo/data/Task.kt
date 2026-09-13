package com.wangbuliao.todo.data

/** 记事/待办实体 */
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
    /** 重复规则：0=不重复 1=每天 2=每周 3=每月（见 RepeatRule，仅在有提醒时间时生效） */
    val repeat: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /** 置顶（列表最上方） */
    val pinned: Boolean = false,
    /** 录音文件路径（""=无录音） */
    val audioPath: String = "",
    /** 录音时长（毫秒） */
    val audioDur: Long = 0,
    /** 图片文件路径列表 */
    val images: List<String> = emptyList()
) {
    companion object {
        const val DEFAULT_CATEGORY = "工作"
        const val QUICK_CATEGORY = "随手记"
    }
}
