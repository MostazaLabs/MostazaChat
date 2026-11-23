package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CallType
import com.example.ui.components.AvatarBadge
import com.example.ui.viewmodel.ActiveFakeCall
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun FakeCallOverlayScreen(
    call: ActiveFakeCall,
    onAnswer: () -> Unit,
    onEndCall: (durationSeconds: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var elapsedSeconds by remember(call.isAnswered) { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }

    BackHandler {
        onEndCall(elapsedSeconds)
    }

    LaunchedEffect(call.isAnswered) {
        if (call.isAnswered) {
            while (true) {
                delay(1000L)
                elapsedSeconds++
            }
        }
    }

    val timerText = String.format(Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF071B18),
                        Color(0xFF0E2F2A),
                        Color(0xFF071412)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (call.callType == CallType.VIDEO) "VIDEOLLAMADA ENTRANTE" else "LLAMADA DE VOZ",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9DF8DE)
                )
                Spacer(Modifier.height(18.dp))
                Box(contentAlignment = Alignment.Center) {
                    if (!call.isAnswered) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF26E1B4).copy(alpha = 0.16f))
                        )
                    }
                    AvatarBadge(
                        emoji = call.avatarEmoji,
                        colorHex = call.avatarColorHex,
                        online = false,
                        size = 112.dp,
                        imageBase64 = call.avatarImageBase64
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = call.contactName,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (call.isAnswered) timerText else "Llamando...",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallControlCircle(
                        icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        label = "Silenciar",
                        active = isMuted,
                        onClick = { isMuted = !isMuted }
                    )
                    CallControlCircle(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        label = "Altavoz",
                        active = isSpeaker,
                        onClick = { isSpeaker = !isSpeaker }
                    )
                    if (call.callType == CallType.VIDEO) {
                        CallControlCircle(
                            icon = if (isCameraOff) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam,
                            label = "Cámara",
                            active = isCameraOff,
                            onClick = { isCameraOff = !isCameraOff }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEF4444),
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable { onEndCall(elapsedSeconds) }
                            .testTag("end_fake_call_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = "Colgar llamada",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    if (!call.isAnswered) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF22C55E),
                            contentColor = Color.White,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onAnswer)
                                .testTag("answer_fake_call_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Call,
                                    contentDescription = "Contestar llamada",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlCircle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (active) Color.White else Color.White.copy(alpha = 0.16f),
            contentColor = if (active) Color(0xFF071B18) else Color.White,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(26.dp))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}
