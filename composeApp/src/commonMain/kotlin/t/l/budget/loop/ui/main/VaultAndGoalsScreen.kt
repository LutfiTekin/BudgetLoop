package t.l.budget.loop.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import t.l.budget.loop.data.db.GoalKind
import t.l.budget.loop.data.db.SavingsEntryEntity
import t.l.budget.loop.data.db.SavingsGoalEntity
import t.l.budget.loop.data.db.SavingsSource
import t.l.budget.loop.money.Money

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VaultAndGoalsScreen(
    defaultCurrency: String,
    totalSaved: Money,
    goals: List<SavingsGoalEntity>,
    entries: List<SavingsEntryEntity>,
    onAddGoal: (name: String, targetMinor: Long, initialMinor: Long, kind: GoalKind, targetDate: LocalDate?) -> Unit,
    onUpdateGoal: (SavingsGoalEntity) -> Unit,
    onMoveGoal: (goalId: Long, delta: Int) -> Unit,
    onDeleteGoal: (SavingsGoalEntity) -> Unit,
    onAddManualEntry: (amountMinor: Long, note: String?, goalId: Long?) -> Unit,
    onDeleteEntry: (SavingsEntryEntity) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedGoalId by remember(goals) { mutableStateOf<Long?>(null) }
    var showCreateGoalDialog by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<SavingsGoalEntity?>(null) }

    val activeGoal = goals.firstOrNull { !it.archived && it.allocatedMinor < it.targetMinor }
        ?: goals.firstOrNull { !it.archived }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Loop Vault & Goals", style = MaterialTheme.typography.titleLarge)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Total Infused & Saved", style = MaterialTheme.typography.labelMedium)
                Text(totalSaved.format(), style = MaterialTheme.typography.displaySmall)
                if (activeGoal != null) {
                    Text(
                        text = "Active Infuse Target: ${activeGoal.name}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Priority Waterfall", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { showCreateGoalDialog = true }) {
                Text("+ Add Goal")
            }
        }

        if (goals.isEmpty()) {
            Text(
                text = "No goals configured yet. Add a debt payoff or savings target and infused daily carry-over will feed them in priority order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            goals.forEachIndexed { idx, goal ->
                GoalWaterfallCard(
                    goal = goal,
                    currency = defaultCurrency,
                    isActiveTarget = activeGoal?.id == goal.id,
                    canMoveUp = idx > 0,
                    canMoveDown = idx < goals.lastIndex,
                    onMoveUp = { onMoveGoal(goal.id, -1) },
                    onMoveDown = { onMoveGoal(goal.id, 1) },
                    onEdit = { editingGoal = goal },
                    onDelete = { onDeleteGoal(goal) },
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Add Manual Vault Entry", style = MaterialTheme.typography.titleMedium)
        TextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount ($defaultCurrency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (goals.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = selectedGoalId == null,
                    onClick = { selectedGoalId = null },
                    label = { Text("Auto (${activeGoal?.name ?: "Vault"})") },
                )
                goals.forEach { goal ->
                    FilterChip(
                        selected = selectedGoalId == goal.id,
                        onClick = { selectedGoalId = goal.id },
                        label = { Text(goal.name) },
                    )
                }
            }
        }

        val parsedManualMinor = parseGoalMinor(amountText, defaultCurrency)
        Button(
            onClick = {
                val minor = parsedManualMinor ?: return@Button
                onAddManualEntry(minor, noteText.trim().ifBlank { null }, selectedGoalId)
                amountText = ""
                noteText = ""
            },
            enabled = parsedManualMinor != null && parsedManualMinor != 0L,
        ) {
            Text("Save to Vault")
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Vault History", style = MaterialTheme.typography.titleMedium)
        if (entries.isEmpty()) {
            Text(
                text = "No vault entries yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { entry ->
                VaultEntryRow(
                    entry = entry,
                    currency = defaultCurrency,
                    onDelete = { onDeleteEntry(entry) },
                )
            }
        }
    }

    if (showCreateGoalDialog) {
        GoalEditorDialog(
            title = "New Loop Target",
            currency = defaultCurrency,
            initialGoal = null,
            onConfirm = { name, targetMinor, allocatedMinor, kind, targetDate ->
                onAddGoal(name, targetMinor, allocatedMinor, kind, targetDate)
                showCreateGoalDialog = false
            },
            onDismiss = { showCreateGoalDialog = false },
        )
    }

    val targetToEdit = editingGoal
    if (targetToEdit != null) {
        GoalEditorDialog(
            title = "Edit Target",
            currency = defaultCurrency,
            initialGoal = targetToEdit,
            onConfirm = { name, targetMinor, allocatedMinor, kind, targetDate ->
                onUpdateGoal(
                    targetToEdit.copy(
                        name = name,
                        targetMinor = targetMinor,
                        allocatedMinor = allocatedMinor,
                        kind = kind,
                        targetDate = targetDate,
                    ),
                )
                editingGoal = null
            },
            onDismiss = { editingGoal = null },
        )
    }
}

@Composable
private fun GoalWaterfallCard(
    goal: SavingsGoalEntity,
    currency: String,
    isActiveTarget: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val progressFraction = if (goal.targetMinor <= 0L) {
        0f
    } else {
        (goal.allocatedMinor.toFloat() / goal.targetMinor.toFloat()).coerceIn(0f, 1f)
    }
    val remainingMinor = (goal.targetMinor - goal.allocatedMinor).coerceAtLeast(0L)
    val allocatedMoney = Money.of(goal.allocatedMinor, currency).format()
    val targetMoney = Money.of(goal.targetMinor, currency).format()
    val remainingMoney = Money.of(remainingMinor, currency).format()
    val kindLabel = if (goal.kind == GoalKind.DEBT_PAYDOWN) "Debt Paydown" else "Savings Goal"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(goal.name, style = MaterialTheme.typography.titleSmall)
                    val meta = buildString {
                        append(kindLabel)
                        if (goal.targetDate != null) append(" · Due ${goal.targetDate}")
                        if (isActiveTarget) append(" · ACTIVE INFUSE")
                    }
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActiveTarget) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Goal")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Goal")
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val verb = if (goal.kind == GoalKind.DEBT_PAYDOWN) "Cleared" else "Saved"
                Text(
                    text = "$verb $allocatedMoney / $targetMoney",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = if (remainingMinor == 0L) "COMPLETE" else "$remainingMoney left",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (remainingMinor == 0L) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun VaultEntryRow(
    entry: SavingsEntryEntity,
    currency: String,
    onDelete: () -> Unit,
) {
    val sourceLabel = when (entry.source) {
        SavingsSource.CARRY_OVER -> "Infused Residuum"
        SavingsSource.MONTH_END -> "Month-End"
        SavingsSource.MANUAL -> "Manual"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val title = buildString {
                append(sourceLabel)
                if (!entry.note.isNullOrBlank()) append(" · ${entry.note}")
            }
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(
                text = entry.date.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Money.of(entry.amountMinor, currency).format(),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete Vault Entry")
        }
    }
}

@Composable
private fun GoalEditorDialog(
    title: String,
    currency: String,
    initialGoal: SavingsGoalEntity?,
    onConfirm: (name: String, targetMinor: Long, allocatedMinor: Long, kind: GoalKind, targetDate: LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialGoal?.name.orEmpty()) }
    var targetText by remember {
        mutableStateOf(
            initialGoal?.let { Money.of(it.targetMinor, currency).format(withCurrency = false) }.orEmpty(),
        )
    }
    var progressText by remember {
        mutableStateOf(
            initialGoal?.let { Money.of(it.allocatedMinor, currency).format(withCurrency = false) } ?: "0",
        )
    }
    var kind by remember { mutableStateOf(initialGoal?.kind ?: GoalKind.SAVINGS) }
    var dateText by remember { mutableStateOf(initialGoal?.targetDate?.toString().orEmpty()) }

    val parsedTarget = parseGoalMinor(targetText, currency)
    val parsedProgress = parseGoalMinor(progressText, currency)
    val parsedDate = if (dateText.isBlank()) {
        null
    } else {
        runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
    }
    val dateValid = dateText.isBlank() || parsedDate != null
    val canSave = name.isNotBlank() &&
        parsedTarget != null &&
        parsedTarget > 0L &&
        parsedProgress != null &&
        parsedProgress >= 0L &&
        dateValid

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val target = parsedTarget ?: return@Button
                    val progress = parsedProgress ?: return@Button
                    onConfirm(name.trim(), target, progress, kind, parsedDate)
                },
                enabled = canSave,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Target name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == GoalKind.SAVINGS,
                        onClick = { kind = GoalKind.SAVINGS },
                        label = { Text("Savings Goal") },
                    )
                    FilterChip(
                        selected = kind == GoalKind.DEBT_PAYDOWN,
                        onClick = { kind = GoalKind.DEBT_PAYDOWN },
                        label = { Text("Debt Paydown") },
                    )
                }
                TextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Target amount ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = progressText,
                    onValueChange = { progressText = it },
                    label = {
                        Text(
                            if (kind == GoalKind.DEBT_PAYDOWN) "Already paid off ($currency)" else "Current saved ($currency)",
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Target date YYYY-MM-DD (optional)") },
                    isError = !dateValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

private fun parseGoalMinor(amount: String, currency: String): Long? {
    val cleaned = amount.trim().replace(",", ".")
    val value = cleaned.toDoubleOrNull() ?: return null
    return Money.fromDecimal(value, currency).minor
}
