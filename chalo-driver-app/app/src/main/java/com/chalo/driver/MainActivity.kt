package com.chalo.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.chalo.driver.presentation.navigation.DriverNavGraph
import com.chalo.driver.presentation.theme.ChaloDriverTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChaloDriverTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DriverNavGraph()
                }
            }
        }
    }
}
