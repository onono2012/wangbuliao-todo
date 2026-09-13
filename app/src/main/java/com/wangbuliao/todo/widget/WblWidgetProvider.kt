package com.wangbuliao.todo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.wangbuliao.todo.MainActivity
import com.wangbuliao.todo.R
import com.wangbuliao.todo.data.DbHelper

/**
 * v1.5.0 桌面小组件：待办清单 + 快速勾选 + 快速新增。
 *
 * 设计说明：
 * - 列表数据走 [WblWidgetService]（RemoteViewsFactory，后台线程同步查 SQLite）。
 * - 行点击用 setPendingIntentTemplate + FLAG_MUTABLE 模板，
 *   fill-in 合并任务 id 到 [WidgetActionReceiver] 完成勾选。
 * - header 点按打开 App；「+」直达新建任务编辑页（深链 EXTRA_NEW_TASK）。
 * - 数据变化后由 [updateAll] 主动刷新（TaskRepo 与勾选广播都会调用），
 *   updatePeriodMillis 仅作兜底。
 */
class WblWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(ctx, awm, id)
    }

    companion object {
        /** MainActivity 深链：打开新建任务编辑页 */
        const val EXTRA_NEW_TASK = "wbl_new_task"

        fun updateWidget(ctx: Context, awm: AppWidgetManager, id: Int) {
            try {
                awm.updateAppWidget(id, buildViews(ctx))
                awm.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
            } catch (_: Exception) {
                // 小组件刷新失败绝不能影响主流程
            }
        }

        /** 刷新桌面上所有本应用小组件（数据变化后调用） */
        fun updateAll(ctx: Context) {
            try {
                val awm = AppWidgetManager.getInstance(ctx)
                val ids = awm.getAppWidgetIds(ComponentName(ctx, WblWidgetProvider::class.java))
                for (id in ids) updateWidget(ctx, awm, id)
            } catch (_: Exception) {
            }
        }

        private fun buildViews(ctx: Context): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_frame)
            val pending = try {
                DbHelper.get(ctx).pendingCount()
            } catch (_: Exception) {
                0
            }
            views.setTextViewText(
                R.id.widget_count,
                if (pending > 0) "$pending 条待办" else "全部完成"
            )
            // 列表数据源
            views.setRemoteAdapter(R.id.widget_list, Intent(ctx, WblWidgetService::class.java))
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            // 行点击模板（fill-in 合并 task id）——MUTABLE 才能合并 extras
            val toggleTpl = PendingIntent.getBroadcast(
                ctx, 100,
                Intent(ctx, WidgetActionReceiver::class.java)
                    .setAction(WidgetActionReceiver.ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, toggleTpl)
            // 「+」→ 新建任务编辑页
            val addPi = PendingIntent.getActivity(
                ctx, 101,
                launchIntent(ctx).putExtra(EXTRA_NEW_TASK, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_add, addPi)
            // header 点按 → 打开 App
            val openPi = PendingIntent.getActivity(
                ctx, 102,
                launchIntent(ctx),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, openPi)
            return views
        }

        private fun launchIntent(ctx: Context): Intent =
            Intent(ctx, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
