package com.adiraimaji.amcallassistant.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * The inline controls the editors share. Nothing here opens a dialog or holds
 * a value: each screen keeps its own state and its own save logic. The fields
 * are built on the decoration box so they sit in a 48dp touch target, and the
 * capsules wrap rather than squeezing a label away on a narrow phone.
 */

/** One of a few choices, in place. The check marks what is selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Capsules(
    labels: List<String>,
    selected: String,
    enabled: Boolean = true,
    disabled: Set<String> = emptySet(),
    onSelect: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (label in labels) {
            FilterChip(
                selected = label == selected,
                onClick = { haptics.tap(); onSelect(label) },
                enabled = enabled && label !in disabled,
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = if (label == selected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * An input in the editor itself, never a dialog, and as short as a field can
 * honestly be. The stock OutlinedTextField reserves 56dp; this one drives
 * Material's decoration box directly, so one line sits in the 48dp touch
 * target instead. The label floats in the border, and [supporting] is only
 * drawn when there is something to say - an error, a count - so a column of
 * fields stays short enough to read without hunting.
 */
@Composable
internal fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 5,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().then(
            if (onFocusChanged != null) Modifier.onFocusChanged { onFocusChanged(it.isFocused) } else Modifier,
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interaction,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                isError = isError,
                label = { Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
                placeholder = placeholder?.let {
                    { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                },
                colors = colors,
                // 12dp above and below a 24dp line is 48dp: the shortest a
                // field may be and still be a full touch target.
                contentPadding = OutlinedTextFieldDefaults.contentPadding(
                    top = 12.dp,
                    start = 12.dp,
                    bottom = 12.dp,
                    end = 12.dp,
                ),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interaction,
                        colors = colors,
                    )
                },
            )
        },
    )
    if (supporting != null) {
        Text(
            supporting,
            style = MaterialTheme.typography.bodySmall,
            color = if (supportingError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp).heightIn(min = 16.dp),
        )
    }
}

@Composable
internal fun InlineError(message: String?) {
    if (message != null) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A single choice as a full row, selected by tapping anywhere on it. Call it
 *  inside `row { }`, as the other settings-list rows are collected. */
@Composable
internal fun RadioRow(
    title: String,
    summary: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = title,
        summary = summary,
        modifier = Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            enabled = enabled,
        ) { onClick() },
        trailing = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        enabled = enabled,
    )
}
