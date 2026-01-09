package com.spendbubble.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

@Singleton
class ExpenseRepository @Inject constructor(private val expenseDao: ExpenseDao) {

    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    suspend fun addExpense(amount: Double, note: String, category: String) {
        val expense = Expense(
            amount = amount,
            note = note,
            category = category,
            timestamp = System.currentTimeMillis()
        )
        expenseDao.insertExpense(expense)
    }

    fun getTodayTotal(): Flow<Double?> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endOfDay = calendar.timeInMillis

        return expenseDao.getTotalForToday(startOfDay, endOfDay)
    }
}
