package com.wangbuliao.todo.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.wangbuliao.todo.R
import com.wangbuliao.todo.data.DbHelper
import com.wangbuliao.todo.data.Task
import com.wangbuliao.todo.util.TimeFmt

/**
 * 小组件列表数据源（RemoteViewsFactory 回调在 binder 线程，可安全同步查库）。
 * 排序与应用内列表一致：置顶 > 紧急 > 提醒时间升序 > 创建时间倒序。
 */
class WblWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext)

    private class Factory(private val ctx: Context) : RemoteViewsFactory {
        private var items: List<Task> = emptyList()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            items = try {
                DbHelper.get(ctx).queryTasks()
                    .filter { !it.done }
                    .sortedWith(
                        compareByDescending<Task> { it.pinned }
                            .thenByDescending { it.urgent }
                            .thenBy { if (it.remindAt > 0) it.remindAt else Long.MAX_VALUE }
                            .thenByDescending { it.createdAt }
                    )
                    .take(100)
            } catch (_: Exception) {
                emptyList()
            }
        }

        override fun onDestroy() {
            items = emptyList()
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(pos: Int): RemoteViews {
            val t = items[pos]
            val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
            rv.setImageViewResource(R.id.item_check, R.drawable.ic_widget_check_off)
            rv.setTextViewText(R.id.item_title, t.title.ifBlank { "（无标题）" })
            val sub = buildString {
                if (t.pinned) append("📌 置顶")
                if (t.urgent) {
                    if (isNotEmpty()) append(" · ")
                    append("🔥 紧急")
                }
                if (t.remindAt > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(TimeFmt.remind(t.remindAt))
                }
            }
            if (sub.isEmpty()) {
                rv.setViewVisibility(R.id.item_sub, View.GONE)
            } else {
                rv.setViewVisibility(R.id.item_sub, View.VISIBLE)
                rv.setTextViewText(R.id.item_sub, sub)
            }
            // fill-in：把任务 id 合并进 provider 设置的 toggle 模板
            rv.setOnClickFillInIntent(
                R.id.item_root,
                Intent().putExtra(WidgetActionReceiver.EXTRA_TASK_ID, t.id)
            )
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(pos: Int): Long = items[pos].id

        override fun hasStableIds(): Boolean = true
    }
}
