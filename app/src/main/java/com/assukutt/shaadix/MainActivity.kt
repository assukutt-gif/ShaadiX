package com.assukutt.shaadix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.assukutt.shaadix.ui.ShaadiXApp
import com.assukutt.shaadix.ui.theme.ShaadiXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShaadiXTheme { Surface(Modifier.fillMaxSize()) { ShaadiXApp() } } }
    }
}
