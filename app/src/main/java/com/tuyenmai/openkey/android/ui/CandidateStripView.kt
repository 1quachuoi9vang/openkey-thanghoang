package com.tuyenmai.openkey.android.ui

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tuyenmai.openkey.core.OneHandedMode
import com.tuyenmai.openkey.core.ThemePalette
import com.tuyenmai.openkey.core.WordCandidate

/**
 * Samsung Galaxy A37 One UI Top Candidate Strip (REQ-004, REQ-023, ADR-004, ADR-024).
 * - When system inline autofill suggestions (passwords / 2FA OTPs) are present: renders opaque system chips.
 * - When candidates are present: displays 3 equal-width suggestions with center primary highlight.
 * - When idle: displays a sleek empty One UI bar with zero setting buttons or text.
 */
@Composable
fun CandidateStripView(
    candidates: List<WordCandidate>,
    heightDp: Float,
    palette: ThemePalette,
    language: Int = 1,
    inputType: Int = 0,
    oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    inlineSuggestionViews: List<View> = emptyList(),
    onCandidateSelected: (WordCandidate) -> Unit = {},
    onLanguageToggled: () -> Unit = {},
    onInputTypeToggled: () -> Unit = {},
    onOneHandedCycle: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = palette
    val stripHeight = heightDp.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(stripHeight)
            .background(Color(theme.backgroundColor)),
        contentAlignment = Alignment.Center
    ) {
        if (inlineSuggestionViews.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                inlineSuggestionViews.forEach { suggestionView ->
                    key(suggestionView) {
                        AndroidView(
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    (suggestionView.parent as? ViewGroup)?.removeView(suggestionView)
                                    addView(suggestionView)
                                }
                            },
                            update = { container ->
                                if (suggestionView.parent !== container) {
                                    (suggestionView.parent as? ViewGroup)?.removeView(suggestionView)
                                    container.removeAllViews()
                                    container.addView(suggestionView)
                                }
                            }
                        )
                    }
                }
            }
        } else if (candidates.isNotEmpty()) {
            val orderedSlots = listOf(
                candidates.getOrNull(1),
                candidates.getOrNull(0),
                candidates.getOrNull(2)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                orderedSlots.forEachIndexed { index, candidate ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(Color(theme.borderColor).copy(alpha = 0.5f))
                        )
                    }

                    val isCenter = (index == 1)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .then(
                                if (isCenter && candidate != null) {
                                    Modifier.background(
                                        Color(theme.accentColor).copy(alpha = 0.12f),
                                        RoundedCornerShape(16.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                enabled = candidate != null,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                candidate?.let { onCandidateSelected(it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (candidate != null) {
                            Text(
                                text = candidate.text,
                                color = Color(theme.primaryTextColor),
                                fontSize = if (isCenter) 17.sp else 16.sp,
                                fontWeight = if (isCenter) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
