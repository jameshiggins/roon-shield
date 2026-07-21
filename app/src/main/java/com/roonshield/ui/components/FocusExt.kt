package com.roonshield.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/** Convenience: report just the boolean focus state of this node. */
fun Modifier.onFocusState(onChange: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { onChange(it.isFocused) }
