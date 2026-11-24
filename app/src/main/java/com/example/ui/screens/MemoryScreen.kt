package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.localization.AppStrings
import com.example.model.MemoryCategory
import com.example.model.MemoryItem
import com.example.model.UiState
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NavyOutline
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SlateText

@Composable
fun MemoryScreen(
    strings: AppStrings,
    memoryEnabled: Boolean,
    memoriesState: UiState<List<MemoryItem>>,
    onToggleMemoryEnabled: (Boolean) -> Unit,
    onAddMemory: (String, String, MemoryCategory, Boolean) -> Unit,
    onUpdateMemory: (String, String, String, MemoryCategory, Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("memory_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.memoryVault,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.memoryVaultDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateText
                    )
                }
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                    modifier = Modifier.testTag("add_memory_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.addMemory)
                }
            }
        }

        // Memory On/Off Master Control
        item {
            Surface(
                color = NavySurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NavyOutline, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.memoryEnabledLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (memoryEnabled) {
                                "Approved memories personalize future conversations."
                            } else {
                                "Paused. SoulMate AI will not use saved memories."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateText
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = memoryEnabled,
                        onCheckedChange = onToggleMemoryEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = BrandCyan
                        ),
                        modifier = Modifier.testTag("memory_enabled_switch")
                    )
                }
            }
        }

        when (memoriesState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandCyan)
                    }
                }
            }

            is UiState.Error -> {
                item {
                    Surface(
                        color = ErrorCoral.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = memoriesState.message,
                            color = ErrorCoral,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val itemsList = memoriesState.data
                if (itemsList.isEmpty()) {
                    item {
                        Surface(
                            color = NavySurfaceElevated,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NavyOutline, RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Psychology,
                                    contentDescription = null,
                                    tint = BrandCyan,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "No memories saved yet. Add preferences or goals you want SoulMate AI to remember.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SlateText
                                )
                            }
                        }
                    }
                } else {
                    items(itemsList, key = { it.id }) { mem ->
                        MemoryCard(
                            memory = mem,
                            onEdit = { editingMemory = mem },
                            onDelete = { onDeleteMemory(mem.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MemoryEditorDialog(
            titleText = strings.addMemory,
            initialTitle = "",
            initialContent = "",
            initialCategory = MemoryCategory.PREFERENCE,
            initialSensitive = false,
            strings = strings,
            onDismiss = { showAddDialog = false },
            onSave = { t, c, cat, sens ->
                onAddMemory(t, c, cat, sens)
                showAddDialog = false
            }
        )
    }

    editingMemory?.let { mem ->
        MemoryEditorDialog(
            titleText = strings.edit,
            initialTitle = mem.title,
            initialContent = mem.content,
            initialCategory = MemoryCategory.fromKey(mem.category),
            initialSensitive = mem.sensitive,
            strings = strings,
            onDismiss = { editingMemory = null },
            onSave = { t, c, cat, sens ->
                onUpdateMemory(mem.id, t, c, cat, sens)
                editingMemory = null
            }
        )
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = NavySurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NavyOutline, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = BrandPurple.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = memory.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (memory.sensitive) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Sensitive memory",
                            tint = ErrorCoral,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit memory", tint = SlateText, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete memory", tint = ErrorCoral, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Text(
                text = memory.title,
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite
            )
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                color = SlateText
            )
        }
    }
}

@Composable
private fun MemoryEditorDialog(
    titleText: String,
    initialTitle: String,
    initialContent: String,
    initialCategory: MemoryCategory,
    initialSensitive: Boolean,
    strings: AppStrings,
    onDismiss: () -> Unit,
    onSave: (String, String, MemoryCategory, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf(initialCategory) }
    var sensitive by remember { mutableStateOf(initialSensitive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 100) title = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 500) content = it },
                    label = { Text("What should SoulMate remember?") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemoryCategory.entries.forEach { cat ->
                        val active = cat == category
                        Surface(
                            color = if (active) BrandPurple.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, if (active) BrandCyan else NavyOutline, RoundedCornerShape(10.dp))
                                .clickable { category = cat }
                        ) {
                            Text(
                                text = cat.key.take(4),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { sensitive = !sensitive }
                ) {
                    Checkbox(checked = sensitive, onCheckedChange = { sensitive = it })
                    Text(
                        text = "Contains personal/sensitive details",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = content.isNotBlank(),
                onClick = {
                    onSave(title.ifBlank { "Saved Note" }, content, category, sensitive)
                }
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
