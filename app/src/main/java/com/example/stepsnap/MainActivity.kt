package com.example.stepsnap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.stepsnap.ui.home.HomeScreen
import com.example.stepsnap.ui.theme.StepSnapColors
import com.example.stepsnap.ui.theme.StepSnapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StepSnapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StepSnapColors.Card
                ) {
                    HomeScreen()
                }
            }
        }
    }
}
