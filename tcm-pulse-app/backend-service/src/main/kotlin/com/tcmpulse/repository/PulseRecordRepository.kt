package com.tcmpulse.repository

import com.tcmpulse.entity.PulseRecord
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface PulseRecordRepository : JpaRepository<PulseRecord, String> {
    
    fun findByUserIdOrderByRecordTimeDesc(userId: String): List<PulseRecord>
    
    fun findByUserId(userId: String, pageable: Pageable): Page<PulseRecord>
    
    @Query("SELECT pr FROM PulseRecord pr WHERE pr.userId = :userId AND pr.recordTime BETWEEN :startDate AND :endDate ORDER BY pr.recordTime DESC")
    fun findByUserIdAndDateRange(
        @Param("userId") userId: String,
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): List<PulseRecord>
    
    @Query("SELECT pr.mainPulse, COUNT(pr) FROM PulseRecord pr WHERE pr.userId = :userId GROUP BY pr.mainPulse")
    fun countPulseTypesByUserId(@Param("userId") userId: String): List<Array<Any>>
    
    @Query("SELECT AVG(pr.pulseRate) FROM PulseRecord pr WHERE pr.userId = :userId AND pr.recordTime >= :since")
    fun averagePulseRateSince(@Param("userId") userId: String, @Param("since") since: LocalDateTime): Double?
    
    fun countByUserId(userId: String): Long
}
