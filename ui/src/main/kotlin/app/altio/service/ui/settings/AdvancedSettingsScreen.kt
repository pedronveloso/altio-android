/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.altio.service.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Immutable
data class BenchmarkRun(
    val id: String,
    val timestamp: Long,
    val modelId: String,
    val modelName: String,
    val accelerator: String,
    val maxTokens: Int,
    val avgLatencyMs: Double,
    val run2LatencyMs: Long,
    val run3LatencyMs: Long,
    val fallbackStatus: String,
    val memoryUsage: String,
)

@Immutable data class AdvancedBenchmarkRuns(val items: List<BenchmarkRun>)

@Composable
fun AdvancedSettingsScreen(
    cpuInfo: String,
    gpuInfo: String,
    ramInfo: String,
    isModelReady: Boolean,
    modelName: String?,
    isBenchmarking: Boolean,
    benchmarkProgress: String?,
    benchmarkRuns: AdvancedBenchmarkRuns,
    onStartBenchmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxHeight().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Device Info Console Header
      item {
        Text(
            text = "Device Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
            border =
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
        ) {
          Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            HardwareInfoRow(label = "CPU Hardware", value = cpuInfo)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            HardwareInfoRow(label = "GPU Hardware", value = gpuInfo)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            HardwareInfoRow(label = "System Memory RAM", value = ramInfo)
          }
        }
      }

      // 2. Inference Benchmark Panel
      item {
        Text(
            text = "Performance Benchmark",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            border =
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            if (modelName != null) {
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                    text = "Active Model",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = modelName,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        ),
                    color = MaterialTheme.colorScheme.primary,
                )
              }
            } else {
              Text(
                  text = "No active model downloaded & ready",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.error,
                  modifier = Modifier.align(Alignment.Start),
              )
            }

            Button(
                onClick = onStartBenchmark,
                enabled = isModelReady && !isBenchmarking,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
              if (isBenchmarking) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                  CircularProgressIndicator(
                      color = MaterialTheme.colorScheme.onPrimary,
                      modifier = Modifier.size(18.dp),
                      strokeWidth = 2.dp,
                  )
                  Text("Benchmarking...", fontWeight = FontWeight.Bold)
                }
              } else {
                Text("Run Inference Benchmark", fontWeight = FontWeight.Bold)
              }
            }

            benchmarkProgress?.let { progress ->
              Surface(
                  color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                  shape = MaterialTheme.shapes.small,
                  border =
                      androidx.compose.foundation.BorderStroke(
                          width = 1.dp,
                          color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                      ),
                  modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                    text = progress,
                    style =
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(8.dp),
                )
              }
            }
          }
        }
      }

      // 3. History Header
      item {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
              text = "Benchmark History",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.semantics { heading() },
          )
          Spacer(Modifier.height(4.dp))
          Text(
              text =
                  "Just because one model is faster than another, it doesn't mean that the quality of the results is better. This benchmark is for execution speed only.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontStyle = FontStyle.Italic,
          )
        }
      }

      // 4. History Logs List
      if (benchmarkRuns.items.isEmpty()) {
        item {
          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .border(
                          1.dp,
                          MaterialTheme.colorScheme.outlineVariant,
                          RoundedCornerShape(8.dp),
                      )
                      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                      .padding(vertical = 24.dp),
              contentAlignment = Alignment.Center,
          ) {
            Text(
                text = "No benchmark runs yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        items(benchmarkRuns.items, key = { it.id }) { run -> BenchmarkRunItem(run = run) }
      }
    }
  }
}

@Composable
private fun BenchmarkRunItem(run: BenchmarkRun) {
  var expanded by remember { mutableStateOf(false) }

  OutlinedCard(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium,
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (expanded) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                  } else {
                    MaterialTheme.colorScheme.surface
                  }
          ),
      border =
          androidx.compose.foundation.BorderStroke(
              width = 1.dp,
              color =
                  if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                  else MaterialTheme.colorScheme.outlineVariant,
          ),
      onClick = { expanded = !expanded },
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = run.modelName,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
          )
          Text(
              text = formatTimestamp(run.timestamp),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = String.format(java.util.Locale.US, "%.1f ms", run.avgLatencyMs),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
      }

      if (expanded) {
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          HardwareInfoRow(label = "Model ID", value = run.modelId)
          HardwareInfoRow(label = "Accelerator Used", value = run.accelerator)
          HardwareInfoRow(label = "Max Tokens Preference", value = run.maxTokens.toString())

          // Execution Latency details formatted nicely
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Execution Latency details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))

            // Console-style table for runs
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
            ) {
              Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text("Warmup Run (1st):", style = MaterialTheme.typography.bodySmall)
                  Text(
                      "Discarded",
                      style =
                          MaterialTheme.typography.bodySmall.copy(
                              fontFamily = FontFamily.Monospace
                          ),
                      fontWeight = FontWeight.Bold,
                  )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text("Run 2:", style = MaterialTheme.typography.bodySmall)
                  Text(
                      "${run.run2LatencyMs} ms",
                      style =
                          MaterialTheme.typography.bodySmall.copy(
                              fontFamily = FontFamily.Monospace
                          ),
                      fontWeight = FontWeight.Bold,
                  )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text("Run 3:", style = MaterialTheme.typography.bodySmall)
                  Text(
                      "${run.run3LatencyMs} ms",
                      style =
                          MaterialTheme.typography.bodySmall.copy(
                              fontFamily = FontFamily.Monospace
                          ),
                      fontWeight = FontWeight.Bold,
                  )
                }
              }
            }
          }

          HardwareInfoRow(label = "Acceleration Status", value = run.fallbackStatus)
          HardwareInfoRow(label = "Model Memory Usage", value = run.memoryUsage)
        }
      }
    }
  }
}

@Composable
private fun HardwareInfoRow(label: String, value: String) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

private val timestampFormatter =
    ThreadLocal.withInitial {
      java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    }

private fun formatTimestamp(timestampMs: Long): String {
  return timestampFormatter.get()!!.format(java.util.Date(timestampMs))
}
