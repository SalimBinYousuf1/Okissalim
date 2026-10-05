package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.screens.ControllerMainScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.CrashProtector
import com.example.viewmodel.ControllerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ControllerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashProtector.install()
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    val isPaired by viewModel.isPaired.collectAsState()
                    val currentTab by viewModel.currentTab.collectAsState()

                    BackHandler(enabled = isPaired && currentTab > 0) {
                        viewModel.setTab(0)
                    }

                    if (!isPaired) {
                        PairingScreen(viewModel = viewModel)
                    } else {
                        ControllerMainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
