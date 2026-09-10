package com.example.ao3application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ao3application.ui.AppUi
import com.example.ao3application.ui.theme.AO3ApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AO3ApplicationTheme {
                AppUi((application as App).repo)
            }
        }
    }
}
