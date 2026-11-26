package com.netwatch.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.netwatch.MainActivity

class NetWatchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataProvider.load(context)
        provideContent {
            GlanceTheme {
                WidgetContent(data)
            }
        }
    }
}

@Composable
private fun WidgetContent(data: WidgetUiData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "NetWatch",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(text = data.defaultSimLabel, style = body())
        Text(text = data.carrier, style = caption())
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(text = data.downloadSpeed, style = speed(), modifier = GlanceModifier.defaultWeight())
            Text(text = data.uploadSpeed, style = speed(), modifier = GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(text = data.todayMobile, style = body())
        Text(text = data.planLine, style = body())
        Spacer(GlanceModifier.height(4.dp))
        Text(text = data.confidenceNote, style = caption())
    }
}

@Composable
private fun body() = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp)

@Composable
private fun caption() = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)

@Composable
private fun speed() = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
)
