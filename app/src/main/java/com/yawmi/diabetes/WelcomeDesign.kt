package com.yawmi.diabetes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeHero() {
    val w = LocalWords.current
    val c = MaterialTheme.colorScheme
    Surface(color = c.primaryContainer, contentColor = c.onPrimaryContainer, shape = BoardShape) {
        Box(Modifier.fillMaxWidth()) {
            Contours(Modifier.matchParentSize(), c.onPrimaryContainer)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(44.dp))
                    Text(w["app"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Reveal(1) {
                    Text(w["welcome_headline"], style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(w["welcome_design_body"], style = MaterialTheme.typography.bodyLarge)
                }
                Reveal(2) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("glucose", "water", "medications").forEach { kind ->
                                Surface(shape = CircleShape, color = c.surfaceContainerLow, contentColor = c.onSurface) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Glyph(kind, Modifier.size(16.dp), domainColors(kind).ink)
                                        Text(w[kind], style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        WaterVessel(.6f, Modifier.size(74.dp, 125.dp))
                    }
                }
            }
        }
    }
}
