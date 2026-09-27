package ru.govsalary.data.repository

import kotlinx.coroutines.flow.Flow
import ru.govsalary.data.local.db.dao.SavingsPeriodDao
import ru.govsalary.data.local.db.dao.SavingsPlanDao
import ru.govsalary.data.local.db.entity.SavingsPeriodEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity

class SavingsPlanRepository(
    private val planDao: SavingsPlanDao,
    private val periodDao: SavingsPeriodDao,
) {

    suspend fun createPlan(entity: SavingsPlanEntity): Long = planDao.insert(entity)

    suspend fun updatePlan(entity: SavingsPlanEntity) = planDao.update(entity)

    suspend fun deletePlan(entity: SavingsPlanEntity) = planDao.delete(entity)

    fun observeActivePlans(): Flow<List<SavingsPlanEntity>> = planDao.observeActive()

    fun observeAllPlans(): Flow<List<SavingsPlanEntity>> = planDao.observeAll()

    fun observePlansForGoal(goalId: Long): Flow<List<SavingsPlanEntity>> = planDao.observeByGoal(goalId)

    fun observePeriods(planId: Long): Flow<List<SavingsPeriodEntity>> = periodDao.observeByPlan(planId)

    /**
     * Добавляет период с проверкой пересечения дат внутри одного плана — пример из мастер-промпта
     * Фазы 3, п.1 (четыре непересекающихся диапазона на год) не должен допускать конфликтов.
     */
    suspend fun addPeriod(entity: SavingsPeriodEntity) {
        val existing = periodDao.getByPlan(entity.planId)
        val overlap = existing.any { it.startDate <= entity.endDate && entity.startDate <= it.endDate }
        require(!overlap) {
            "Период ${entity.startDate}..${entity.endDate} пересекается с уже существующим периодом плана ${entity.planId}"
        }
        periodDao.insert(entity)
    }

    suspend fun getPeriods(planId: Long): List<SavingsPeriodEntity> = periodDao.getByPlan(planId)
}
