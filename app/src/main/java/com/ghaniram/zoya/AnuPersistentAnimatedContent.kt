package com.ghaniram.zoya

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

/**
 * Tab content host used by AnuMainScreen.
 *
 * It intentionally keeps navigation state instead of disposing/recreating a tab every time
 * the bottom navigation is tapped. This removes the apparent page refresh and restores
 * rememberSaveable state such as scroll position and text-field state when a tab is reopened.
 */
@Composable
fun <S> AnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = { error("unused") },
    contentAlignment: Alignment = Alignment.TopStart,
    label: String = "AnimatedContent",
    contentKey: (targetState: S) -> Any? = { it },
    content: @Composable (targetState: S) -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredTransitionSpec = transitionSpec
    @Suppress("UNUSED_VARIABLE")
    val ignoredLabel = label
    val holder = rememberSaveableStateHolder()
    Box(modifier = modifier) {
        holder.SaveableStateProvider(contentKey(targetState) ?: "__null__") {
            Box(contentAlignment = contentAlignment) {
                content(targetState)
            }
        }
    }
}
