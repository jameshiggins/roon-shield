package com.roonshield.ui

import com.roonshield.roon.RoonRepository
import com.roonshield.roon.model.BrowseItem
import com.roonshield.roon.model.BrowseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Drives one Roon Browse hierarchy (e.g. "browse" for the library, "search" for search) as a
 * navigable stack: [home], [into] an item, [back] a level, [submitInput] for text prompts.
 * Each browse() moves the cursor; we then load() the resulting page into [items].
 */
class BrowseController(
    private val repo: RoonRepository,
    private val scope: CoroutineScope,
    private val rootHierarchy: String,
) {
    val items = MutableStateFlow<List<BrowseItem>>(emptyList())
    val title = MutableStateFlow("")
    val level = MutableStateFlow(0)
    val loading = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val inputPrompt = MutableStateFlow<String?>(null)

    val canGoBack: Boolean get() = level.value > 0

    fun home() = scope.launch {
        loading.value = true
        apply(repo.browse(hierarchy = rootHierarchy, popAll = true))
    }

    fun into(item: BrowseItem) = scope.launch {
        val key = item.itemKey ?: return@launch
        loading.value = true
        apply(repo.browse(hierarchy = rootHierarchy, itemKey = key))
    }

    fun back() = scope.launch {
        if (!canGoBack) return@launch
        loading.value = true
        apply(repo.browse(hierarchy = rootHierarchy, popLevels = 1))
    }

    fun submitInput(text: String) = scope.launch {
        loading.value = true
        apply(repo.browse(hierarchy = rootHierarchy, input = text))
    }

    private suspend fun apply(res: BrowseResult?) {
        if (res == null) {
            message.value = "No response from Core"
            loading.value = false
            return
        }
        if (res.isError == true) {
            message.value = res.message ?: "Browse error"
            loading.value = false
            return
        }
        when (res.action) {
            "list" -> {
                title.value = res.list?.title ?: rootHierarchy.replaceFirstChar { it.uppercase() }
                level.value = res.list?.level ?: 0
                val count = (res.list?.count ?: 100).coerceIn(1, 500)
                val loaded = repo.load(hierarchy = rootHierarchy, offset = 0, count = count)
                items.value = loaded?.items ?: emptyList()
                inputPrompt.value = loaded?.items?.firstNotNullOfOrNull { it.inputPrompt?.prompt }
                message.value = null
            }
            "message" -> message.value = res.message
            "none" -> message.value = res.message // an action (e.g. Play Now) was performed
            else -> message.value = res.message
        }
        loading.value = false
    }
}
