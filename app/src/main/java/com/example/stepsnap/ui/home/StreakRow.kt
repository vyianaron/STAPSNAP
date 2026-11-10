package com.example.stepsnap.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stepsnap.ui.theme.StepSnapColors

@Composable
fun StreakRow(
    streak: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..5).forEach { day ->
            val completed = day <= streak
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = if (completed) StepSnapColors.Lime else StepSnapColors.Card,
                        shape = CircleShape
                    )
                    .border(1.5.dp, StepSnapColors.Lime, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = StepSnapColors.Ink
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StreakRowPreview() {
    com.example.stepsnap.ui.theme.StepSnapTheme {
        StreakRow(streak = 2)
    }
}
