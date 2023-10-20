package com.pengxh.autodingding.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.pengxh.autodingding.BaseApplication
import com.pengxh.autodingding.bean.HistoryRecordBean
import com.pengxh.autodingding.bean.NotificationBean
import com.pengxh.autodingding.extensions.createMail
import com.pengxh.autodingding.extensions.sendTextMail
import com.pengxh.autodingding.ui.WelcomeActivity
import com.pengxh.autodingding.utils.Constant
import com.pengxh.kt.lite.extensions.timestampToCompleteDate
import com.pengxh.kt.lite.utils.SaveKeyValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import android.os.PowerManager
import android.content.Context
import android.app.KeyguardManager
/**
 * @description: 状态栏监听服务
 * @author: Pengxh
 * @email: 290677893@qq.com
 * @date: 2019/12/25 23:17
 */
class NotificationMonitorService : NotificationListenerService() {

    private val kTag = "MonitorService"
    private val historyRecordBeanDao by lazy { BaseApplication.get().daoSession.historyRecordBeanDao }
    private val notificationBeanDao by lazy { BaseApplication.get().daoSession.notificationBeanDao }

    //尝试自动解锁
    private val pm by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private var wakeLock: PowerManager.WakeLock =
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                acquire()
            }
        }
    private val km by lazy {getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    private var keyguardLock = km.newKeyguardLock("");
    private var isMeUnLock = false



    /**
     * 有可用的并且和通知管理器连接成功时回调
     */
    override fun onListenerConnected() {
        Log.d(kTag, "onListenerConnected")
    }

    /**
     * 当有新通知到来时会回调
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras: Bundle = sbn.notification.extras
        // 获取接收消息APP的包名
        val packageName: String = sbn.packageName
        // 获取接收消息的内容
        val notificationText = extras.getString(Notification.EXTRA_TEXT)

        //保存所有通知信息
        val notificationBean = NotificationBean()
        notificationBean.uuid = UUID.randomUUID().toString()
        notificationBean.packageName = packageName
        notificationBean.notificationTitle = extras.getString(Notification.EXTRA_TITLE)
        notificationBean.notificationMsg = notificationText
        notificationBean.postTime = System.currentTimeMillis().timestampToCompleteDate()
        notificationBeanDao.save(notificationBean)

        if (notificationText == null || notificationText == "") {
            return
        }
        if (notificationText.contains("考勤打卡")) {
            //保存打卡记录
            val bean = HistoryRecordBean()
            bean.uuid = UUID.randomUUID().toString()
            bean.date = System.currentTimeMillis().timestampToCompleteDate()
            bean.message = notificationText
            historyRecordBeanDao.save(bean)
            val emailAddress = SaveKeyValues.getValue(Constant.EMAIL_ADDRESS, "") as String
            if (emailAddress.isBlank()) {
                Log.d(kTag, "邮箱地址为空")
            } else {
                //发送打卡成功的邮件
                CoroutineScope(Dispatchers.Main).launch {
                    withContext(Dispatchers.IO) {
                        notificationText.createMail(emailAddress).sendTextMail()
                    }

                    val intent = Intent(
                        this@NotificationMonitorService, WelcomeActivity::class.java
                    )
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    Log.d(kTag,"准备锁屏")
                    autoLock()
                }
            }
        }
    }

    /**
     * 当有通知移除时会回调
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    override fun onListenerDisconnected() {
        Log.d(kTag, "onListenerDisconnected")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 通知侦听器断开连接 - 请求重新绑定
            requestRebind(ComponentName(this, NotificationListenerService::class.java))
        }
    }

    fun autoUnlock() {
        if (!pm.isScreenOn) {
            wakeLock!!.acquire()
            Log.d(kTag, "onAccessibilityEvent: 亮屏")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (km.isDeviceLocked()) {
                Log.d(kTag, "autoUnlock: sdk >= 22: 屏幕被密码锁柱")
                wakeLock!!.release()
            } else {
                if (km.inKeyguardRestrictedInputMode()) {
                    keyguardLock.disableKeyguard()
                    Log.d(kTag,"onAccessibilityEvent: 尝试解锁"
                    )
                    isMeUnLock = true
                }
            }
        }
    }

    fun autoLock() {
//        if (isMeUnLock) {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            keyguardLock.reenableKeyguard()
            Log.d(kTag, "autoLock: 自动锁")
            if (wakeLock != null && pm.isScreenOn) {
                wakeLock.release()
                Log.d(kTag, "autoLock: 自动灭")
            }
            isMeUnLock = false
//        }
    }

}