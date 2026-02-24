package com.tcmpulse.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "users")
data class User(
    @Id
    @Column(name = "id", length = 36)
    val id: String = UUID.randomUUID().toString(),
    
    @Column(name = "phone", length = 20, unique = true)
    var phone: String? = null,
    
    @Column(name = "email", length = 100, unique = true)
    var email: String? = null,
    
    @Column(name = "password_hash", length = 255)
    var passwordHash: String? = null,
    
    @Column(name = "nickname", length = 50)
    var nickname: String? = null,
    
    @Column(name = "avatar_url", length = 255)
    var avatarUrl: String? = null,
    
    @Column(name = "real_name", length = 50)
    var realName: String? = null,
    
    @Column(name = "gender")
    var gender: Int = 0, // 0: 未知, 1: 男, 2: 女
    
    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,
    
    @Column(name = "age")
    var age: Int? = null,
    
    @Column(name = "height", precision = 5, scale = 2)
    var height: Double? = null, // cm
    
    @Column(name = "weight", precision = 5, scale = 2)
    var weight: Double? = null, // kg
    
    @Column(name = "body_type", length = 20)
    var bodyType: String? = null, // slim, normal, overweight
    
    @Column(name = "constitution", length = 50)
    var constitution: String? = null, // 体质类型
    
    @Column(name = "medical_history", columnDefinition = "TEXT")
    var medicalHistory: String? = null, // JSON格式
    
    @Column(name = "allergies", columnDefinition = "TEXT")
    var allergies: String? = null,
    
    @Column(name = "status")
    var status: Int = 1, // 0: 禁用, 1: 正常
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null
)
