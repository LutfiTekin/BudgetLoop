package t.l.budget.loop.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@Composable
fun TransactionDatePicker(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    maxDate: LocalDate? = null,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    var selectedYear by remember(date) { mutableStateOf(date.year) }
    var selectedMonth by remember(date) { mutableStateOf(date.monthNumber) }
    var selectedDay by remember(date) { mutableStateOf(date.dayOfMonth) }

    val daysInMonth = remember(selectedYear, selectedMonth) {
        val start = LocalDate(selectedYear, selectedMonth, 1)
        val end = start.plus(1, DateTimeUnit.MONTH)
        start.daysUntil(end)
    }

    // Ensure day is valid for the new month/year
    LaunchedEffect(daysInMonth) {
        if (selectedDay > daysInMonth) {
            selectedDay = daysInMonth
            onDateChange(LocalDate(selectedYear, selectedMonth, selectedDay))
        }
    }
    
    // Enforce maxDate
    LaunchedEffect(selectedYear, selectedMonth, selectedDay, maxDate) {
        if (maxDate != null) {
            val currentSelection = LocalDate(selectedYear, selectedMonth, selectedDay)
            if (currentSelection > maxDate) {
                 // If selected date is future, snap strictly to maxDate.
                 // This handles all cases: future year, future month in current year, future day in current month.
                 selectedYear = maxDate.year
                 selectedMonth = maxDate.monthNumber
                 selectedDay = maxDate.dayOfMonth
                 onDateChange(maxDate)
            }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Day
        PhysicsWheelPicker(
            range = 1..daysInMonth,
            value = selectedDay,
            onValueChange = {
                val newDate = LocalDate(selectedYear, selectedMonth, it)
                if (maxDate == null || newDate <= maxDate) {
                    selectedDay = it
                    onDateChange(newDate)
                } else {
                     // If user tries to scroll past today, we could just ignore it or snap back.
                     // The LaunchedEffect above handles strict enforcement, but preventing the value change here feels snappier.
                     // However, PhysicsWheelPicker might animate. Let's just update local state if valid or 'snap' to maxDate if invalid but that might be fighting the picker.
                     // Simplest: Allow selection, let LaunchedEffect snap it back.
                     selectedDay = it
                     onDateChange(newDate)
                }
            },
            textStyle = textStyle,
            modifier = Modifier.width(48.dp),
        )

        Text(".", style = textStyle)

        // Month
        PhysicsWheelPicker(
            range = 1..12,
            value = selectedMonth,
            onValueChange = {
                val maxDay = LocalDate(selectedYear, it, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
                val newDay = selectedDay.coerceAtMost(maxDay)
                val newDate = LocalDate(selectedYear, it, newDay)
                
                selectedMonth = it
                if(newDay != selectedDay) selectedDay = newDay
                onDateChange(newDate)
            },
            textStyle = textStyle,
            modifier = Modifier.width(48.dp),
            label = { it.toString().padStart(2, '0') }
        )

        Text(".", style = textStyle)

        // Year
        val currentYear = remember { date.year }
        val minYear = currentYear - 5
        val maxYear = if (maxDate != null) maxDate.year else currentYear + 5
        
        PhysicsWheelPicker(
            range = minYear..maxYear,
            value = selectedYear,
            onValueChange = {
                val maxDay = LocalDate(it, selectedMonth, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
                val newDay = selectedDay.coerceAtMost(maxDay)
                val newDate = LocalDate(it, selectedMonth, newDay)
                
                selectedYear = it
                if(newDay != selectedDay) selectedDay = newDay
                onDateChange(newDate)
            },
            textStyle = textStyle,
            modifier = Modifier.width(90.dp), // Increased width for better visibility
        )
    }
}
