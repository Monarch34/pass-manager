package com.passmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.passmanager.R
import com.passmanager.domain.validation.BankPasswordViolation

@Composable
fun BankPasswordRuleIndicator(
    password: String,
    violations: List<BankPasswordViolation>,
    showReusedRule: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pinPath = password.isNotEmpty() && password.all { it.isDigit() } && password.length <= 6

    val rules: List<Pair<BankPasswordViolation, Int>> = if (pinPath) {
        buildList {
            add(BankPasswordViolation.TooShort to R.string.bank_rule_pin_exact)
            if (showReusedRule) {
                add(BankPasswordViolation.ReusedPassword to R.string.bank_rule_not_reused)
            }
        }
    } else {
        buildList {
            add(BankPasswordViolation.TooShort to R.string.bank_rule_length)
            add(BankPasswordViolation.MissingUppercase to R.string.bank_rule_uppercase)
            add(BankPasswordViolation.MissingLowercase to R.string.bank_rule_lowercase)
            add(BankPasswordViolation.MissingDigit to R.string.bank_rule_digit)
            add(BankPasswordViolation.ConsecutiveSequence to R.string.bank_rule_no_consecutive)
            add(BankPasswordViolation.RepeatingCharacters to R.string.bank_rule_no_repeating)
            if (showReusedRule) {
                add(BankPasswordViolation.ReusedPassword to R.string.bank_rule_not_reused)
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rules.forEach { (violation, labelRes) ->
            val isFailing = when {
                pinPath && violation is BankPasswordViolation.TooShort ->
                    violations.any { it is BankPasswordViolation.TooShort } || password.length != 6
                pinPath && violation is BankPasswordViolation.ReusedPassword ->
                    violations.any { it is BankPasswordViolation.ReusedPassword }
                violation is BankPasswordViolation.TooShort ->
                    violations.any { it is BankPasswordViolation.TooShort || it is BankPasswordViolation.TooLong }
                else -> violations.any { it::class == violation::class }
            }
            val isPassing = !isFailing

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPassing) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isPassing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
