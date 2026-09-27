package ru.govsalary.data.repository

import kotlinx.coroutines.flow.Flow
import ru.govsalary.data.local.db.dao.FinancialGoalDao
import ru.govsalary.data.local.db.entity.FinancialGoalEntity
import ru.govsalary.data.local.db.entity.GoalStatus
import java.math.BigDecimal

class FinancialGoalRepository(private val dao: FinancialGoalDao) {

    suspend fun create(entity: FinancialGoalEntity): Long {
        require(entity.targetAmount.signum() > 0) { "Целевая сумма должна быть положительной" }
        return dao.insert(entity)
    }

    suspend fun update(entity: FinancialGoalEntity) = dao.update(entity)

    suspend fun delete(entity: FinancialGoalEntity) = dao.delete(entity)

    fun observeAll(): Flow<List<FinancialGoalEntity>> = dao.observeAll()

    fun observeActive(): Flow<List<FinancialGoalEntity>> = dao.observeByStatus(GoalStatus.ACTIVE)

    fun observeById(id: Long): Flow<FinancialGoalEntity?> = dao.observeById(id)

    suspend fun addContribution(id: Long, amount: BigDecimal, currentAmount: BigDecimal) {
        require(amount.signum() >= 0) { "Сумма взноса не может быть отрицательной" }
        dao.updateCurrentAmount(id, currentAmount.add(amount))
    }
}
