package com.xdl.upward

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.xdl.upward.data.local.AppDatabase
import com.xdl.upward.data.local.ConfigEntity
import com.xdl.upward.ui.navigation.AppNavGraph
import com.xdl.upward.ui.theme.UpWardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            val configDao = AppDatabase.getInstance(applicationContext).configDao()
            configDao.getValue("message_context_count")
            if (configDao.getByKey("initial_date") == null) {
                configDao.insert(ConfigEntity(key = "initial_date", value = LocalDate.now().toString()))
            }
        }
        enableEdgeToEdge()
        setContent {
            UpWardTheme {
                AppNavGraph()
            }
        }
    }
}
