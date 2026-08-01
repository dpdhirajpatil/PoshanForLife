package com.poshanforlife.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import java.util.Locale
import kotlin.math.abs

/**
 * One metric's trend line over time, plus its latest value and the server-computed
 * delta versus the previous reading.
 *
 * [values] must already be in chronological (oldest-first) order — the backend's
 * `GET /health-records/{patientId}` returns them that way, so callers pass the
 * response through unchanged rather than sorting it again. [latestDelta] is the
 * backend's own `*Delta` field; never recompute it client-side.
 */
@Composable
fun HealthTrendChart(
    label: String,
    unit: String,
    values: List<Double>,
    latestDelta: Double?,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    // A single point draws nothing useful, so treat it the same as no data at all.
    if (values.size < 2) {
        EmptyTrend(label = label, modifier = modifier)
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        modelProducer.runTransaction { lineSeries { series(values) } }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${formatValue(values.last())} $unit",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (latestDelta != null && latestDelta != 0.0) {
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(text = formatDelta(latestDelta, unit), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(fill = LineCartesianLayer.LineFill.single(fill(lineColor))),
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }
}

@Composable
private fun EmptyTrend(label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Not enough readings yet to show a trend.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatValue(value: Double): String = String.format(Locale.US, "%.1f", value)

/** Renders the backend's delta with an explicit sign, e.g. "▼ 0.4 kg since last reading". */
private fun formatDelta(delta: Double, unit: String): String {
    val arrow = if (delta > 0) "▲" else "▼"
    return "$arrow ${formatValue(abs(delta))} $unit"
}
