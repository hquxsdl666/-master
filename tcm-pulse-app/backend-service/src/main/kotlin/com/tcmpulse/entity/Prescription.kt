package com.tcmpulse.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "prescriptions", indexes = [
    Index(name = "idx_name", columnList = "name"),
    Index(name = "idx_category", columnList = "category"),
    Index(name = "idx_is_classic", columnList = "is_classic")
])
data class Prescription(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    // 基本信息
    @Column(name = "name", length = 100, nullable = false)
    val name: String,
    
    @Column(name = "pinyin", length = 100)
    val pinyin: String? = null,
    
    @Column(name = "alias", length = 200)
    val alias: String? = null,
    
    @Column(name = "category", length = 50)
    val category: String? = null, // 解表剂/清热剂等
    
    // 来源
    @Column(name = "source_book", length = 100)
    val sourceBook: String? = null,
    
    @Column(name = "source_dynasty", length = 20)
    val sourceDynasty: String? = null,
    
    @Column(name = "source_author", length = 50)
    val sourceAuthor: String? = null,
    
    @Column(name = "original_text", columnDefinition = "TEXT")
    val originalText: String? = null,
    
    // 组成
    @Column(name = "composition", columnDefinition = "TEXT", nullable = false)
    val composition: String, // JSON格式
    
    @Column(name = "total_herbs")
    val totalHerbs: Int? = null,
    
    // 用法
    @Column(name = "preparation", columnDefinition = "TEXT")
    val preparation: String? = null,
    
    @Column(name = "dosage", columnDefinition = "TEXT")
    val dosage: String? = null,
    
    @Column(name = "duration", length = 50)
    val duration: String? = null,
    
    // 功效主治
    @Column(name = "efficacy", columnDefinition = "TEXT", nullable = false)
    val efficacy: String,
    
    @Column(name = "indications", columnDefinition = "TEXT", nullable = false)
    val indications: String,
    
    @Column(name = "symptoms", columnDefinition = "TEXT")
    val symptoms: String? = null,
    
    @Column(name = "tongue_pulse", columnDefinition = "TEXT")
    val tonguePulse: String? = null,
    
    // 禁忌
    @Column(name = "contraindications", columnDefinition = "TEXT")
    val contraindications: String? = null,
    
    @Column(name = "precautions", columnDefinition = "TEXT")
    val precautions: String? = null,
    
    @Column(name = "side_effects", columnDefinition = "TEXT")
    val sideEffects: String? = null,
    
    // 方解
    @Column(name = "analysis", columnDefinition = "TEXT")
    val analysis: String? = null,
    
    @Column(name = "modifications", columnDefinition = "TEXT")
    val modifications: String? = null, // JSON格式
    
    // 现代应用
    @Column(name = "modern_applications", columnDefinition = "TEXT")
    val modernApplications: String? = null,
    
    @Column(name = "clinical_studies", columnDefinition = "TEXT")
    val clinicalStudies: String? = null,
    
    // 元数据
    @Column(name = "authority_score", precision = 3, scale = 2)
    val authorityScore: BigDecimal = BigDecimal("0.80"),
    
    @Column(name = "usage_frequency")
    val usageFrequency: Int = 0,
    
    @Column(name = "is_classic")
    val isClassic: Boolean = false,
    
    @Column(name = "is_official")
    val isOfficial: Boolean = false,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
