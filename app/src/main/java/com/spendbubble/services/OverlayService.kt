package com.spendbubble.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.spendbubble.MainActivity
import com.spendbubble.R
import com.spendbubble.data.ExpenseRepository
import com.spendbubble.ui.OverlayContent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class OverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    @Inject
    lateinit var repository: ExpenseRepository

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ComposeView
    private lateinit var params: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isExpanded = mutableStateOf(false)

    // For SavedStateRegistryOwner
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(1, createNotification())

        initOverlayView()
    }

    private fun initOverlayView() {
        overlayView = ComposeView(this).apply {
            // Attach lifecycle and saved state owners for Compose
            ViewTreeLifecycleOwner.set(this, this@OverlayService)
            ViewTreeViewModelStoreOwner.set(this, this@OverlayService)
            ViewTreeSavedStateRegistryOwner.set(this, this@OverlayService)

            setContent {
                OverlayContent(
                    isExpanded = isExpanded.value,
                    onExpand = { toggleState(true) },
                    onCollapse = { toggleState(false) },
                    onSave = { amount, note, cat ->
                        saveExpense(amount, note, cat)
                        toggleState(false)
                    }
                )
            }
        }

        // Layout Params
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            // Initially NOT Focusable to allow pass-through
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        setupTouchListener()
        windowManager.addView(overlayView, params)
    }

    private fun setupTouchListener() {
        overlayView.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (isExpanded.value) return false // Don't drag when expanded

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Snap to edge logic
                        val screenWidth = resources.displayMetrics.widthPixels
                        val middle = screenWidth / 2
                        if (params.x >= middle) {
                            params.x = screenWidth - 100 // Right edge padding
                        } else {
                            params.x = 0
                        }
                        windowManager.updateViewLayout(overlayView, params)
                        
                        // Check for click vs drag
                        if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) {
                            v.performClick() // Trigger Bubble click
                            toggleState(true)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun toggleState(expanded: Boolean) {
        isExpanded.value = expanded
        if (expanded) {
            // Remove FLAG_NOT_FOCUSABLE to allow Keyboard
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            // Center the overlay for input
             val screenWidth = resources.displayMetrics.widthPixels
             val screenHeight = resources.displayMetrics.heightPixels
             params.x = (screenWidth / 2) - 150 // approximate half width offset cant rely on view width yet
             params.y = (screenHeight / 3)
        } else {
            // Add FLAG_NOT_FOCUSABLE back
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            // Snap back to side is handled by user dragging next time, 
            // but for now let's just keep it where it was or snap it back
            params.x = 0 // Reset to left for simplicity
        }
        windowManager.updateViewLayout(overlayView, params)
    }

    private fun saveExpense(amount: Double, note: String, category: String) {
        CoroutineScope(Dispatchers.IO).launch {
            repository.addExpense(amount, note, category)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "overlay_channel",
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "overlay_channel")
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Default icon
            .setContentIntent(pendingIntent)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
