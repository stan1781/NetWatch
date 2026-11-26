package com.netwatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.netwatch.domain.model.SpeedSample

@Composable
fun SpeedChart(
    samples: List<SpeedSample>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val data = samples.takeLast(60)
        if (data.size < 2) return@Canvas
        val max = data.maxOf { maxOf(it.displayDownloadBytesPerSec, it.displayUploadBytesPerSec, 1L) }.toFloat()
        val step = size.width / (data.size - 1).coerceAtLeast(1)
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        fun path(selector: (SpeedSample) -> Long): Path {
            val p = Path()
            data.forEachIndexed { index, sample ->
                val x = index * step
                val y = size.height - (selector(sample) / max) * size.height
                if (index == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        drawPath(path { it.displayDownloadBytesPerSec }, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawPath(path { it.displayUploadBytesPerSec }, secondary, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}
