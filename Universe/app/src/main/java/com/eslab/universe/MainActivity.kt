package com.eslab.universe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.eslab.universe.ui.UniverseApp
import com.eslab.universe.ui.UniverseViewModel
import com.eslab.universe.ui.theme.UniverseTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<UniverseViewModel> {
        UniverseViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniverseTheme {
                UniverseApp(viewModel = viewModel)
            }
        }
    }
}
