package com.ghaniram.zoya.settings

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.ui.theme.*

// Expose LocalAnuColors to all files in com.ghaniram.zoya.settings package
val LocalAnuColors = com.ghaniram.zoya.ui.theme.LocalAnuColors

/**
 * Screen destinations within the Anu Settings module.
 */
enum class SettingsScreenDestination {
    HOME,
    PERSONAL,
    ASSISTANT_ANU,
    VOICE_PICKER,
    SKILLS,
    SUB_AGENTS,
    EMAIL,
    WHATSAPP_REPORTS,
    SOCIAL_MEDIA,
    CONNECTORS,
    BACKUP_RESTORE,
    ADVANCED,
    THEME_CUSTOMIZATION,
    APPEARANCE_ORB,
    BEHAVIOUR,
    TYPING,
    VOICE_GUARDIAN,
    EMERGENCY_SOS,
    TOUCH_GUARD,
    SCREEN_LOCK,
    PERMISSIONS,
    WHATSAPP_AUTO_REPLY,
    EVENT_TRIGGERS,
    OPTIONAL_PLACES
}

val AnuDarkBackground: Color
    @Composable
    get() = LocalAnuColors.current.background

val AnuDarkCard: Color
    @Composable
    get() = LocalAnuColors.current.cardBackground

val AnuDarkCardBorder: Color
    @Composable
    get() = LocalAnuColors.current.cardBorder

val AnuDarkInputBg: Color
    @Composable
    get() = LocalAnuColors.current.inputBackground

val AnuAccentBlue = Color(0xFF38BDF8)
val AnuWarningOrange = Color(0xFFF59E0B)

@Composable
fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    showNotificationBell: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val colors = LocalAnuColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = colors.topBarTint
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        if (trailingContent != null) {
            trailingContent()
        } else if (showNotificationBell) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "Notifications",
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = colors.accentPrimary,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "ANU",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    val colors = LocalAnuColors.current
    Text(
        text = text.uppercase(),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.textSecondary,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun SettingsItemCard(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val colors = LocalAnuColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.cardBackground,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = if (colors.isDark) 0.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.accentPrimary.copy(alpha = if (colors.isDark) 0.2f else 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.accentPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SettingsCardContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAnuColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.cardBackground,
        contentColor = colors.textPrimary,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = if (colors.isDark) 0.dp else 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingsCardTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = LocalAnuColors.current.textPrimary,
        modifier = modifier
    )
}

@Composable
fun SettingsTipBanner(
    text: String,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false
) {
    val colors = LocalAnuColors.current
    val bgColor = if (isWarning) {
        if (colors.isDark) Color(0xFF451A03).copy(alpha = 0.6f) else Color(0xFFFEF3C7)
    } else {
        colors.chipBackground
    }
    val borderColor = if (isWarning) AnuWarningOrange.copy(alpha = 0.5f) else colors.cardBorder
    val dotColor = if (isWarning) AnuWarningOrange else colors.accentPrimary
    val textColor = if (isWarning) {
        if (colors.isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
    } else {
        colors.textPrimary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 11.5.sp,
                color = textColor,
                lineHeight = 16.5.sp
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalAnuColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    lineHeight = 15.sp
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accentPrimary,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.cardBorder
            )
        )
    }
}

@Composable
fun SettingsInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false
) {
    val colors = LocalAnuColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.inputBackground,
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 13.sp,
                        color = colors.textSecondary.copy(alpha = 0.7f)
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.5.sp,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accentPrimary),
                    singleLine = true,
                    visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ChoiceChipPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAnuColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) colors.accentPrimary else colors.chipBackground,
        border = BorderStroke(1.dp, if (isSelected) colors.accentPrimary else colors.cardBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
