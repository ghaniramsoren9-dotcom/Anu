package com.ghaniram.zoya.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghaniram.zoya.AnuSettingsStore
import com.ghaniram.zoya.ui.theme.LocalAnuColors

data class SkillItem(
    val id: String,
    val title: String,
    val description: String,
    val isStoreBadge: Boolean = true
)

/**
 * Skills Screen matching Page 14 of the specification.
 * Fully interactive with toggling and installing from skill store with dynamic theme support.
 */
@Composable
fun AnuSkillsSettingsScreen(
    store: AnuSettingsStore,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAnuColors.current
    var enabledSet by remember { mutableStateOf(store.enabledSkills) }
    var installedStoreSkills by remember { mutableStateOf(store.installedStoreSkills) }

    val baseInstalledSkills = listOf(
        SkillItem("pro-email", "pro-email", "Professional email likhna aur bhejna — leave, apology, follow-up, application, client mail"),
        SkillItem("group-report", "group-report", "Job/work WhatsApp group me fixed format wali daily report bhejna — value poochh ke, confirm le ke"),
        SkillItem("pro-whatsapp", "pro-whatsapp", "Professional WhatsApp message likhna — client, boss, HR, vendor ko dhang ka message"),
        SkillItem("youtube-script", "youtube-script", "YouTube video ka ready-to-speak script likhne ka tarika — hook, retention, CTA aur bolne layak language"),
        SkillItem("youtube-title-thumbnail", "youtube-title-thumbnail", "YouTube title aur thumbnail text likhna — curiosity gap banao, jhooth nahi"),
        SkillItem("youtube-upload", "youtube-upload", "Video upload se pehle ka kaam — description, chapters, tags, pinned comment ka checklist"),
        SkillItem("social-posting", "social-posting", "Photo ya poster ko Instagram/Facebook pe story ya post ki tarah daalna, sahi size aur sahi raste"),
        SkillItem("web-design", "web-design", "Website banate waqt colour, layout aur phone-first rules — jo bhi Anu khud code karti hai uspe lagte hain"),
        SkillItem("coding-agent", "coding-agent", "Anu khud coding karti hai — kab kaunsa coding tool, kya poochhna hai, aur user ko kya batana hai"),
        SkillItem("code-publish", "code-publish", "Anu ka banaya project GitHub pe daalna aur Vercel/Netlify se deploy karwana"),
        SkillItem("whatsapp-pro", "whatsapp-pro", "WhatsApp ka koi bhi kaam (message, reply, group, file, call) galti-free tarike se karna")
    )

    val availableStoreSkills = listOf(
        SkillItem("daily-briefing", "daily-briefing", "Subah 'good morning' / 'din ka update do' bole to phone ka pura ek-baar-me briefing dena"),
        SkillItem("call-secretary", "call-secretary", "Incoming phone call aane pe announce/pickup/reject aur call ke baad ka pura etiquette"),
        SkillItem("photo-share", "photo-share", "Photo/video khinch ke ya gallery se nikaal ke WhatsApp pe bhejne ka end-to-end flow"),
        SkillItem("music-dj", "music-dj", "Gaana/music/video bajane, control karne aur mood ke hisaab se DJ banne ka tarika"),
        SkillItem("phone-care", "phone-care", "Battery bachana, phone slow/garam hone pe care karna, settings sahi karna"),
        SkillItem("study-focus", "study-focus", "Padhai/kaam ka focus session — DND, timer, distractions band, end pe recap"),
        SkillItem("chatgpt-image-gen", "chatgpt-image-gen", "AI se image bana do / photo generate karo bole to AI app se sub-agent banwa ke download karna")
    )

    val activeCount = enabledSet.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SettingsTopBar(
            title = "Skills",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
        ) {
            item {
                Text(
                    text = "Installed skills",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "$activeCount/25 — enabled ones go into Anu's context",
                    fontSize = 11.5.sp,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(8.dp))
            }

            // List of Base Installed Skills
            items(baseInstalledSkills, key = { it.id }) { skill ->
                val isChecked = enabledSet.contains(skill.id)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = skill.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.chipBackground
                                ) {
                                    Text(
                                        text = "store",
                                        fontSize = 9.sp,
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = skill.description,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                lineHeight = 15.sp
                            )
                        }

                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = isChecked,
                            onCheckedChange = {
                                store.toggleSkill(skill.id)
                                enabledSet = store.enabledSkills
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accentPrimary,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.chipBackground
                            )
                        )
                    }
                }
            }

            // Installed Store Skills (if any)
            items(availableStoreSkills.filter { installedStoreSkills.contains(it.id) }, key = { "inst_${it.id}" }) { skill ->
                val isChecked = enabledSet.contains(skill.id)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, colors.accentPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = skill.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.accentPrimary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "installed",
                                        fontSize = 9.sp,
                                        color = colors.accentPrimary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = skill.description,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                lineHeight = 15.sp
                            )
                        }

                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = isChecked,
                            onCheckedChange = {
                                store.toggleSkill(skill.id)
                                enabledSet = store.enabledSkills
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accentPrimary,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.chipBackground
                            )
                        )
                    }
                }
            }

            // Skill Store Header
            item {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoAwesome, null, tint = colors.accentPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Skill store", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        }
                        Text(
                            text = "github.com/HunterIsLive-1/mobile-anu-skills",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val uninstalledCount = availableStoreSkills.count { !installedStoreSkills.contains(it.id) }
                            Text(
                                text = "$uninstalledCount skills available",
                                fontSize = 11.5.sp,
                                color = colors.textSecondary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        availableStoreSkills.forEach { store.installStoreSkill(it.id) }
                                        installedStoreSkills = store.installedStoreSkills
                                        enabledSet = store.enabledSkills
                                        Toast.makeText(context, "All skills installed!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Install all", color = colors.accentPrimary, fontSize = 12.sp)
                                }

                                TextButton(
                                    onClick = {
                                        Toast.makeText(context, "Refreshed skill store catalog", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Refresh", color = colors.textSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Uninstalled Store Skills List
            items(availableStoreSkills.filter { !installedStoreSkills.contains(it.id) }, key = { "store_${it.id}" }) { skill ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.cardBackground,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = skill.title,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = skill.description,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                lineHeight = 15.sp
                            )
                        }

                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                store.installStoreSkill(skill.id)
                                installedStoreSkills = store.installedStoreSkills
                                enabledSet = store.enabledSkills
                                Toast.makeText(context, "Installed ${skill.title}", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Install", fontSize = 11.5.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
