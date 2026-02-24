package com.tcmpulse.repository

import com.tcmpulse.entity.Prescription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PrescriptionRepository : JpaRepository<Prescription, Long> {
    fun findByName(name: String): Prescription?
    fun findByNameIn(names: List<String>): List<Prescription>
    fun findByCategory(category: String): List<Prescription>
    fun findByNameContaining(keyword: String): List<Prescription>
}
