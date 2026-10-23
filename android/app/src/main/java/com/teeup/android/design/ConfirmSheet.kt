package com.teeup.android.design

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The body of a confirmation [TeeUpSheet], iOS 26's replacement for an alert: the question, what
 * it means, then the action and Cancel as full-width buttons. [destructive] paints the action red.
 * The action runs at once and the sheet slides away behind it.
 */
@Composable
fun ColumnScope.ConfirmSheetContent(
    title: String,
    message: String?,
    confirmLabel: String,
    cancelLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    dismiss: (then: () -> Unit) -> Unit
) {
    val type = TeeUpTheme.type
    Label(
        title,
        type.title3.copy(textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).semantics { heading() }
    )
    if (message != null) {
        Spacer(Modifier.height(8.dp))
        Label(
            message,
            type.subheadline.copy(textAlign = TextAlign.Center),
            color = TeeUpTheme.colors.secondaryLabel,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
    Spacer(Modifier.height(22.dp))
    TeeUpButton(
        confirmLabel,
        onClick = { onConfirm(); dismiss {} },
        modifier = Modifier.fillMaxWidth(),
        kind = if (destructive) ButtonKind.Destructive else ButtonKind.Primary
    )
    Spacer(Modifier.height(10.dp))
    TeeUpButton(cancelLabel, onClick = { dismiss {} }, modifier = Modifier.fillMaxWidth(), kind = ButtonKind.Secondary)
}
