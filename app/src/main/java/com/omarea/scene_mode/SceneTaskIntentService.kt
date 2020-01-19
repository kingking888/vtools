package com.omarea.scene_mode

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.widget.Toast
import com.omarea.data_collection.EventBus
import com.omarea.data_collection.EventReceiver
import com.omarea.data_collection.EventTypes
import com.omarea.data_collection.GlobalStatus
import com.omarea.model.TimingTaskInfo
import com.omarea.store.TimingTaskStorage

class SceneTaskIntentService : IntentService("SceneTaskIntentService") {

    override fun onHandleIntent(intent: Intent?) {
        intent?.run {
            val taskId = if (intent.hasExtra("taskId")) intent.getStringExtra("taskId") else null
            taskId?.run {
                executeTask(this)
            }
        }
    }

    private fun executeTask(taskId: String) {
        val context = this
        val timingTask = TimingTaskStorage(context).load(taskId)
        timingTask?.run {
            if (timingTask.taskActions != null && timingTask.taskActions.size > 0) {
                if (chargeOnly && GlobalStatus.batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING) {
                    Toast.makeText(context, "未在充电状态，跳过定时任务", Toast.LENGTH_LONG).show()
                } else if (batteryCapacityRequire > 0 && GlobalStatus.batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING && GlobalStatus.batteryCapacity < batteryCapacityRequire) {
                    Toast.makeText(context, "电量低于" + batteryCapacityRequire + "%，跳过定时任务", Toast.LENGTH_LONG).show()
                } else if (afterScreenOff && ScreenState(context).isScreenOn()) {
                    // 如果是个要求屏幕关闭后执行的任务，且现在屏幕还在点亮状态，放到息屏事件观测队列中
                    EventBus.subscibe(ScreenDelayTaskReceiver(this, context.applicationContext))
                } else {
                    TimingTaskExecutor(this, context).run()
                }
            }
        }
    }

    // 屏幕关闭后才执行的任务
    class ScreenDelayTaskReceiver(private val timingTask: TimingTaskInfo, private val context: Context) :EventReceiver {
        override fun onReceive(eventType: EventTypes?) {
            TimingTaskExecutor(timingTask, context).run()
        }

        override fun eventFilter(eventType: EventTypes?): Boolean {
            return eventType == EventTypes.SCREEN_OFF
        }
    }
}