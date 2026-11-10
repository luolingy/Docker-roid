package com.dockerroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dockerroid.ui.DockerDroidRoot
import com.dockerroid.ui.theme.DockerDroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DockerDroidTheme {
                DockerDroidRoot()
            }
        }
    }
}
