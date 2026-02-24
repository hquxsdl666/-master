package com.tcmpulse.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "syndromes", indexes = [
    Index(name = "idx_syndrome_name", columnList = "name"),
    Index(name = "idx_syndrome_category", columnList = "category")
])
data class Syndrome(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    // 基本信息
    @Column(name = "name", length = 100, nullable = false)
    val name: String,
    
    @Column(name = "pinyin", length = 100)
    val pinyin: String? = null,
    
    @Column(name = "category", length = 50)
    val category: String? = null, // 八纲/脏腑/气血津液等
    
    // 诊断要点
    @Column(name = "main_symptoms", columnDefinition = "TEXT")
    val mainSymptoms: String? = null,
    
    @Column(name = "secondary_symptoms", columnDefinition = "TEXT")
    val secondarySymptoms: String? = null,
    
    @Column(name = "tongue", columnDefinition = "TEXT")
    val tongue: String? = null,
    
    @Column(name = "pulse", columnDefinition = "TEXT")
    val pulse: String? = null,
    
    // 病机
    @Column(name = "pathogenesis", columnDefinition = "TEXT")
    val pathogenesis: String? = null,
    
    @Column(name = "location", length = 100)
    val location: String? = null, // 病位
    
    @Column(name = "nature", length = 50)
    val nature: String? = null, // 病性
    
    // 治法
    @Column(name = "treatment_principle", columnDefinition = "TEXT")
    val treatmentPrinciple: String? = null,
    
    @Column(name = "common_prescriptions", columnDefinition = "TEXT")
    val commonPrescriptions: String? = null, // JSON格式
    
    @Column(name = "common_herbs", columnDefinition = "TEXT")
    val commonHerbs: String? = null, // JSON格式
    
    // 鉴别诊断
    @Column(name = "differentiation", columnDefinition = "TEXT")
    val differentiation: String? = null,
    
    @Column(name = "similar_syndromes", columnDefinition = "TEXT")
    val similarSyndromes: String? = null, // JSON格式
    
    // 转归预后
    @Column(name = "prognosis", columnDefinition = "TEXT")
    val prognosis: String? = null,
    
    @Column(name = "complications", columnDefinition = "TEXT")
    val complications: String? = null, // JSON格式
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
