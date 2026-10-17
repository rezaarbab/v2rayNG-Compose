package ir.v2rayng.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ir.v2rayng.compose.ui.AppScaffold
import ir.v2rayng.compose.ui.theme.V2rayNGTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            V2rayNGTheme {
                AppScaffold()
            }
        }
    }
}
