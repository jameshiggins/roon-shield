package com.roonshield.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.roonshield.ui.RoonViewModel
import com.roonshield.ui.browse.LibraryBrowser
import com.roonshield.ui.browse.LibraryStyle

/** Home = today's daily sample of local-library albums, 4 per row, drillable. */
@Composable
fun HomeScreen(
    viewModel: RoonViewModel,
    modifier: Modifier = Modifier,
) {
    LibraryBrowser(
        viewModel = viewModel,
        sessionKey = "home",
        rootHierarchy = "albums",
        rootTitle = "Today's Albums",
        style = LibraryStyle.Grid,
        modifier = modifier,
        randomSample = true,
        sampleSize = 48,
        rootColumns = 4,
    )
}
