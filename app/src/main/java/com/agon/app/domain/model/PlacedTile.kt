package com.agon.app.domain.model

data class PlacedTile(
    val tile: DominoTile,
    val side: BoardSide,
    val position: Int
)