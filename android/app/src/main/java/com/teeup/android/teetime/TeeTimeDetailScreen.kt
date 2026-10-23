package com.teeup.android.teetime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teeup.android.R
import com.teeup.android.data.Course
import com.teeup.android.data.GroupMember
import com.teeup.android.data.TeeTime
import com.teeup.android.data.formatHandicap
import com.teeup.android.data.formatPrice
import com.teeup.android.data.formatTeeTime
import com.teeup.android.data.paceLabel
import com.teeup.android.design.Avatar
import com.teeup.android.design.BackButton
import com.teeup.android.design.GroupedSection
import com.teeup.android.design.IconRowDividerInset
import com.teeup.android.design.Label
import com.teeup.android.design.LargeTitle
import com.teeup.android.design.LargeTitleScreen
import com.teeup.android.design.ListDivider
import com.teeup.android.design.ListRow
import com.teeup.android.design.Stat
import com.teeup.android.design.StatStrip
import com.teeup.android.design.StateMessage
import com.teeup.android.design.TeeUpButton
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpSheet
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.skeleton
import com.teeup.android.home.wantedSummary

/** Navigation out of the screen; the Activity owns Intents and transitions. */
class TeeTimeDetailActions(
    val back: () -> Unit,
    val previewCourse: (Course?) -> Unit,
    val editGroup: (TeeTime) -> Unit
)

internal sealed interface DetailSheet {
    data object Requests : DetailSheet
    data class Withdraw(val joinRequestId: String) : DetailSheet
    data object CancelGroup : DetailSheet
    data object DeleteGroup : DetailSheet
}

private val MEMBER_AVATAR = 36.dp

/**
 * Tee Time Detail in the iOS 26 idiom: the course as a large title with the tee time above it,
 * the key numbers at a glance, then inset grouped sections for what the host wants, who's
 * playing and the join requests. Request to Join floats at the bottom; confirmations and the
 * request list are sheets.
 */
@Composable
fun TeeTimeDetailScreen(viewModel: TeeTimeDetailViewModel, actions: TeeTimeDetailActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<DetailSheet?>(null) }
    val detail = state.detail
    val courseName = detail?.course?.name ?: stringResource(R.string.teetime_unknown_course)
    val title = if (detail != null) courseName else stringResource(R.string.detail_title)
    val joinButton = detail?.presentation?.joinButton ?: JoinButton.HIDDEN

    LargeTitleScreen(
        title = title,
        navigation = { BackButton(actions.back, stringResource(R.string.detail_back)) },
        bottomBar = if (joinButton == JoinButton.HIDDEN) null else {
            { JoinBar(joinButton, state.sendingRequest, viewModel::requestToJoin) }
        },
        header = {
            when {
                detail != null -> LargeTitle(courseName, eyebrow = formatTeeTime(detail.teeTime.dateTime).uppercase())
                state.error != null -> LargeTitle(title)
                else -> HeaderSkeleton()
            }
        },
        overlay = {
            sheet?.let { current ->
                TeeUpSheet(onDismissed = { sheet = null }) { dismiss ->
                    DetailSheetContent(current, state, viewModel, dismiss)
                }
            }
        }
    ) {
        when {
            detail != null -> detailContent(detail, actions, onSheet = { sheet = it })
            state.error != null -> item(key = "error") {
                val error = state.error!!
                StateMessage(
                    message = error.text?.takeIf { it.isNotBlank() } ?: stringResource(error.fallback),
                    buttonLabel = if (state.canRetry) stringResource(R.string.profile_retry) else null,
                    onButton = viewModel::load
                )
            }
            else -> item(key = "skeleton", contentType = "skeleton") { BodySkeleton() }
        }
    }
}

private fun LazyListScope.detailContent(
    detail: LoadedDetail,
    actions: TeeTimeDetailActions,
    onSheet: (DetailSheet) -> Unit
) {
    val teeTime = detail.teeTime
    val presentation = detail.presentation
    item(key = "stats", contentType = "stats") { StatsStrip(teeTime) }
    item(key = "about", contentType = "section") {
        GroupedSection {
            ListRow(
                title = stringResource(R.string.home_card_wanted_format, wantedSummary(teeTime)),
                icon = R.drawable.ic_glyph_target
            )
            ListDivider(IconRowDividerInset)
            ListRow(
                title = stringResource(R.string.detail_preview_course),
                icon = R.drawable.ic_glyph_map,
                onClick = { actions.previewCourse(detail.course) }
            )
        }
    }
    item(key = "host", contentType = "section") { HostSection(teeTime, presentation) }
    item(key = "players", contentType = "section") { PlayersSection(teeTime.members) }
    item(key = "requests", contentType = "section") {
        GroupedSection {
            ListRow(
                title = stringResource(R.string.detail_pending_requests),
                subtitle = if (presentation.pendingCount > 0) {
                    stringResource(R.string.teetime_pending_waiting_format, presentation.pendingCount)
                } else {
                    stringResource(R.string.teetime_pending_none)
                },
                icon = R.drawable.ic_glyph_person_badge,
                trailing = if (presentation.pendingCount > 0) {
                    { CountBadge(presentation.pendingCount) }
                } else null,
                onClick = { onSheet(DetailSheet.Requests) }
            )
        }
    }
    presentation.withdrawRequestId?.let { requestId ->
        item(key = "withdraw", contentType = "section") {
            GroupedSection {
                ListRow(
                    title = stringResource(R.string.teetime_withdraw_request),
                    titleColor = TeeUpTheme.colors.destructive,
                    showChevron = false,
                    onClick = { onSheet(DetailSheet.Withdraw(requestId)) }
                )
            }
        }
    }
    presentation.hostActions?.let { hostActions ->
        item(key = "manage", contentType = "section") {
            GroupedSection {
                if (hostActions.canEditOrCancel) {
                    ListRow(
                        title = stringResource(R.string.teetime_edit_group),
                        titleColor = TeeUpTheme.colors.accent,
                        showChevron = false,
                        onClick = { actions.editGroup(teeTime) }
                    )
                    ListDivider()
                    ListRow(
                        title = stringResource(R.string.teetime_cancel_group),
                        titleColor = TeeUpTheme.colors.destructive,
                        showChevron = false,
                        onClick = { onSheet(DetailSheet.CancelGroup) }
                    )
                    ListDivider()
                }
                ListRow(
                    title = stringResource(R.string.teetime_delete_group),
                    titleColor = TeeUpTheme.colors.destructive,
                    showChevron = false,
                    onClick = { onSheet(DetailSheet.DeleteGroup) }
                )
            }
        }
    }
}

/** Spots, holes and price side by side. */
@Composable
private fun StatsStrip(teeTime: TeeTime) {
    val full = teeTime.spotsRemaining <= 0
    StatStrip(
        listOf(
            Stat(stringResource(R.string.detail_stat_spots), if (full) stringResource(R.string.home_card_full) else teeTime.spotsRemaining.toString()),
            Stat(stringResource(R.string.home_filter_holes), (teeTime.holes ?: 18).toString()),
            Stat(stringResource(R.string.detail_stat_price), formatPrice(teeTime.price))
        ),
        Modifier.padding(top = 20.dp)
    )
}

@Composable
private fun HostSection(teeTime: TeeTime, presentation: TeeTimeDetailPresentation) {
    val host = teeTime.members.firstOrNull { it.isHost }
    GroupedSection(header = stringResource(R.string.detail_host)) {
        ListRow(
            title = when (presentation.hostKind) {
                HostKind.BOOKING_NO_HOST -> stringResource(R.string.teetime_host_booking_no_host)
                HostKind.OPEN_NO_HOST -> stringResource(R.string.teetime_host_open_no_host)
                HostKind.HOSTED -> stringResource(R.string.teetime_host_hosted)
            },
            // Host handicap/pace/home course can't be shown beyond the member row: there's no
            // public-profile-by-id endpoint, which raises the POPIA question flagged elsewhere.
            subtitle = stringResource(R.string.teetime_host_subtitle_format, presentation.acceptedCount, presentation.pendingCount),
            icon = if (host == null) R.drawable.ic_glyph_people else null,
            leading = host?.let { { Avatar(it.displayName, it.userId, size = MEMBER_AVATAR) } }
        )
    }
}

/** Every current member (host and accepted guests) with handicap and pace (EME-312). */
@Composable
private fun PlayersSection(members: List<GroupMember>) {
    val context = LocalContext.current
    GroupedSection(header = stringResource(R.string.detail_members)) {
        members.forEachIndexed { index, member ->
            if (index > 0) ListDivider(16.dp + MEMBER_AVATAR + 14.dp)
            ListRow(
                title = if (member.isHost) stringResource(R.string.group_host_member_format, member.displayName) else member.displayName,
                subtitle = "HCP ${formatHandicap(member.handicapIndex)}  ·  ${paceLabel(context, member.paceOfPlay)}",
                leading = { Avatar(member.displayName, member.userId, size = MEMBER_AVATAR) }
            )
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    val colors = TeeUpTheme.colors
    Box(
        Modifier.clip(CircleShape).background(colors.accent).padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Label(count.toString(), TeeUpTheme.type.footnote.copy(fontWeight = TeeUpTheme.type.headline.fontWeight), color = colors.onAccent, maxLines = 1)
    }
}

@Composable
private fun JoinBar(joinButton: JoinButton, sending: Boolean, onRequest: () -> Unit) {
    val label = when {
        sending -> stringResource(R.string.teetime_sending)
        joinButton == JoinButton.SENT -> stringResource(R.string.teetime_request_sent)
        joinButton == JoinButton.FULL -> stringResource(R.string.teetime_full)
        else -> stringResource(R.string.teetime_request_to_join)
    }
    TeeUpButton(
        label,
        onClick = onRequest,
        modifier = Modifier.fillMaxWidth(),
        enabled = joinButton == JoinButton.AVAILABLE && !sending,
        leadingIcon = if (joinButton == JoinButton.SENT) R.drawable.ic_glyph_check else null
    )
}

@Composable
private fun HeaderSkeleton() {
    Column(Modifier.padding(horizontal = TeeUpMetrics.gutter)) {
        Box(Modifier.size(130.dp, 13.dp).skeleton(RoundedCornerShape(6.dp)))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(250.dp, 32.dp).skeleton(RoundedCornerShape(8.dp)))
    }
}

@Composable
private fun BodySkeleton() {
    val shape = RoundedCornerShape(TeeUpMetrics.cardRadius)
    Column(
        Modifier.padding(horizontal = TeeUpMetrics.gutter).padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(78.dp).skeleton(shape))
        Box(Modifier.fillMaxWidth().height(106.dp).skeleton(shape))
        Box(Modifier.fillMaxWidth().height(64.dp).skeleton(shape))
    }
}
