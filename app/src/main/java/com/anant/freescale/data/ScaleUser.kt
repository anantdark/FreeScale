package com.anant.freescale.data

import java.util.Calendar
import java.util.Date

data class ScaleUser(
    var id: Int = 1,
    var userName: String = "User",
    var birthday: Date = defaultBirthday(30),
    var bodyHeight: Float = 170f,
    var gender: GenderType = GenderType.MALE,
) {
    val age: Int
        get() {
            // Compare month/day. not DAY_OF_YEAR (leap years shift Aug 15: 227 vs 228).
            val today = Calendar.getInstance()
            val born = Calendar.getInstance().apply { time = birthday }
            var years = today.get(Calendar.YEAR) - born.get(Calendar.YEAR)
            val beforeBirthday =
                today.get(Calendar.MONTH) < born.get(Calendar.MONTH) ||
                    (today.get(Calendar.MONTH) == born.get(Calendar.MONTH) &&
                        today.get(Calendar.DAY_OF_MONTH) < born.get(Calendar.DAY_OF_MONTH))
            if (beforeBirthday) years -= 1
            return years.coerceAtLeast(1)
        }

    companion object {
        fun defaultBirthday(ageYears: Int): Date {
            val cal = Calendar.getInstance()
            cal.add(Calendar.YEAR, -ageYears)
            return cal.time
        }
    }
}
