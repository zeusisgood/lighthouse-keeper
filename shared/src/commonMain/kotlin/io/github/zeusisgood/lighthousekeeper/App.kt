package io.github.zeusisgood.lighthousekeeper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val Background = Color(0xFF121417)
private val TextColor = Color(0xFFE5E7EB)

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme(background = Background, onBackground = TextColor)) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Text("Lighthouse Keeper", color = TextColor)
        }
    }
}
