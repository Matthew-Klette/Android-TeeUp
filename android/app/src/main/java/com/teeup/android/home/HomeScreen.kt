package com.teeup.android.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teeup.android.R
import com.teeup.android.data.Course
import com.teeup.android.design.FilterChip
import com.teeup.android.design.GlassIconButton
import com.teeup.android.design.GlassTabBar
import com.teeup.android.design.Label
import com.teeup.android.design.LocalHazeState
import com.teeup.android.design.SearchField
import com.teeup.android.design.StateMessage
import com.teeup.android.design.TeeUpMetrics
import com.teeup.android.design.TeeUpSheet
import com.teeup.android.design.TeeUpTheme
import com.teeup.android.design.bottomEdgeGlass
import com.teeup.android.design.scrollEdgeGlass
import com.teeup.android.design.skeleton
import com.teeup.android.nav.BottomNavTab
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

internal enum class HomeSheet { DATE, SPOTS, COURSE, HOLES, SKILL }

/** Where the navigation actions go; the Activity owns Intents and transitions. */
class HomeActions(
    val openGroup: (teeTimeId: String) -> Unit,
    val previewCourse: (Course?) -> Unit,
    val openNotifications: () -> Unit,
    val createGroup: () -> Unit,
    val openTab: (BottomNavTab) -> Unit
)

/**
 * Home, in the iOS 26 idiom: a large title that hands over to an inline title in a glass bar as
 * the list scrolls beneath it, floating glass toolbar buttons, a search field and filter chips
 * (each opening a sheet), the open groups, and the floating glass tab bar.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, actions: HomeActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = TeeUpTheme.colors
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val collapseDistance = with(LocalDensity.current) { 52.dp.toPx() }
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    var sheet by remember { mutableStateOf<HomeSheet?>(null) }
    val focusManager = LocalFocusManager.current
    // Groups already shown once; only new ones get the entrance animation.
    val seen = remember { mutableSetOf<String>() }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            // The wash and the list are both blur sources, so the glass bar frosts over either.
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusTop + 340.dp)
                        .background(Brush.verticalGradient(listOf(colors.accentWash, colors.background.copy(alpha = 0f))))
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = statusTop + 56.dp, bottom = navBottom + TeeUpMetrics.tabBarHeight + 32.dp)
                ) {
                    // The large title is gone by half-way, before the inline title starts to appear.
                    item(key = "header") { LargeHeader(Modifier.graphicsLayer { alpha = (1f - collapse * 2f).coerceIn(0f, 1f) }) }
                    item(key = "search") {
                        SearchField(
                            value = state.filters.search,
                            onValueChange = { text -> viewModel.updateFilters { it.copy(search = text) } },
                            placeholder = stringResource(R.string.home_search_hint),
                            clearDescription = stringResource(R.string.home_clear_search),
                            modifier = Modifier.padding(horizontal = TeeUpMetrics.gutter).padding(top = 16.dp)
                        )
                    }
                    item(key = "chips") {
                        FilterRow(state, onSheet = { focusManager.clearFocus(); sheet = it }, viewModel = viewModel)
                    }
                    homeContent(state, seen, viewModel, actions)
                }
            }

            // Passed as a lambda and only read while drawing: scrolling never recomposes Home.
            TopChrome({ collapse }, statusTop, actions)

            // Frosts the content sliding under the tab bar so the two never fight.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navBottom + TeeUpMetrics.tabBarHeight + 64.dp)
                    .bottomEdgeGlass()
            )

            GlassTabBar(
                selected = BottomNavTab.HOME,
                onSelect = actions.openTab,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 10.dp)
            )

            sheet?.let { current ->
                TeeUpSheet(onDismissed = { sheet = null }) { dismiss ->
                    HomeSheetContent(current, state, viewModel, dismiss)
                }
            }
        }
    }
}

@Composable
private fun LargeHeader(modifier: Modifier = Modifier) {
    val type = TeeUpTheme.type
    Column(modifier.padding(horizontal = TeeUpMetrics.gutter)) {
        Label(stringResource(R.string.home_area), type.eyebrow, color = TeeUpTheme.colors.accent, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Label(stringResource(R.string.home_large_title), type.largeTitle, maxLines = 1, modifier = Modifier.semantics { heading() })
    }
}

/** Glass toolbar buttons that always float, plus the inline title and scroll-edge blur that
 *  fade in once the large title has scrolled away. */
@Composable
private fun TopChrome(collapse: () -> Float, statusTop: androidx.compose.ui.unit.Dp, actions: HomeActions) {
    // The blur only exists once there's something under the bar; at rest it costs nothing.
    val showEdge by remember { derivedStateOf { collapse() > 0f } }
    Box(Modifier.fillMaxWidth()) {
        if (showEdge) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + 56.dp)
                    .graphicsLayer { alpha = collapse() }
                    .scrollEdgeGlass()
            )
        }
        Box(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Label(
                stringResource(R.string.home_large_title),
                TeeUpTheme.type.headline,
                maxLines = 1,
                modifier = Modifier.graphicsLayer {
                    val progress = ((collapse() - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    alpha = progress
                    // Rises into place as it appears, hinting where the large title went.
                    translationY = (1f - progress) * 6.dp.toPx()
                }
            )
        }
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = TeeUpMetrics.gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            GlassIconButton(R.drawable.ic_glyph_bell, stringResource(R.string.home_notifications), actions.openNotifications)
            Spacer(Modifier.width(10.dp))
            GlassIconButton(R.drawable.ic_glyph_plus, stringResource(R.string.home_create_group), actions.createGroup)
        }
    }
}

@Composable
private fun FilterRow(state: HomeUiState, onSheet: (HomeSheet) -> Unit, viewModel: HomeViewModel) {
    val filters = state.filters
    val rowState = rememberLazyListState()
    val hasRefinements = filters.activeRefinements > 0
    // "Clear filters" slides in at the front; bring it into view rather than leave it off-screen.
    LaunchedEffect(hasRefinements) { if (hasRefinements) rowState.animateScrollToItem(0) }
    LazyRow(
        state = rowState,
        modifier = Modifier.padding(top = 8.dp),
        contentPadding = PaddingValues(horizontal = TeeUpMetrics.gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasRefinements) {
            item(key = "clear") {
                FilterChip(
                    stringResource(R.string.home_clear_filters),
                    selected = false,
                    onClick = viewModel::clearRefinements,
                    showChevron = false,
                    leadingIcon = R.drawable.ic_glyph_xmark,
                    modifier = Modifier.animateItem()
                )
            }
        }
        item(key = "mine") {
            FilterChip(
                stringResource(R.string.home_chip_mine),
                selected = filters.mineOnly,
                onClick = { viewModel.updateFilters { it.copy(mineOnly = !it.mineOnly) } },
                showChevron = false,
                leadingIcon = R.drawable.ic_glyph_people,
                modifier = Modifier.animateItem()
            )
        }
        item(key = "date") {
            val label = when (filters.date) {
                DateFilter.ALL -> stringResource(R.string.home_chip_any_day)
                DateFilter.TODAY -> stringResource(R.string.home_chip_today)
                DateFilter.TOMORROW -> stringResource(R.string.home_chip_tomorrow)
            }
            FilterChip(label, filters.date != DateFilter.ALL, { onSheet(HomeSheet.DATE) }, Modifier.animateItem())
        }
        item(key = "spots") {
            val label = if (filters.minOpenSpots == 0) stringResource(R.string.home_chip_spots)
            else stringResource(R.string.home_chip_spots_min_format, filters.minOpenSpots)
            FilterChip(label, filters.minOpenSpots > 0, { onSheet(HomeSheet.SPOTS) }, Modifier.animateItem())
        }
        item(key = "course") {
            val label = filters.courseId?.let { state.courses[it]?.name } ?: stringResource(R.string.home_filter_course)
            FilterChip(label, filters.courseId != null, { onSheet(HomeSheet.COURSE) }, Modifier.animateItem())
        }
        item(key = "holes") {
            val label = filters.holes?.let { stringResource(R.string.home_chip_holes_format, it) } ?: stringResource(R.string.home_filter_holes)
            FilterChip(label, filters.holes != null, { onSheet(HomeSheet.HOLES) }, Modifier.animateItem())
        }
        item(key = "skill") {
            FilterChip(stringResource(R.string.home_chip_skill), filters.hasSkillFilter, { onSheet(HomeSheet.SKILL) }, Modifier.animateItem())
        }
    }
}

/** The list body: skeletons while loading, a message when there's nothing to show, else cards. */
private fun androidx.compose.foundation.lazy.LazyListScope.homeContent(
    state: HomeUiState,
    seen: MutableSet<String>,
    viewModel: HomeViewModel,
    actions: HomeActions
) {
    val visible = state.visible
    when {
        state.isLoading && state.teeTimes.isEmpty() -> {
            item(key = "section") { SectionLabel(null) }
            items(2, key = { "skeleton$it" }, contentType = { "skeleton" }) { SkeletonCard() }
        }
        state.loadError != null -> item(key = "message") {
            StateMessage(
                message = state.loadError.ifBlank { stringResource(R.string.home_load_failed) },
                buttonLabel = stringResource(R.string.profile_retry),
                onButton = viewModel::load
            )
        }
        state.teeTimes.isEmpty() -> item(key = "message") {
            StateMessage(
                message = stringResource(R.string.home_empty_no_teetimes),
                buttonLabel = stringResource(R.string.home_create_group),
                onButton = actions.createGroup
            )
        }
        visible.isEmpty() -> item(key = "message") {
            StateMessage(
                message = stringResource(R.string.home_empty_no_matches),
                buttonLabel = stringResource(R.string.home_clear_filters),
                onButton = { viewModel.updateFilters { HomeFilters(mineOnly = it.mineOnly) } }
            )
        }
        else -> {
            item(key = "section") { SectionLabel(visible.size) }
            itemsIndexed(visible, key = { _, teeTime -> teeTime.id }, contentType = { _, _ -> "card" }) { index, teeTime ->
                val animateIn = seen.add(teeTime.id)
                TeeTimeCard(
                    teeTime = teeTime,
                    course = state.courses[teeTime.courseId],
                    index = index,
                    animateIn = animateIn,
                    onOpen = { actions.openGroup(teeTime.id) },
                    onPreview = { actions.previewCourse(state.courses[teeTime.courseId]) },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = TeeUpMetrics.gutter)
                        .padding(bottom = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(count: Int?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TeeUpMetrics.gutter + 4.dp)
            .padding(top = 26.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Label(stringResource(R.string.home_results_heading), TeeUpTheme.type.eyebrow, color = TeeUpTheme.colors.secondaryLabel, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Label(count.toString(), TeeUpTheme.type.eyebrow, color = TeeUpTheme.colors.tertiaryLabel, maxLines = 1)
        }
    }
}

@Composable
private fun SkeletonCard() {
    val shape = RoundedCornerShape(TeeUpMetrics.cardRadius)
    Column(
        Modifier
            .padding(horizontal = TeeUpMetrics.gutter)
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .background(TeeUpTheme.colors.surface, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp, 58.dp).skeleton(RoundedCornerShape(15.dp)))
            Spacer(Modifier.width(14.dp))
            Column {
                Box(Modifier.size(170.dp, 16.dp).skeleton(RoundedCornerShape(6.dp)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.size(110.dp, 12.dp).skeleton(RoundedCornerShape(6.dp)))
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(34.dp).skeleton(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(40.dp).skeleton(CircleShape))
    }
}
