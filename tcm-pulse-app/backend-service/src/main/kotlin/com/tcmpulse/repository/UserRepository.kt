package com.tcmpulse.repository

import com.tcmpulse.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, String> {
    fun findByPhone(phone: String): User?
    fun findByEmail(email: String): User?
    fun existsByPhone(phone: String): Boolean
    fun existsByEmail(email: String): Boolean
}
