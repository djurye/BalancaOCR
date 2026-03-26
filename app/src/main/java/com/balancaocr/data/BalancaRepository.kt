package com.balancaocr.data

import android.content.Context

class BalancaRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val measureDao = db.measurementDao()
    private val sessionDao = db.sessionDao()

    // Sessions
    suspend fun getAllSessions() = sessionDao.getAll()

    suspend fun createSession(name: String): Long = sessionDao.insert(Session(name = name))

    suspend fun deleteSession(session: Session) {
        measureDao.deleteBySession(session.id)
        sessionDao.delete(session)
    }

    suspend fun updateSessionCount(sessionId: Long, count: Int) {
        val sessions = sessionDao.getAll()
        sessions.firstOrNull { it.id == sessionId }?.let {
            sessionDao.update(it.copy(count = count))
        }
    }

    // Measurements
    suspend fun getMeasurements(sessionId: Long) = measureDao.getBySession(sessionId)

    suspend fun addMeasurement(sessionId: Long, value: Double, unit: String, index: Int): Long {
        val id = measureDao.insert(
            Measurement(sessionId = sessionId, value = value, unit = unit, index = index)
        )
        updateSessionCount(sessionId, index + 1)
        return id
    }

    suspend fun deleteMeasurement(m: Measurement) = measureDao.delete(m)
}
