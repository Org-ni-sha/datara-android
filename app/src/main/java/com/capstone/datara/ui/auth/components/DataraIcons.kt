package com.capstone.datara.ui.auth.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object DataraIcons {
    val More: ImageVector by lazy { outlineIcon("More") {
        moveTo(5f, 11f); lineTo(5f, 13f); moveTo(12f, 11f); lineTo(12f, 13f); moveTo(19f, 11f); lineTo(19f, 13f)
    } }
    val ArrowForward: ImageVector by lazy { outlineIcon("ArrowForward") {
        moveTo(4f, 12f); lineTo(20f, 12f); moveTo(14f, 6f); lineTo(20f, 12f); lineTo(14f, 18f)
    } }
    val Chevron: ImageVector by lazy { outlineIcon("Chevron") {
        moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
    } }
    val Clock: ImageVector by lazy { outlineIcon("Clock") {
        moveTo(21f, 12f); curveTo(21f, 0f, 3f, 0f, 3f, 12f); curveTo(3f, 24f, 21f, 24f, 21f, 12f)
        moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14f)
    } }
    val Data: ImageVector by lazy { outlineIcon("Data") {
        moveTo(5f, 4f); lineTo(5f, 20f); moveTo(1f, 16f); lineTo(5f, 20f); lineTo(9f, 16f)
        moveTo(18f, 20f); lineTo(18f, 4f); moveTo(14f, 8f); lineTo(18f, 4f); lineTo(22f, 8f)
    } }
    val Shield: ImageVector by lazy { outlineIcon("Shield") {
        moveTo(12f, 2f); lineTo(21f, 6f); lineTo(20f, 15f); curveTo(19f, 19f, 15f, 21f, 12f, 23f)
        curveTo(9f, 21f, 5f, 19f, 4f, 15f); lineTo(3f, 6f); close()
        moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f)
    } }
    val Plus: ImageVector by lazy { outlineIcon("Plus") {
        moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f)
    } }
    val Bell: ImageVector by lazy {
        outlineIcon("Bell") {
            moveTo(5f, 10f); curveTo(5f, 1f, 19f, 1f, 19f, 10f)
            lineTo(19f, 15f); lineTo(21f, 18f); lineTo(3f, 18f); lineTo(5f, 15f); close()
            moveTo(9f, 21f); curveTo(10f, 23f, 14f, 23f, 15f, 21f)
        }
    }
    val Home: ImageVector by lazy {
        outlineIcon("Home") {
            moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
            moveTo(5f, 9f); lineTo(5f, 21f); lineTo(10f, 21f)
            lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 21f)
            lineTo(19f, 21f); lineTo(19f, 9f)
        }
    }
    val History: ImageVector by lazy {
        outlineIcon("History") {
            moveTo(4f, 9f); curveTo(6f, 2f, 17f, 1f, 21f, 9f)
            curveTo(25f, 20f, 8f, 26f, 4f, 17f)
            moveTo(3f, 4f); lineTo(3f, 10f); lineTo(9f, 10f)
            moveTo(12f, 7f); lineTo(12f, 13f); lineTo(16f, 15f)
        }
    }
    val Settings: ImageVector by lazy {
        outlineIcon("Settings") {
            moveTo(5f, 3f); lineTo(5f, 8f); moveTo(5f, 12f); lineTo(5f, 21f)
            moveTo(12f, 3f); lineTo(12f, 14f); moveTo(12f, 18f); lineTo(12f, 21f)
            moveTo(19f, 3f); lineTo(19f, 6f); moveTo(19f, 10f); lineTo(19f, 21f)
            moveTo(2f, 8f); lineTo(8f, 8f); lineTo(8f, 12f); lineTo(2f, 12f); close()
            moveTo(9f, 14f); lineTo(15f, 14f); lineTo(15f, 18f); lineTo(9f, 18f); close()
            moveTo(16f, 6f); lineTo(22f, 6f); lineTo(22f, 10f); lineTo(16f, 10f); close()
        }
    }

    private fun outlineIcon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()
    val Email: ImageVector by lazy {
        ImageVector.Builder(
            name = "DataraEmail",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                // Rounded Mail Outline & Flap
                moveTo(20f, 4f)
                horizontalLineTo(4f)
                curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(20f, 8f)
                lineToRelative(-8f, 5f)
                lineToRelative(-8f, -5f)
                verticalLineTo(6f)
                lineToRelative(8f, 5f)
                lineToRelative(8f, -5f)
                verticalLineToRelative(2f)
                close()
            }
        }.build()
    }

    val Person: ImageVector by lazy {
        ImageVector.Builder(
            name = "DataraPerson",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                // Head
                moveTo(12f, 12f)
                curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
                reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
                reflectiveCurveToRelative(-4f, 1.79f, -4f, 4f)
                reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                close()
                // Shoulders
                moveTo(12f, 14f)
                curveToRelative(-2.67f, 0f, -8f, 1.34f, -8f, 4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
                close()
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "DataraLock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                // Padlock Shackle & Body
                moveTo(18f, 8f)
                horizontalLineToRelative(-1f)
                verticalLineTo(6f)
                curveToRelative(0f, -2.76f, -2.24f, -5f, -5f, -5f)
                reflectiveCurveTo(7f, 3.24f, 7f, 6f)
                verticalLineToRelative(2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(10f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(9f, 6f)
                curveToRelative(0f, -1.66f, 1.34f, -3f, 3f, -3f)
                reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
                verticalLineToRelative(2f)
                horizontalLineTo(9f)
                verticalLineTo(6f)
                close()
                moveTo(12f, 17f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
                close()
            }
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "DataraArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(20f, 11f)
                horizontalLineTo(7.83f)
                lineToRelative(5.59f, -5.59f)
                lineTo(12f, 4f)
                lineToRelative(-8f, 8f)
                lineToRelative(8f, 8f)
                lineToRelative(1.41f, -1.41f)
                lineTo(7.83f, 13f)
                horizontalLineTo(20f)
                verticalLineToRelative(-2f)
                close()
            }
        }.build()
    }

    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "DataraCheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(10f, 17f)
                lineToRelative(-5f, -5f)
                lineToRelative(1.41f, -1.41f)
                lineTo(10f, 14.17f)
                lineToRelative(7.59f, -7.59f)
                lineTo(19f, 8f)
                lineToRelative(-9f, 9f)
                close()
            }
        }.build()
    }
}

