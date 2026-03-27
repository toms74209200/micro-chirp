package com.example.timeline

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MvRefreshLogRepository : JpaRepository<MvRefreshLog, String>
