package com.xdl.upward

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.ui.navigation.AppNavGraph
import com.xdl.upward.ui.theme.UpWardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(applicationContext).configDao().getValue("message_context_count")
        }
        enableEdgeToEdge()
        setContent {
            UpWardTheme {
                AppNavGraph()
            }
        }
    }
}
