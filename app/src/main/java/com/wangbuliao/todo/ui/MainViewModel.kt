package com.wangbuliao.todo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wangbuliao.todo.data.Task
import com.wangbuliao.todo.data.TaskRepo
import com.wangbuliao.todo.media.ImageStore
import com.wangbuliao.todo.reminder.AlarmScheduler
import com.wangbuliao.todo.reminder.PinNotifService
import com.wangbuliao.todo.update.Updater
import com.wangbuliao.todo.util.RepeatRule
import com.wangbuliao.todo.util.TimeFmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class StatusFilter(val label: String) {
    TODO("待办"), DONE("已办"), ALL("全部")
}

sealed interface Screen {
    object List : Screen
    object Edit : Screen
    object Settings : Screen
    object Calendar : Screen
    object Theme : Screen
}

/** 编辑页草稿（单一数据源，避免本地状态同步问题） */
data class EditDraft(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val category: String = Task.DEFAULT_CATEGORY,
    val urgent: Boolean = false,
    val remindAt: Long = 0,
    val done: Boolean = false,
    val pinned: Boolean = false,
    val audioPath: String = "",
    val audioDur: Long = 0,
    val images: List<String> = emptyList(),
    val repeat: Int = 0
)

data class UiState(
    val loading: Boolean = true,
    val allTasks: List<Task> = emptyList(),
    val categories: List<String> = emptyList(),
    val status: StatusFilter = StatusFilter.TODO,
    val category: String? = null,
    val urgentOnly: Boolean = false,
    val search: String = ""
) {
    /** 按当前筛选条件排序后的可见任务：
     *  搜索匹配 → 待办在前 → 手动置顶 → 紧急置顶 → 有提醒按时间升序 → 创建时间降序；
     *  已办按完成时间降序 */
    val visible: List<Task>
        get() {
            val q = search.trim().lowercase()
            return allTasks.asSequence()
                .filter {
                    when (status) {
                        StatusFilter.TODO -> !it.done
                        StatusFilter.DONE -> it.done
                        StatusFilter.ALL -> true
                    }
                }
                .filter { category == null || it.category == category }
                .filter { !urgentOnly || it.urgent }
                .filter {
                    q.isEmpty() ||
                        it.title.lowercase().contains(q) ||
                        it.note.lowercase().contains(q) ||
                        it.category.lowercase().contains(q)
                }
                .sortedWith(
                    Comparator<Task> { a, b ->
                        if (a.done != b.done) return@Comparator if (a.done) 1 else -1
                        if (!a.done) {
                            if (a.pinned != b.pinned) return@Comparator if (a.pinned) -1 else 1
                            if (a.urgent != b.urgent) return@Comparator if (a.urgent) -1 else 1
                            val ra = if (a.remindAt > 0) a.remindAt else Long.MAX_VALUE
                            val rb = if (b.remindAt > 0) b.remindAt else Long.MAX_VALUE
                            if (ra != rb) return@Comparator ra.compareTo(rb)
                        } else if (a.updatedAt != b.updatedAt) {
                            return@Comparator b.updatedAt.compareTo(a.updatedAt)
                        }
                        b.createdAt.compareTo(a.createdAt)
                    }
                )
                .toList()
        }
}

/** 数据备份/恢复 UI 状态 */
data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.List)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _draft = MutableStateFlow(EditDraft())
    val draft: StateFlow<EditDraft> = _draft.asStateFlow()

    /** 打开编辑页时的草稿快照：判定是否有修改 + 放弃/保存时差量清理媒体文件 */
    private var draftOriginal: EditDraft = EditDraft()

    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg.asStateFlow()

    fun consumeMsg() {
        _msg.value = null
    }

    // ── 数据备份/恢复（123 网盘 WebDAV，手动触发）──
    private val _backup = MutableStateFlow(BackupUiState())
    val backup: StateFlow<BackupUiState> = _backup.asStateFlow()

    fun doBackup() {
        if (_backup.value.busy) return
        _backup.value = BackupUiState(busy = true, message = "备份中…")
        viewModelScope.launch {
            val r = com.wangbuliao.todo.util.BackupManager.backup(ctx)
            _backup.value = BackupUiState(busy = false, message = r)
        }
    }

    fun doRestore() {
        if (_backup.value.busy) return
        _backup.value = BackupUiState(busy = true, message = "恢复中…完成后应用将重启")
        viewModelScope.launch {
            val r = com.wangbuliao.todo.util.BackupManager.restore(ctx)
            _backup.value = BackupUiState(busy = false, message = r)
        }
    }

    /** 供 UI 组件上抛一次性提示（如权限被拒、图片超限） */
    fun notifyMsg(m: String) {
        _msg.value = m
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val tasks = TaskRepo.tasks()
                val cats = TaskRepo.categories()
                _ui.value = _ui.value.copy(loading = false, allTasks = tasks, categories = cats)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false)
                _msg.value = "读取数据失败：${e.message}"
            }
        }
    }

    fun setStatus(s: StatusFilter) {
        _ui.value = _ui.value.copy(status = s)
    }

    fun setCategoryFilter(c: String?) {
        _ui.value = _ui.value.copy(category = c)
    }

    fun toggleUrgentOnly() {
        _ui.value = _ui.value.copy(urgentOnly = !_ui.value.urgentOnly)
    }

    fun setSearch(q: String) {
        _ui.value = _ui.value.copy(search = q)
    }

    fun openNew() {
        _draft.value = EditDraft(
            category = _ui.value.categories.firstOrNull() ?: Task.DEFAULT_CATEGORY
        )
        draftOriginal = _draft.value
        _screen.value = Screen.Edit
    }

    fun openEdit(id: Long) {
        viewModelScope.launch {
            val t = TaskRepo.get(id)
            _draft.value = t?.let {
                EditDraft(
                    it.id, it.title, it.note, it.category, it.urgent, it.remindAt, it.done,
                    it.pinned, it.audioPath, it.audioDur, it.images, it.repeat
                )
            } ?: EditDraft()
            draftOriginal = _draft.value
            _screen.value = Screen.Edit
        }
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun openCalendar() {
        _screen.value = Screen.Calendar
    }

    fun goScreen(sc: Screen) {
        _screen.value = sc
    }

    /** v1.5.0 桌面小组件「+」：打开新建任务编辑页（空白草稿） */
    fun openNewTask() {
        _draft.value = EditDraft(
            category = _ui.value.categories.firstOrNull() ?: Task.DEFAULT_CATEGORY
        )
        draftOriginal = _draft.value
        _screen.value = Screen.Edit
    }

    /** 日历页选中某天「新增事项」：预填提醒时间为当天 09:00 */
    fun openNewForDate(dayStartMillis: Long) {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = dayStartMillis
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        _draft.value = EditDraft(
            category = _ui.value.categories.firstOrNull() ?: Task.DEFAULT_CATEGORY,
            remindAt = cal.timeInMillis
        )
        draftOriginal = _draft.value
        _screen.value = Screen.Edit
    }

    fun backList() {
        _screen.value = Screen.List
    }

    /** 草稿相对打开编辑页时是否有修改（返回时决定是否弹确认） */
    fun isDraftDirty(): Boolean = _draft.value != draftOriginal

    /** 放弃草稿返回列表：删除本次会话新增、未被任务引用的图片/录音，防孤儿文件 */
    fun discardDraft() {
        deleteSessionMedia(_draft.value)
        backList()
    }

    /** 删除本次会话新增且仍在草稿中的媒体文件（放弃/删任务时防孤儿） */
    private fun deleteSessionMedia(d: EditDraft) {
        val o = draftOriginal
        d.images.forEach { p -> if (p !in o.images) ImageStore.delete(p) }
        if (d.audioPath.isNotEmpty() && d.audioPath != o.audioPath) {
            try { File(d.audioPath).delete() } catch (_: Exception) {}
        }
    }

    /** 移除草稿图片：会话新增文件立即删磁盘；原任务附件延迟到保存/放弃时处理（防放弃后引用损坏） */
    fun removeDraftImage(path: String) {
        val d = _draft.value
        if (path !in draftOriginal.images) ImageStore.delete(path)
        _draft.value = d.copy(images = d.images - path)
    }

    /** 移除草稿录音：同上 */
    fun removeDraftAudio() {
        val d = _draft.value
        if (d.audioPath.isNotEmpty() && d.audioPath != draftOriginal.audioPath) {
            try { File(d.audioPath).delete() } catch (_: Exception) {}
        }
        _draft.value = d.copy(audioPath = "", audioDur = 0)
    }

    /** 保存成功后：差量删除被移除的旧图片/录音文件（编辑期间只动草稿不动磁盘） */
    private fun cleanupRemovedMedia(newImages: List<String>, newAudio: String) {
        val o = draftOriginal
        o.images.forEach { p -> if (p !in newImages) ImageStore.delete(p) }
        if (o.audioPath.isNotEmpty() && o.audioPath != newAudio) {
            try { File(o.audioPath).delete() } catch (_: Exception) {}
        }
    }

    fun updateDraft(f: (EditDraft) -> EditDraft) {
        _draft.value = f(_draft.value)
    }

    /** 随手记：文本直接入库（分类「随手记」），列表页对话框与悬浮窗共用逻辑 */
    fun quickNote(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            try {
                TaskRepo.quickNote(t)
                refresh()
                PinNotifService.refresh(ctx)
com.wangbuliao.todo.reminder.KeepAliveService.refresh(ctx)
                _msg.value = "已记入「随手记」✍"
            } catch (e: Exception) {
                _msg.value = "保存失败：${e.message}"
            }
        }
    }

    fun saveDraft() {
        val d = _draft.value
        val title = d.title.trim()
        if (title.isEmpty()) {
            _msg.value = "请填写要做的事情"
            return
        }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val old = if (d.id > 0) TaskRepo.get(d.id) else null
                val task = Task(
                    id = d.id,
                    title = title,
                    note = d.note.trim(),
                    category = d.category,
                    urgent = d.urgent,
                    done = old?.done ?: false,
                    remindAt = d.remindAt,
                    reminded = if (old != null && old.remindAt == d.remindAt) old.reminded else false,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                    pinned = d.pinned,
                    audioPath = d.audioPath,
                    audioDur = d.audioDur,
                    images = d.images,
                    repeat = if (d.remindAt > 0) d.repeat else 0
                )
                TaskRepo.save(task)
                // 保存成功后差量清理：删除编辑期间被移除的原任务附件文件
                cleanupRemovedMedia(d.images, d.audioPath)
                draftOriginal = d
                if (task.done || task.remindAt <= 0) {
                    AlarmScheduler.cancel(ctx, task)
                } else {
                    AlarmScheduler.schedule(ctx, task)
                }
                refresh()
                PinNotifService.refresh(ctx)
com.wangbuliao.todo.reminder.KeepAliveService.refresh(ctx)
                _screen.value = Screen.List
                _msg.value = if (d.id > 0) "已保存" else "已添加"
            } catch (e: Exception) {
                _msg.value = "保存失败：${e.message}"
            }
        }
    }

    fun toggleDone(t: Task) {
        viewModelScope.launch {
            try {
                val nd = !t.done
                // 重复任务勾选完成会自动滚动到下一周期（TaskRepo 统一处理闹钟，勿在此 cancel）
                val rolled = TaskRepo.setDone(t.id, nd)
                // 震动反馈（受设置-震动开关控制）+ 明确提示去向
                if (rolled != null) {
                    com.wangbuliao.todo.util.Haptics.taskDone(ctx)
                    _msg.value = "「${t.title.ifEmpty { "未命名" }}」本期完成 ✓ " +
                        RepeatRule.label(t.repeat) + " " + TimeFmt.remind(rolled.remindAt) + " 再次提醒"
                } else if (nd) {
                    com.wangbuliao.todo.util.Haptics.taskDone(ctx)
                    _msg.value = "已完成「${t.title.ifEmpty { "未命名" }}」✓ 移入已办"
                } else {
                    com.wangbuliao.todo.util.Haptics.taskUndone(ctx)
                    _msg.value = "已移回待办"
                }
                refresh()
                PinNotifService.refresh(ctx)
com.wangbuliao.todo.reminder.KeepAliveService.refresh(ctx)
            } catch (e: Exception) {
                _msg.value = "操作失败：${e.message}"
            }
        }
    }

    /** 列表页长按置顶/取消置顶 */
    fun togglePinned(t: Task) {
        viewModelScope.launch {
            try {
                TaskRepo.save(t.copy(pinned = !t.pinned, updatedAt = System.currentTimeMillis()))
                refresh()
                _msg.value = if (!t.pinned) "已置顶到最上方 📌" else "已取消置顶"
            } catch (e: Exception) {
                _msg.value = "操作失败：${e.message}"
            }
        }
    }

    /** 删除任务并清理其录音/图片附件文件 */
    private fun cleanupMedia(t: Task?) {
        if (t == null) return
        if (t.audioPath.isNotEmpty()) {
            try {
                File(t.audioPath).delete()
            } catch (_: Exception) {
            }
        }
        t.images.forEach { p -> ImageStore.delete(p) }
    }

    fun deleteTask(t: Task) {
        viewModelScope.launch {
            try {
                val full = TaskRepo.get(t.id)
                TaskRepo.delete(t.id)
                cleanupMedia(full)
                AlarmScheduler.cancel(ctx, t)
                refresh()
                PinNotifService.refresh(ctx)
com.wangbuliao.todo.reminder.KeepAliveService.refresh(ctx)
                _msg.value = "已删除"
            } catch (e: Exception) {
                _msg.value = "删除失败：${e.message}"
            }
        }
    }

    fun deleteDraftTask() {
        val d = _draft.value
        if (d.id <= 0) {
            backList()
            return
        }
        viewModelScope.launch {
            try {
                val full = TaskRepo.get(d.id)
                TaskRepo.delete(d.id)
                cleanupMedia(full)
                // 本次会话新增（尚未入库）的图片/录音同样要清理，防孤儿文件
                val origImages = full?.images ?: emptyList()
                d.images.forEach { p -> if (p !in origImages) ImageStore.delete(p) }
                if (d.audioPath.isNotEmpty() && d.audioPath != (full?.audioPath ?: "")) {
                    try { File(d.audioPath).delete() } catch (_: Exception) {}
                }
                AlarmScheduler.cancel(ctx, Task(id = d.id))
                refresh()
                PinNotifService.refresh(ctx)
com.wangbuliao.todo.reminder.KeepAliveService.refresh(ctx)
                _screen.value = Screen.List
                _msg.value = "已删除"
            } catch (e: Exception) {
                _msg.value = "删除失败：${e.message}"
            }
        }
    }

    fun addCategory(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            try {
                TaskRepo.addCategory(n)
                refresh()
                _draft.value = _draft.value.copy(category = n)
            } catch (e: Exception) {
                _msg.value = "添加分类失败：${e.message}"
            }
        }
    }

    fun checkUpdate(silent: Boolean = false) {
        Updater.check(ctx, silent)
    }

    fun downloadUpdate(info: Updater.Info) {
        Updater.download(ctx, info)
    }
}
