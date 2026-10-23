package com.teeup.android.teetime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.teeup.android.R
import com.teeup.android.data.JoinRequest
import com.teeup.android.data.JoinRequestStatus
import com.teeup.android.design.Avatar
import com.teeup.android.design.ButtonKind
import com.teeup.android.design.ConfirmSheetContent
import com.teeup.android.design.Label
import com.teeup.android.design.ListDivider
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpTheme

@Composable
internal fun ColumnScope.DetailSheetContent(
    sheet: DetailSheet,
    state: TeeTimeDetailUiState,
    viewModel: TeeTimeDetailViewModel,
    dismiss: (then: () -> Unit) -> Unit
) {
    when (sheet) {
        DetailSheet.Requests -> RequestsSheet(state.detail?.requests.orEmpty(), viewModel::respond, dismiss)
        is DetailSheet.Withdraw -> ConfirmSheetContent(
            title = stringResource(R.string.teetime_withdraw_confirm_title),
            message = stringResource(R.string.teetime_withdraw_confirm_message),
            confirmLabel = stringResource(R.string.teetime_withdraw_request),
            cancelLabel = stringResource(R.string.dialog_cancel),
            destructive = true,
            onConfirm = { viewModel.withdraw(sheet.joinRequestId) },
            dismiss = dismiss
        )
        DetailSheet.CancelGroup -> ConfirmSheetContent(
            title = stringResource(R.string.teetime_cancel_confirm_title),
            message = stringResource(R.string.teetime_cancel_confirm_message),
            confirmLabel = stringResource(R.string.teetime_cancel_group),
            cancelLabel = stringResource(R.string.dialog_cancel),
            destructive = true,
            onConfirm = viewModel::cancelGroup,
            dismiss = dismiss
        )
        DetailSheet.DeleteGroup -> ConfirmSheetContent(
            title = stringResource(R.string.teetime_delete_confirm_title),
            message = stringResource(R.string.teetime_delete_confirm_message),
            confirmLabel = stringResource(R.string.teetime_delete_group),
            cancelLabel = stringResource(R.string.dialog_cancel),
            destructive = true,
            onConfirm = viewModel::deleteGroup,
            dismiss = dismiss
        )
    }
}

/** Every join request with its status; pending ones can be accepted or declined in place, and
 *  the list updates from the refreshed state without closing. */
@Composable
private fun ColumnScope.RequestsSheet(
    requests: List<JoinRequest>,
    onRespond: (joinRequestId: String, status: Int) -> Unit,
    dismiss: (then: () -> Unit) -> Unit
) {
    val colors = TeeUpTheme.colors
    val type = TeeUpTheme.type
    val context = LocalContext.current
    Label(stringResource(R.string.teetime_join_requests_title), type.title3, modifier = Modifier.padding(bottom = 10.dp).semantics { heading() })
    if (requests.isEmpty()) {
        Label(
            stringResource(R.string.teetime_no_join_requests),
            type.body,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(vertical = 18.dp)
        )
    } else {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            itemsIndexed(requests, key = { _, request -> request.id }) { index, request ->
                if (index > 0) ListDivider(0.dp)
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(request.guestDisplayName, request.guestUserId)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Label(request.guestDisplayName, type.headline, maxLines = 1)
                            Label(
                                JoinRequestStatus.label(context, request.status),
                                type.footnote,
                                color = if (request.status == JoinRequestStatus.ACCEPTED) colors.accent else colors.secondaryLabel,
                                maxLines = 1
                            )
                        }
                    }
                    if (request.status == JoinRequestStatus.PENDING) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TeeUpButton(
                                stringResource(R.string.teetime_decline),
                                onClick = { onRespond(request.id, JoinRequestStatus.DECLINED) },
                                modifier = Modifier.weight(1f),
                                kind = ButtonKind.Secondary,
                                compact = true
                            )
                            TeeUpButton(
                                stringResource(R.string.teetime_accept),
                                onClick = { onRespond(request.id, JoinRequestStatus.ACCEPTED) },
                                modifier = Modifier.weight(1f),
                                compact = true
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    TeeUpButton(
        stringResource(R.string.teetime_close),
        onClick = { dismiss {} },
        modifier = Modifier.fillMaxWidth(),
        kind = ButtonKind.Secondary
    )
}
