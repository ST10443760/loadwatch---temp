package com.example.loadwatch.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.loadwatch.R
import kotlin.math.roundToInt

// An area's accuracy badge text, shared by S04 and S07: "86% schedule match from 42 reports", "No reports yet",
// or "Not known yet" before the score has been fetched. score is between 0.0 and 1.0.
@Composable
fun accuracyText(score: Double?, sampleSize: Int?): String = when {
    score == null || sampleSize == null -> stringResource(R.string.accuracy_unknown)
    sampleSize == 0 -> stringResource(R.string.accuracy_no_reports)
    else -> pluralStringResource(R.plurals.accuracy_match, sampleSize, (score * 100).roundToInt(), sampleSize)
}
