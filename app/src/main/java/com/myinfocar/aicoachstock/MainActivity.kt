package com.myinfocar.aicoachstock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.myinfocar.aicoachstock.ui.navigation.MainScaffold
import com.myinfocar.aicoachstock.ui.theme.AICoachStockTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AICoachStockTheme {
                MainScaffold()
            }
        }
    }
}
