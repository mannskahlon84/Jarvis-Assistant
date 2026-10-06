package com.example.jarvis_personalassistant.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvis_personalassistant.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsReferenceScreen(
    onBackClick: () -> Unit
) {
    val categories = remember {
        listOf(
            CategoryData(
                title = "Basics and navigation",
                description = "System controls and basic screen navigation",
                commands = listOf(
                    CommandItem("go back", "Return to the previous screen"),
                    CommandItem("go home", "Navigate to your home screen"),
                    CommandItem("open [app]", "Start any application (e.g. 'open whatsapp')"),
                    CommandItem("open notifications", "Pull down the notifications shade"),
                    CommandItem("quick settings", "Open the Quick Settings panel"),
                    CommandItem("recent apps", "Open recent applications list"),
                    CommandItem("take screenshot", "Capture the current screen"),
                    CommandItem("lock screen", "Lock your device screen"),
                    CommandItem("open power menu", "Open the system power dialog"),
                    CommandItem("stop listening", "Pause active assistant microphone"),
                    CommandItem("volume up", "Increase system media volume"),
                    CommandItem("volume down", "Decrease system media volume"),
                    CommandItem("mute", "Mute system media volume"),
                    CommandItem("unmute", "Unmute system media volume"),
                    CommandItem("turn on bluetooth", "Open Bluetooth configurations settings")
                )
            ),
            CategoryData(
                title = "Gestures",
                description = "Perform screen taps, clicks, checkboxes, and swipes",
                commands = listOf(
                    CommandItem("click [text]", "Press any labeled button or item (e.g. 'click Search')"),
                    CommandItem("tap [text]", "Synonym for clicking on screen text"),
                    CommandItem("long press [text]", "Perform long-press on screen item"),
                    CommandItem("check [text]", "Enable a checkbox or switch"),
                    CommandItem("uncheck [text]", "Disable a checkbox or switch"),
                    CommandItem("scroll down / up", "Swipe vertically on scrollable screen"),
                    CommandItem("scroll left / right", "Swipe horizontally on scrollable screen"),
                    CommandItem("swipe [direction] edge", "Swipe from top, bottom, left or right edge")
                )
            ),
            CategoryData(
                title = "Grid selection",
                description = "Interactive pixel-coordinate grids",
                commands = listOf(
                    CommandItem("show grid", "Display numbered coordinate selection grid overlay"),
                    CommandItem("hide grid", "Hide the coordinate selection grid overlay"),
                    CommandItem("[number]", "Zoom grid onto specific cell (e.g. '8')"),
                    CommandItem("tap [number]", "Tap coordinates at cell center"),
                    CommandItem("long press [number]", "Long click coordinates at cell center"),
                    CommandItem("swipe [direction] [number]", "Swipe starting from cell center")
                )
            ),
            CategoryData(
                title = "Text editing",
                description = "Dictate, replace, delete and style text inputs",
                commands = listOf(
                    CommandItem("type [text]", "Write text into active input field"),
                    CommandItem("delete all text", "Clear the entire text field"),
                    CommandItem("delete [word]", "Remove target word from text"),
                    CommandItem("delete selected text", "Delete currently selected text block"),
                    CommandItem("delete to the beginning", "Clear everything before cursor"),
                    CommandItem("delete to the end", "Clear everything after cursor"),
                    CommandItem("select all text", "Select entire input text"),
                    CommandItem("unselect text", "Deselect highlight"),
                    CommandItem("go to the beginning / end", "Move input cursor to start or end"),
                    CommandItem("replace [A] with [B]", "Replace word occurrences"),
                    CommandItem("uppercase / lowercase [A]", "Change target word case"),
                    CommandItem("capitalize [A]", "Capitalize first character of word"),
                    CommandItem("insert [text] before/after [word]", "Add text relative to anchor word")
                )
            ),
            CategoryData(
                title = "Magnification",
                description = "Zoom and pan accessibility viewports",
                commands = listOf(
                    CommandItem("start zooming", "Activate viewport screen magnifier"),
                    CommandItem("zoom in / out", "Change magnification scales"),
                    CommandItem("stop zooming", "Deactivate viewport magnifier"),
                    CommandItem("pan up / down / left / right", "Shift magnified viewport focus")
                )
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Voice Commands Guide",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = JarvisTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = JarvisPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JarvisBackground)
            )
        },
        containerColor = JarvisBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(categories) { category ->
                CategoryRow(category)
            }
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryData) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, JarvisPrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.title,
                        color = JarvisPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.description,
                        color = JarvisTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    color = JarvisPrimary,
                    fontSize = 10.sp
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    category.commands.forEach { command ->
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = JarvisTextSecondary.copy(alpha = 0.15f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "\"${command.phrase}\"",
                                color = JarvisTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1.2f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = command.description,
                                color = JarvisTextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class CategoryData(
    val title: String,
    val description: String,
    val commands: List<CommandItem>
)

data class CommandItem(
    val phrase: String,
    val description: String
)
