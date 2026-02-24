package com.tcmpulse.pulseapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcmpulse.pulseapp.data.model.PulseRecordDetail
import com.tcmpulse.pulseapp.data.model.PrescriptionRecommendation
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseReportScreen(
    record: PulseRecordDetail?,
    recommendations: List<PrescriptionRecommendation>,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onViewPrescription: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("脉诊报告", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (record == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 报告头部信息
                item {
                    ReportHeader(record = record)
                }
                
                // 脉象分析结果
                item {
                    PulseAnalysisCard(record = record)
                }
                
                // 脉象特征详情
                item {
                    PulseFeaturesCard(features = record.pulseFeatures)
                }
                
                // 辨证分析
                record.syndrome?.let { syndrome ->
                    item {
                        SyndromeAnalysisCard(
                            syndrome = syndrome,
                            confidence = record.syndromeConfidence
                        )
                    }
                }
                
                // 方剂推荐
                if (recommendations.isNotEmpty()) {
                    item {
                        Text(
                            text = "推荐方剂",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    items(recommendations) { recommendation ->
                        PrescriptionCard(
                            recommendation = recommendation,
                            onClick = { onViewPrescription(recommendation.prescriptionId) }
                        )
                    }
                }
                
                // 健康建议
                item {
                    HealthAdviceCard()
                }
            }
        }
    }
}

@Composable
fun ReportHeader(record: PulseRecordDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "检测时间",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = record.recordTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        fontSize = 14.sp
                    )
                }
                
                record.signalQuality?.let { quality ->
                    val qualityText = when {
                        quality.toDouble() > 0.8 -> "优"
                        quality.toDouble() > 0.6 -> "良"
                        else -> "一般"
                    }
                    val qualityColor = when {
                        quality.toDouble() > 0.8 -> Color(0xFF4CAF50)
                        quality.toDouble() > 0.6 -> Color(0xFFFFA726)
                        else -> Color(0xFFEF5350)
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "信号质量",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = qualityText,
                            fontSize = 14.sp,
                            color = qualityColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "检测设备",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "华为WATCH GT4",
                        fontSize = 14.sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "采集时长",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${record.measurementDuration}秒",
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PulseAnalysisCard(record: PulseRecordDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "脉象分析结果",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 主要脉象
            Text(
                text = record.mainPulse,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            record.secondaryPulse?.let { secondary ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "兼$secondary",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PulseStatItem("脉率", "${record.pulseRate}次/分")
                PulseStatItem("脉位", getPulsePositionText(record.pulseFeatures.position))
                PulseStatItem("脉力", getPulseForceText(record.pulseFeatures.force.forceScore))
            }
        }
    }
}

@Composable
fun PulseStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun PulseFeaturesCard(features: com.tcmpulse.pulseapp.data.model.PulseFeatures) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "脉象特征详情",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 脉位
            FeatureRow("脉位", listOf(
                "浮" to features.position.floating.toDouble(),
                "中" to features.position.normal.toDouble(),
                "沉" to features.position.deep.toDouble()
            ))
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 脉力
            FeatureProgressBar("脉力", features.force.forceScore.toDouble())
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 脉形
            FeatureProgressBar("脉宽", features.shape.widthScore.toDouble())
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 光滑度
            FeatureProgressBar("光滑度", features.shape.smoothness.toDouble())
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 紧张度
            FeatureProgressBar("紧张度", features.shape.tautness.toDouble())
        }
    }
}

@Composable
fun FeatureRow(label: String, items: List<Pair<String, Double>>) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            items.forEach { (name, value) ->
                val alpha = (0.3f + value * 0.7f).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        color = if (value > 0.5) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureProgressBar(label: String, value: Double) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "${(value * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = value.toFloat().coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun SyndromeAnalysisCard(
    syndrome: String,
    confidence: java.math.BigDecimal?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "辨证分析",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE65100)
                )
                
                confidence?.let {
                    Text(
                        text = "置信度 ${(it.toDouble() * 100).toInt()}%",
                        fontSize = 14.sp,
                        color = Color(0xFFE65100).copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = syndrome,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = getSyndromeDescription(syndrome),
                fontSize = 14.sp,
                color = Color(0xFFE65100).copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun PrescriptionCard(
    recommendation: PrescriptionRecommendation,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recommendation.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFFA726)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${(recommendation.matchScore.toDouble() * 100).toInt()}%",
                        fontSize = 14.sp,
                        color = Color(0xFFFFA726)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = recommendation.efficacy,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2
            )
            
            if (recommendation.composition.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "组成: ${recommendation.composition.take(4).joinToString(", ") { it.herb }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun HealthAdviceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F5E9)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "健康建议",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2E7D32)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            AdviceItem("饮食调理", "饮食宜清淡，避免辛辣油腻食物")
            AdviceItem("生活起居", "保持规律作息，避免熬夜")
            AdviceItem("情绪调节", "保持心情舒畅，避免情绪波动")
            AdviceItem("运动建议", "适当进行舒缓运动，如太极、散步")
        }
    }
}

@Composable
fun AdviceItem(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2E7D32)
        )
        Text(
            text = content,
            fontSize = 13.sp,
            color = Color(0xFF2E7D32).copy(alpha = 0.8f)
        )
    }
}

// 辅助函数
private fun getPulsePositionText(position: com.tcmpulse.pulseapp.data.model.PositionFeatures): String {
    return when {
        position.floating.toDouble() > 0.6 -> "浮"
        position.deep.toDouble() > 0.6 -> "沉"
        else -> "中"
    }
}

private fun getPulseForceText(forceScore: java.math.BigDecimal): String {
    return when {
        forceScore.toDouble() > 0.7 -> "强"
        forceScore.toDouble() < 0.3 -> "弱"
        else -> "中"
    }
}

private fun getSyndromeDescription(syndrome: String): String {
    return when (syndrome) {
        "肝郁气滞" -> "情志不畅，肝气郁结，气机阻滞"
        "气虚证" -> "元气不足，脏腑功能减退"
        "血虚证" -> "血液亏虚，脏腑经络失养"
        "阴虚证" -> "阴液不足，阴虚阳亢"
        "阳虚证" -> "阳气不足，温煦功能减退"
        "痰湿证" -> "痰湿内停，阻滞气机"
        "血瘀证" -> "血液运行不畅，瘀阻脉络"
        else -> "根据脉象特征综合分析所得"
    }
}
