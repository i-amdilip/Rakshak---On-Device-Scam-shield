package com.example.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

fun rakshakTypography(elderlyMode: Boolean): Typography {
    val bodySize = if (elderlyMode) 18.sp else 15.sp
    val bodyLine = if (elderlyMode) 26.sp else 22.sp
    val labelSize = if (elderlyMode) 15.sp else 13.sp
    val titleMed = if (elderlyMode) 20.sp else 17.sp
    val titleLarge = if (elderlyMode) 23.sp else 20.sp
    val headSmall = if (elderlyMode) 25.sp else 22.sp
    val headMed = if (elderlyMode) 29.sp else 26.sp

    return Typography(
        headlineMedium = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = headMed,
            lineHeight = headMed * 1.18f,
            letterSpacing = (-0.5).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = headSmall,
            lineHeight = headSmall * 1.22f,
            letterSpacing = (-0.3).sp
        ),
        titleLarge = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = titleLarge,
            lineHeight = titleLarge * 1.22f,
            letterSpacing = (-0.2).sp
        ),
        titleMedium = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = titleMed,
            lineHeight = titleMed * 1.24f,
            letterSpacing = (-0.1).sp
        ),
        titleSmall = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (elderlyMode) 18.sp else 15.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = bodySize,
            lineHeight = bodyLine
        ),
        bodyMedium = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = if (elderlyMode) 17.sp else 14.sp,
            lineHeight = if (elderlyMode) 24.sp else 20.sp
        ),
        bodySmall = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = if (elderlyMode) 15.sp else 13.sp,
            lineHeight = if (elderlyMode) 21.sp else 18.sp
        ),
        labelLarge = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = labelSize
        ),
        labelMedium = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (elderlyMode) 14.sp else 12.sp
        ),
        labelSmall = TextStyle(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = if (elderlyMode) 13.sp else 11.sp
        )
    )
}
