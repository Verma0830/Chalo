package com.chalo.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.chalo.customer.presentation.navigation.ChaloNavGraph
import com.chalo.customer.presentation.theme.ChaloTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChaloTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChaloNavGraph()
                }
            }
        }
    }
}
