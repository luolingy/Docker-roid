package com.midsancode.docklet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.midsancode.docklet.ui.DockletRoot
import com.midsancode.docklet.ui.theme.DockletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DockletTheme {
                DockletRoot()
            }
        }
    }
}
