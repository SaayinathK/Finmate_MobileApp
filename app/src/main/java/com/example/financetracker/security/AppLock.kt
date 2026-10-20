package com.example.financetracker.security

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.financetracker.activities.PinActivity
import com.example.financetracker.widget.TodayTasksWidget
import java.util.Collections
import java.util.WeakHashMap

/** One in-memory unlock session, discarded when all activities leave the foreground. */
object AppLock : Application.ActivityLifecycleCallbacks {
    @Volatile private var unlocked = false
    private val activities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private val started = mutableSetOf<Activity>()
    fun isLocked(context: Context) = PinStore(context).enabled && !unlocked

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { lock(context) }
        }
        if (Build.VERSION.SDK_INT >= 33) application.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        else application.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun protect(activity: Activity) {
        if (PinStore(activity).enabled || activity is PinActivity) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (activity !is PinActivity) activity.window.decorView.visibility = if (isLocked(activity)) View.INVISIBLE else View.VISIBLE
    }

    fun requireUnlock(activity: Activity) {
        protect(activity)
        if (activity !is PinActivity && isLocked(activity))
            activity.startActivity(Intent(activity, PinActivity::class.java).putExtra(PinActivity.MODE, PinActivity.UNLOCK)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    fun lock(context: Context) {
        unlocked = false
        activities.toList().forEach(::protect)
        TodayTasksWidget.refresh(context)
    }

    fun unlock(activity: Activity): Boolean {
        val foreground = (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        val interactive = (activity.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (!foreground || !interactive) return false
        unlocked = true
        activities.toList().forEach(::protect)
        TodayTasksWidget.refresh(activity)
        return true
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) { activities.add(activity); protect(activity) }
    override fun onActivityStarted(activity: Activity) { started.add(activity); protect(activity) }
    override fun onActivityResumed(activity: Activity) { requireUnlock(activity) }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        started.remove(activity)
        if (started.isEmpty() && !activity.isChangingConfigurations) lock(activity.applicationContext)
    }
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) { activities.remove(activity); started.remove(activity) }
}
