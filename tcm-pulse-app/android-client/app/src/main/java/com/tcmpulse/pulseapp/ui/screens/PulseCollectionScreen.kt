package com.tcmpulse.pulseapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcmpulse.pulseapp.data.model.PulseCollectionState
import com.tcmpulse.pulseapp.data.model.ScannedDeviceInfo
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseCollectionScreen(
    collectionState: PulseCollectionState,
    onStartCollection: () -> Unit,
    onCancelCollection: () -> Unit,
    onBack: () -> Unit,
    onViewReport: () -> Unit = onBack,
    onDeviceSelect: (String) -> Unit = {},
    waveformData: List<Float> = emptyList()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("脉诊采集", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (collectionState) {
                is PulseCollectionState.Idle -> {
                    IdleView(onStartCollection = onStartCollection)
                }
                is PulseCollectionState.Scanning -> {
                    ScanningView(
                        devices = collectionState.devices,
                        onDeviceSelect = onDeviceSelect,
                        onCancel = onCancelCollection
                    )
                }
                is PulseCollectionState.Connecting -> {
                    ConnectingView(onCancel = onCancelCollection)
                }
                is PulseCollectionState.Collecting,
                is PulseCollectionState.Progress -> {
                    CollectingView(
                        state = collectionState,
                        waveformData = waveformData,
                        onCancel = onCancelCollection
                    )
                }
                is PulseCollectionState.Analyzing -> {
                    AnalyzingView()
                }
                is PulseCollectionState.Success -> {
                    SuccessView(
                        result = collectionState.result,
                        onViewReport = onViewReport
                    )
                }
                is PulseCollectionState.Error -> {
                    ErrorView(
                        message = collectionState.message,
                        onRetry = onStartCollection,
                        onCancel = onCancelCollection
                    )
                }
            }
        }
    }
}

@Composable
fun IdleView(onStartCollection: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "准备开始脉诊",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "请确保华为 WATCH GT4 已佩戴\n并已在手表上开启「心率广播」",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStartCollection,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("开始采集", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "采集提示",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                TipItem("保持安静，避免剧烈运动")
                TipItem("手表紧贴手腕，不要过松")
                TipItem("采集过程中保持手臂平稳")
                TipItem("全程约需60秒")
            }
        }
    }
}

@Composable
fun TipItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * 扫描界面：列出已发现的蓝牙设备，用户点击后 GATT 连接；
 * 若手表心率广播已经被检测到，会自动跳过此界面直接开始采集。
 */
@Composable
fun ScanningView(
    devices: List<ScannedDeviceInfo>,
    onDeviceSelect: (String) -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 标题行
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text("正在搜索设备...", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "点击设备连接；或等待心率广播自动检测",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (devices.isEmpty()) {
            // 尚未发现设备 — 引导卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("未发现设备，请检查以下设置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "① 确保手表已通过「华为运动健康 App」配对\n" +
                        "② 手表蓝牙已开启\n\n" +
                        "如何开启心率广播：\n" +
                        "手表：设置 → 健康监测 → 心率广播 → 开启\n" +
                        "或 App：设备 → 健康管理 → 心率 → 心率广播 → 开启",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        } else {
            // 设备列表
            Text(
                text = "已发现 ${devices.size} 个设备",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 6.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    DeviceItem(device = device, onClick = { onDeviceSelect(device.address) })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 心率广播引导（折叠卡片）
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("如何开启心率广播", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "手表：设置 → 健康监测 → 心率广播 → 开启\n" +
                        "（开启后 App 可自动接收数据，无需手动选择）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) { Text("取消") }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DeviceItem(device: ScannedDeviceInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Watch,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (device.isBonded) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (device.isBonded) {
                        Text(
                            text = "已配对",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "RSSI: ${device.rssi} dBm",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "连接",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * GATT 连接中的等待界面
 */
@Composable
fun ConnectingView(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(80.dp), strokeWidth = 6.dp)

        Spacer(modifier = Modifier.height(28.dp))

        Text("正在连接手表...", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "请在手表上确认配对请求\n若提示占用，请先退出华为运动健康 App",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) { Text("取消") }
    }
}

@Composable
fun CollectingView(
    state: PulseCollectionState,
    waveformData: List<Float>,
    onCancel: () -> Unit
) {
    val progress = if (state is PulseCollectionState.Progress) state.percent else 0
    val quality  = if (state is PulseCollectionState.Progress) state.quality else 0f
    val bpm      = if (state is PulseCollectionState.Progress) state.bpm else 0

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // 波形显示区域
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E)
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (waveformData.isNotEmpty()) {
                        val path = Path()
                        val stepX = size.width / (waveformData.size - 1)
                        waveformData.forEachIndexed { index, value ->
                            val x = index * stepX
                            val y = size.height / 2 + value * size.height / 4
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF00E676),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // 信号质量指示
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = when {
                                    quality > 0.8f -> Color(0xFF00E676)
                                    quality > 0.5f -> Color(0xFFFFA726)
                                    else -> Color(0xFFEF5350)
                                },
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            quality > 0.8f -> "信号良好"
                            quality > 0.5f -> "信号一般"
                            else -> "信号较差"
                        },
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 实时心率 + 进度
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "$progress%", fontSize = 42.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text(text = "进度", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            if (bpm > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null,
                            modifier = Modifier.size(18.dp), tint = Color(0xFFE53935))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "$bpm", fontSize = 42.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53935))
                    }
                    Text(text = "BPM", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = progress / 100f,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "正在采集脉象数据...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "请保持手腕平稳，不要移动",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("取消采集")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun AnalyzingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            strokeWidth = 6.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "正在分析脉象...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AI正在识别您的脉象特征",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun SuccessView(
    result: com.tcmpulse.pulseapp.data.model.PulseAnalysisResponse,
    onViewReport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = Color(0xFF4CAF50)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "脉诊完成",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "主要脉象",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.mainPulse,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "置信度: ${(result.mainPulseConfidence.toDouble() * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${result.pulseRate}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "次/分",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }

                    result.syndrome?.let {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = it,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "辨证结果",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onViewReport,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("查看完整报告", fontSize = 18.sp)
        }
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFFEF5350)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "采集失败",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("重新尝试")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("返回")
        }
    }
}
