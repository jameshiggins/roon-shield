package com.roonshield.ui.home

import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.components.BrowsePane
import com.roonshield.ui.components.BrowseStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Home landing. Placeholder foundation version simply shows the library root; the Home agent
 * replaces this with shelves (Recently Played / Added / Discover) while keeping this signature.
 */
@Composable
fun HomeScreen(
    viewModel: RoonViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BrowsePane(
        vm = viewModel,
        sessionKey = "home",
        rootHierarchy = "browse",
        style = BrowseStyle.Grid,
        rootTitle = "Home",
        modifier = modifier,
    )
}
