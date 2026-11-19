package com.lyricsync.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionOnboardingScreen(
    hasNotificationAccess: Boolean,
    hasOverlayPermission: Boolean,
    onOpenAppDetails: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onContinueToApp: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "LyricSync Kurulumu",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Spotify'dan canlı şarkı yakalamak ve ekran üstü altyazı göstermek için izinleri tamamlayın ya da manuel arama moduyla devam edin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Step 1: Notification Access + Android 13+ Restricted Settings Unlock Guide
            NotificationPermissionCard(
                isGranted = hasNotificationAccess,
                onOpenAppDetails = onOpenAppDetails,
                onRequestNotificationAccess = onRequestNotificationAccess
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Overlay Permission
            PermissionStepCard(
                stepNumber = "2",
                title = "Diğer Uygulamaların Üstünde Gösterme",
                description = "Spotify açıkken ekranın üstünde sürüklenebilir mini çeviri altyazısı göstermek için gereklidir.",
                icon = Icons.Default.Layers,
                isGranted = hasOverlayPermission,
                buttonText = "Üstte Gösterme İznini Ver",
                onAction = onRequestOverlayPermission
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onContinueToApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = when {
                        hasNotificationAccess && hasOverlayPermission -> "Uygulamayı Başlat"
                        hasNotificationAccess -> "Yüzen Pencere Olmadan Devam Et"
                        else -> "İzinsiz Göz At (Manuel Şarkı Arama Modu)"
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun NotificationPermissionCard(
    isGranted: Boolean,
    onOpenAppDetails: () -> Unit,
    onRequestNotificationAccess: () -> Unit
) {
    val borderColor = if (isGranted) {
        Color(0xFF10B981)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verildi",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = "1",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bildirim Erişimi (Otomatik Takip)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isGranted) "İzin Verildi ✓" else "Bekleniyor",
                    fontSize = 12.sp,
                    color = if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                )
            }

            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Spotify'da çalan şarkıyı, sanatçıyı ve anlık saniyeyi yakalamak için gereklidir.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!isGranted) {
            Spacer(modifier = Modifier.height(12.dp))

            // Android 13+ Restricted Settings explicit walkthrough
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "\"Güvenliğiniz için bu ayar kullanılamıyor\" uyarısı çözümü:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android 13+ dışarıdan kurulan APK'larda bu ayarı kilitler. Açmak için:\n" +
                        "• Önce aşağıdaki 1.A düğmesiyle Uygulama Bilgileri'ni açın.\n" +
                        "• Sağ üstteki ⋮ (üç nokta) menüsünden \"Kısıtlanmış ayarlara izin ver\" seçeneğine dokunun.\n" +
                        "• Geri dönüp 1.B ile Bildirim Erişimi'ni açın.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onOpenAppDetails,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFF59E0B)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "1.A: Uygulama Bilgilerini Aç (Kısıtlanmış Ayar İzni)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onRequestNotificationAccess,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "1.B: Bildirim Erişimini Aç",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PermissionStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    buttonText: String,
    onAction: () -> Unit
) {
    val borderColor = if (isGranted) {
        Color(0xFF10B981)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verildi",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = stepNumber,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isGranted) "İzin Verildi ✓" else "Bekleniyor",
                    fontSize = 12.sp,
                    color = if (isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                )
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = description,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!isGranted) {
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = buttonText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
