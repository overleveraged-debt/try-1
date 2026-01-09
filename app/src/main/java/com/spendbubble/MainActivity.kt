package com.spendbubble

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendbubble.data.Expense
import com.spendbubble.data.ExpenseRepository
import com.spendbubble.services.OverlayService
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: ExpenseRepository

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            val expenses by repository.allExpenses.collectAsState(initial = emptyList())
            val todayTotal by repository.getTodayTotal().collectAsState(initial = 0.0)

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SpendBubble History",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        
                        Text(
                            text = "Today's Total: $${todayTotal ?: 0.0}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            Button(onClick = { openOverlaySettings() }) {
                                Text("Grant Overlay Permission")
                            }
                        } else {
                            Button(onClick = { startOverlayService() }) {
                                Text("Restart Overlay Service")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        LazyColumn {
                            items(expenses) { expense ->
                                ExpenseItem(expense)
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkPermissions() {
        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, 1234)
    }
}

@Composable
fun ExpenseItem(expense: Expense) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = expense.category, style = MaterialTheme.typography.bodyMedium)
            if (expense.note.isNotEmpty()) {
                Text(text = expense.note, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(expense.timestamp)),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Text(
            text = "$${expense.amount}",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
