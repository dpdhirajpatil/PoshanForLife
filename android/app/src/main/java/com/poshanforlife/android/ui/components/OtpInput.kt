package com.poshanforlife.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val OTP_LENGTH = 6

/**
 * Six-box one-time-code entry, shared by the login/signup phone flow and the
 * "link phone number" flow in Settings.
 *
 * <p>Deliberately backed by a single hidden [BasicTextField] holding the whole
 * code rather than six independent fields. Six fields means six focus states to
 * keep in sync, and backspace-on-empty has to hop focus backwards manually —
 * which is exactly where per-box implementations tend to drop characters. Here
 * the boxes are pure rendering over one string, so paste and autofill of a full
 * code also work for free.
 *
 * @param onCompleted invoked once the sixth digit is entered, for auto-submit
 */
@Composable
fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    errorMessage: String? = null,
    onCompleted: (String) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(modifier = modifier) {
        Box6(
            value = value,
            isError = isError,
            enabled = enabled,
            focusRequester = focusRequester,
            onValueChange = { next ->
                onValueChange(next)
                if (next.length == OTP_LENGTH) onCompleted(next)
            },
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Box6(
    value: String,
    isError: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
) {
    // The selection is pinned to the end on every recomposition so the caret
    // can never sit mid-string, which would make typing overwrite a digit
    // instead of appending.
    val fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))

    BasicTextField(
        value = fieldValue,
        onValueChange = { input ->
            val digits = input.text.filter { it.isDigit() }.take(OTP_LENGTH)
            if (digits != value) onValueChange(digits)
        },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        // The real field is invisible; the boxes below are the visible UI.
        cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
        textStyle = TextStyle(color = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(OTP_LENGTH) { index ->
                    OtpBox(
                        digit = value.getOrNull(index)?.toString().orEmpty(),
                        // Highlight the box the next digit will land in.
                        isFocused = enabled && index == value.length.coerceAtMost(OTP_LENGTH - 1),
                        isError = isError,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
private fun OtpBox(digit: String, isFocused: Boolean, isError: Boolean, modifier: Modifier = Modifier) {
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    OutlinedCard(
        modifier = modifier.size(width = 0.dp, height = 56.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(borderColor)),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
