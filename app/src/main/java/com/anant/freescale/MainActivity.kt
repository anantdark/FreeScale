package com.anant.freescale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anant.freescale.ui.navigation.FreeScaleApp
import com.anant.freescale.ui.theme.FreeScaleTheme

class MainActivity : ComponentActivity() {
    private val vm: MeasureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val materialYou by vm.materialYou.collectAsStateWithLifecycle()
            FreeScaleTheme(dynamicColor = materialYou) {
                FreeScaleApp(vm = vm)
            }
        }
    }
}
