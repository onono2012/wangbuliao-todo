package com.wangbuliao.todo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wangbuliao.todo.data.Task
import com.wangbuliao.todo.data.TaskRepo
import com.wangbuliao.todo.reminder.AlarmScheduler
import com.wangbuliao.todo.update.Updater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StatusFilter(val label: String) {
    TODO("待办"), DONE("已办"), ALL("全部")
}

sealed interface Screen {
    object List : Screen
    object Edit : Screen
    object Settings : Screen
}

/** 编辑页草稿（单一数据源，避免本地状态同步问题） */
data class EditDraft(
    val id: Long = 0,
    val title: String = "",
    val note: String = "",
    val category: String = Task.DEFAULT_CATEGORY,
    val urgent: Boolean = false,
    val remindAt: Long = 0,
    val done: Boolean = false
)

data class UiState(
    val loading: Boolean = true,
    val allTasks: List<Task> = emptyList(),
    val categories: List<String> = emptyList(),
    val status: StatusFilter = StatusFilter.TODO,
    val category: String? = null,
    val urgentOnly: Boolean = false
) {
    /** 按当前筛选条件排序后的可见任务：
     *  待办在前 → 紧急置顶 → 有提醒按时间升序 → 创建时间降序；已办按完成时间降序 */
    val visible: List<Task>
        get() = allTasks.asSequence()
            .filter {
                when (status) {
                    StatusFilter.TODO -> !it.done
                    StatusFilter.DONE -> it.done
                    StatusFilter.ALL -> true
                }
            }
            .filter { category == null || it.category == category }
            .filter { !urgentOnly || it.urgent }
            .sortedWith(
                Comparator<Task> { a, b ->
                    if (a.done != b.done) return@Comparator if (a.done) 1 else -1
                    if (!a.done) {
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

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.List)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _draft = MutableStateFlow(EditDraft())
    val draft: StateFlow<EditDraft> = _draft.asStateFlow()

    private val _msg = MutableStateFlow<String?>(null)
    val msg: StateFlow<String?> = _msg.asStateFlow()

    fun consumeMsg() {
        _msg.value = null
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

    fun openNew() {
        _draft.value = EditDraft(
            category = _ui.value.categories.firstOrNull() ?: Task.DEFAULT_CATEGORY
        )
        _screen.value = Screen.Edit
    }

    fun openEdit(id: Long) {
        viewModelScope.launch {
            val t = TaskRepo.get(id)
            _draft.value = t?.let {
                EditDraft(it.id, it.title, it.note, it.category, it.urgent, it.remindAt, it.done)
            } ?: EditDraft()
            _screen.value = Screen.Edit
        }
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun backList() {
        _screen.value = Screen.List
    }

    fun updateDraft(f: (EditDraft) -> EditDraft) {
        _draft.value = f(_draft.value)
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
                    updatedAt = now
                )
                TaskRepo.save(task)
                if (task.done || task.remindAt <= 0) {
                    AlarmScheduler.cancel(ctx, task)
                } else {
                    AlarmScheduler.schedule(ctx, task)
                }
                refresh()
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
                TaskRepo.setDone(t.id, nd)
                val nt = t.copy(done = nd, updatedAt = System.currentTimeMillis())
                if (nd || nt.remindAt <= 0) {
                    AlarmScheduler.cancel(ctx, nt)
                } else {
                    AlarmScheduler.schedule(ctx, nt)
                }
                refresh()
            } catch (e: Exception) {
                _msg.value = "操作失败：${e.message}"
            }
        }
    }

    fun deleteTask(t: Task) {
        viewModelScope.launch {
            try {
                TaskRepo.delete(t.id)
                AlarmScheduler.cancel(ctx, t)
                refresh()
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
                TaskRepo.delete(d.id)
                AlarmScheduler.cancel(ctx, Task(id = d.id))
                refresh()
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
