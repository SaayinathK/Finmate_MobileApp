package com.example.financetracker.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.databinding.ActivitySplashBinding
import com.example.financetracker.receiver.NotificationScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.ui.WalletMotion

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private var startupComplete = false
    private var destination: Class<*>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                // Let the global PIN gate settle before starting the visible sequence.
                binding.root.post {
                    if (canShowStartup()) {
                        startupComplete = false
                        revealBrand()
                        binding.startupRunner.restart {
                            startupComplete = true
                            navigateWhenReady()
                        }
                    }
                }
            }
            override fun onPause(owner: LifecycleOwner) {
                startupComplete = false
                binding.startupRunner.cancelPlayback()
            }
        })
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { splash -> splash.remove() }
        }
        val padding = binding.root.paddingLeft
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom); insets
        }
        lifecycleScope.launch {
            val signedIn = try {
                withContext(Dispatchers.IO) {
                    try {
                        NotificationScheduler.scheduleDailyReminder(this@SplashActivity)
                        NotificationScheduler.rescheduleTasks(this@SplashActivity)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { android.util.Log.w("SWalletStartup", "Could not restore reminders", error) }
                    AppDatabase.getDatabase(this@SplashActivity).userProfileDao().getLoggedInUser() != null
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { false }
            destination = if (signedIn) HomeActivity::class.java else LoginActivity::class.java
            navigateWhenReady()
        }
    }
    private fun canShowStartup() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        !isFinishing && !com.example.financetracker.security.AppLock.isLocked(this)

    private fun revealBrand() {
        if (!WalletMotion.enabled()) { return }
        binding.logoImage.alpha = 0f
        binding.logoImage.scaleX = 0.92f; binding.logoImage.scaleY = 0.92f
        binding.logoImage.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).start()
        WalletMotion.reveal(binding.startupHeadline, 100)
        WalletMotion.reveal(binding.startupStage, 150)
        WalletMotion.reveal(binding.startupFeatures, 200)
    }
    private fun navigateWhenReady() {
        val next = destination ?: return
        if (!startupComplete || !canShowStartup()) { return }
        destination = null; startActivity(Intent(this, next)); finish()
    }
}
