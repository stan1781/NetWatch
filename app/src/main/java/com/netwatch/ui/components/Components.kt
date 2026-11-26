package com.netwatch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.netwatch.domain.model.DataConfidence
import com.netwatch.domain.model.DataScope

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun ConfidenceChip(confidence: DataConfidence, scope: DataScope? = null) {
    val label = when (confidence) {
        DataConfidence.REAL -> "实测"
        DataConfidence.ESTIMATED -> "估算"
        DataConfidence.UNAVAILABLE -> "不可用"
    } + when (scope) {
        DataScope.DEVICE_WIDE -> " · 全设备"
        DataScope.ATTRIBUTED_TO_DEFAULT_DATA_SIM -> " · 按默认数据卡归属"
        DataScope.PER_APP -> " · 按应用"
        DataScope.HOTSPOT_SESSION -> " · 热点会话"
        DataScope.UNKNOWN, null -> ""
    }
    SuggestionChip(onClick = {}, enabled = false, label = { Text(label) })
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NoteText(notes: List<String>) {
    if (notes.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    notes.forEach { note ->
        Text(text = note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
