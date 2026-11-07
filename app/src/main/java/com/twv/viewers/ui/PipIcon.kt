package com.twv.viewers.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Иконка «картинка в картинке»: рамка окна и маленькое окошко в углу (в material-icons-core её нет). */
val PipIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PictureInPicture", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
        // внешний прямоугольник и внутренний — вместе дают рамку
        moveTo(1f, 3f); lineTo(23f, 3f); lineTo(23f, 21f); lineTo(1f, 21f); close()
        moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
        // маленькое окно
        moveTo(11f, 11f); lineTo(19f, 11f); lineTo(19f, 17f); lineTo(11f, 17f); close()
    }.build()
}
