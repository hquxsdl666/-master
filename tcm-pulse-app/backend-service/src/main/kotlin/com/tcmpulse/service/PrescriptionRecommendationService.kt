package com.tcmpulse.service

import com.tcmpulse.controller.PrescriptionRecommendationRequest
import com.tcmpulse.controller.PrescriptionRecommendationResponse
import com.tcmpulse.controller.PrescriptionRecommendationDto
import com.tcmpulse.controller.HerbDosageDto
import com.tcmpulse.entity.Prescription
import com.tcmpulse.repository.PrescriptionRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PrescriptionRecommendationService(
    private val prescriptionRepository: PrescriptionRepository
) {
    
    // 证型与方剂的映射关系
    private val syndromePrescriptionMap = mapOf(
        "表证" to listOf("桂枝汤", "麻黄汤", "银翘散"),
        "里实热证" to listOf("白虎汤", "黄连解毒汤", "大承气汤"),
        "里虚寒证" to listOf("理中丸", "四逆汤", "小建中汤"),
        "气虚证" to listOf("四君子汤", "补中益气汤", "生脉散"),
        "血虚证" to listOf("四物汤", "归脾汤", "当归补血汤"),
        "阴虚证" to listOf("六味地黄丸", "知柏地黄丸", "大补阴丸"),
        "阳虚证" to listOf("金匮肾气丸", "右归丸", "附子理中丸"),
        "气滞证" to listOf("柴胡疏肝散", "逍遥散", "越鞠丸"),
        "血瘀证" to listOf("血府逐瘀汤", "桃红四物汤", "失笑散"),
        "痰湿证" to listOf("二陈汤", "温胆汤", "半夏白术天麻汤"),
        "肝郁气滞" to listOf("柴胡疏肝散", "逍遥散", "四逆散"),
        "肝阳上亢" to listOf("天麻钩藤饮", "镇肝熄风汤", "龙胆泻肝汤"),
        "心火亢盛" to listOf("导赤散", "黄连解毒汤", "朱砂安神丸"),
        "脾胃虚弱" to listOf("四君子汤", "参苓白术散", "香砂六君子汤"),
        "肾气不足" to listOf("金匮肾气丸", "五子衍宗丸", "龟鹿二仙胶")
    )
    
    // 脉象与方剂的映射关系
    private val pulsePrescriptionMap = mapOf(
        "浮脉" to listOf("桂枝汤", "麻黄汤", "桑菊饮"),
        "沉脉" to listOf("四逆汤", "理中丸", "真武汤"),
        "迟脉" to listOf("理中丸", "附子理中丸", "四逆汤"),
        "数脉" to listOf("白虎汤", "黄连解毒汤", "清营汤"),
        "虚脉" to listOf("四君子汤", "四物汤", "八珍汤"),
        "实脉" to listOf("大承气汤", "小承气汤", "调胃承气汤"),
        "滑脉" to listOf("二陈汤", "温胆汤", "保和丸"),
        "涩脉" to listOf("血府逐瘀汤", "桃红四物汤", "生化汤"),
        "弦脉" to listOf("柴胡疏肝散", "逍遥散", "天麻钩藤饮"),
        "紧脉" to listOf("麻黄汤", "桂枝汤", "小青龙汤"),
        "洪脉" to listOf("白虎汤", "清营汤", "犀角地黄汤"),
        "细脉" to listOf("四物汤", "归脾汤", "当归补血汤"),
        "濡脉" to listOf("香薷散", "新加香薷饮", "藿朴夏苓汤"),
        "弱脉" to listOf("十全大补汤", "人参养荣汤", "当归补血汤"),
        "微脉" to listOf("四逆汤", "参附汤", "生脉散"),
        "缓脉" to listOf("香砂六君子汤", "参苓白术散", "补中益气汤")
    )
    
    fun recommend(request: PrescriptionRecommendationRequest): PrescriptionRecommendationResponse {
        val syndrome = request.syndrome
        val mainPulse = request.mainPulse
        val secondaryPulse = request.secondaryPulse
        
        // 获取候选方剂
        val candidateNames = mutableSetOf<String>()
        
        // 根据证型获取方剂
        syndrome?.let {
            syndromePrescriptionMap[it]?.let { names -> candidateNames.addAll(names) }
        }
        
        // 根据脉象获取方剂
        pulsePrescriptionMap[mainPulse]?.let { candidateNames.addAll(it) }
        secondaryPulse?.let { pulsePrescriptionMap[it]?.let { names -> candidateNames.addAll(names) } }
        
        // 如果没有匹配的方剂，返回默认方剂
        if (candidateNames.isEmpty()) {
            candidateNames.addAll(listOf("四君子汤", "四物汤", "逍遥散"))
        }
        
        // 从数据库获取方剂详情
        val prescriptions = prescriptionRepository.findByNameIn(candidateNames.toList())
        
        // 计算匹配分数并排序
        val scoredRecommendations = prescriptions.map { prescription ->
            val score = calculateMatchScore(prescription, syndrome, mainPulse, secondaryPulse)
            Pair(prescription, score)
        }.sortedByDescending { it.second }
        
        // 构建响应
        val recommendations = scoredRecommendations.take(3).mapIndexed { index, (prescription, score) ->
            mapToRecommendationDto(prescription, score, index + 1)
        }
        
        return PrescriptionRecommendationResponse(
            syndrome = syndrome,
            syndromeConfidence = if (syndrome != null) BigDecimal("0.85") else null,
            recommendations = recommendations
        )
    }
    
    private fun calculateMatchScore(
        prescription: Prescription,
        syndrome: String?,
        mainPulse: String,
        secondaryPulse: String?
    ): BigDecimal {
        var score = 0.5 // 基础分数
        
        // 证型匹配
        syndrome?.let {
            if (syndromePrescriptionMap[it]?.contains(prescription.name) == true) {
                score += 0.3
            }
        }
        
        // 主脉匹配
        if (pulsePrescriptionMap[mainPulse]?.contains(prescription.name) == true) {
            score += 0.2
        }
        
        // 兼脉匹配
        secondaryPulse?.let {
            if (pulsePrescriptionMap[it]?.contains(prescription.name) == true) {
                score += 0.1
            }
        }
        
        // 权威性加分
        score += prescription.authorityScore.toDouble() * 0.1
        
        return BigDecimal(score.coerceIn(0.0, 1.0)).setScale(2, RoundingMode.HALF_UP)
    }
    
    private fun mapToRecommendationDto(
        prescription: Prescription,
        score: BigDecimal,
        rank: Int
    ): PrescriptionRecommendationDto {
        // 解析组成JSON
        val composition = parseComposition(prescription.composition)
        
        return PrescriptionRecommendationDto(
            prescriptionId = prescription.id,
            name = prescription.name,
            composition = composition,
            efficacy = prescription.efficacy,
            matchScore = score,
            rank = rank
        )
    }
    
    private fun parseComposition(compositionJson: String): List<HerbDosageDto> {
        // 简化的JSON解析
        return try {
            // 这里应该使用JSON解析库
            listOf(
                HerbDosageDto("示例药材", "9g", "君")
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}
