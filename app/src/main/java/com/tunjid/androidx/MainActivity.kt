package com.tunjid.androidx

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tunjid.androidx.core.delegates.activityIntent
import com.tunjid.androidx.databinding.ActivityMainBinding
import com.tunjid.androidx.navigation.MultiStackNavigator
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.androidx.navigation.multiStackNavigationController
import com.tunjid.androidx.tabnav.routing.RouteFragment
import com.tunjid.androidx.uidrivers.GlobalUiDriver
import com.tunjid.androidx.uidrivers.GlobalUiHost
import com.tunjid.androidx.uidrivers.materialDepthAxisTransition
import com.tunjid.androidx.uidrivers.materialFadeThroughTransition
import leakcanary.AppWatcher
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), GlobalUiHost, Navigator.Controller {

    private val tabs = intArrayOf(R.id.menu_navigation, R.id.menu_recyclerview, R.id.menu_communications, R.id.menu_misc)
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val deepLinkTab by activityIntent<Int?>(-1)

    override val globalUiController by lazy { GlobalUiDriver(this, binding, navigator) }

    override val navigator: MultiStackNavigator by multiStackNavigationController(
        stackCount = tabs.size,
        containerId = R.id.content_container,
        backStackType = MultiStackNavigator.BackStackType.Unlimited,
        stopInvalidNavigation = true,
        rootFunction = RouteFragment.Companion::newInstance,
        initialIndex = 0
    )

    public override fun onCreate(savedInstanceState: Bundle?) {
        AppWatcher.config = AppWatcher.config.copy(watchDurationMillis = TimeUnit.SECONDS.toMillis(8))

        // Add this before on create to make sure fragment callbacks are added after.
        // This makes Fragment back pressed callbacks take higher precedence.
        onBackPressedDispatcher.addCallback(this) { if (!navigator.pop()) finish() }

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.bottomNavigation.apply {
            navigator.stackSelectedListener = { menu.findItem(tabs[it])?.isChecked = true }
            navigator.stackTransactionModifier = navigator.materialFadeThroughTransition()
            navigator.transactionModifier = navigator.materialDepthAxisTransition()

            // Swallow insets, don't allow default behavior
            setOnApplyWindowInsetsListener { _: View?, windowInsets: WindowInsets? -> windowInsets }
            setOnNavigationItemSelectedListener { navigator.show(tabs.indexOf(it.itemId)).let { true } }
            setOnNavigationItemReselectedListener { navigator.activeNavigator.clear() }
        }
        createNotificationChannel()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkTab
            ?.takeIf { it >= 0 }
            ?.let(navigator::show)
    }

    override fun onResume() {
        super.onResume()
        if (intent?.getBooleanExtra("stress_test", false) == true) {
            navigator.performConsecutively(lifecycleScope) {
                clearAll()
                push(RouteFragment.newInstance(tabIndex = 0, launchStressTest = true))
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                getString(R.string.notification_channel_id),
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                getSystemService(NotificationManager::class.java).createNotificationChannel(this)
            }
        }
    }
}
