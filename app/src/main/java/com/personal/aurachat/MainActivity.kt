package com.personal.aurachat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.personal.aurachat.ui.theme.AuraChatTheme
import com.personal.aurachat.ui.navigation.AuraChatNavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as AuraChatApp).appContainer
        enableEdgeToEdge()
        setContent {
            AuraChatTheme {
                AuraChatNavGraph(appContainer = appContainer)
            }
        }
    }
}
