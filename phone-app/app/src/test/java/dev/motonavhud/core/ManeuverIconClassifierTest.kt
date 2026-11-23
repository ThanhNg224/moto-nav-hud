package dev.motonavhud.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverIconClassifierTest {
    private fun grid(vararg rows: String): BooleanArray {
        require(rows.size == ManeuverIconClassifier.GRID)
        val out = BooleanArray(ManeuverIconClassifier.GRID * ManeuverIconClassifier.GRID)
        for (y in rows.indices) {
            require(rows[y].length == ManeuverIconClassifier.GRID)
            for (x in rows[y].indices) out[y * ManeuverIconClassifier.GRID + x] = rows[y][x] == '#'
        }
        return out
    }

    @Test fun classifiesTurnLeft() {
        val left = grid(
            "................",
            "....#...........",
            "...##...........",
            "..###...........",
            ".###########....",
            ".###########....",
            "..###.....##....",
            "...##.....##....",
            "....#.....##....",
            "..........##....",
            "..........##....",
            "..........##....",
            "..........##....",
            "..........##....",
            "..........##....",
            "................",
        )
        assertEquals(NavState.M_LEFT, ManeuverIconClassifier.classify(left))
    }

    @Test fun classifiesTurnRight() {
        val right = grid(
            "................",
            "...........#....",
            "...........##...",
            "...........###..",
            "....###########.",
            "....###########.",
            "....##.....###..",
            "....##.....##...",
            "....##.....#....",
            "....##..........",
            "....##..........",
            "....##..........",
            "....##..........",
            "....##..........",
            "....##..........",
            "................",
        )
        assertEquals(NavState.M_RIGHT, ManeuverIconClassifier.classify(right))
    }

    @Test fun classifiesStraight() {
        val straight = grid(
            ".......##.......",
            "......####......",
            ".....######.....",
            "....########....",
            "...##########...",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            "................",
        )
        assertEquals(NavState.M_STRAIGHT, ManeuverIconClassifier.classify(straight))
    }

    @Test fun rejectsBlankFullAndAmbiguous() {
        assertEquals(NavState.M_UNKNOWN, ManeuverIconClassifier.classify(BooleanArray(256)))
        assertEquals(NavState.M_UNKNOWN, ManeuverIconClassifier.classify(BooleanArray(256) { true }))
        assertEquals(NavState.M_UNKNOWN, ManeuverIconClassifier.classify(BooleanArray(10)))
    }

    @Test fun rejectsUturnAndRoundaboutToAvoidWrongDirection() {
        val uturn = grid(
            "................",
            "....########....",
            "...##########...",
            "...##......##...",
            "...##......##...",
            "...##......##...",
            "...##......##...",
            "...##......##...",
            ".######....##...",
            "..####.....##...",
            "...##......##...",
            "...........##...",
            "...........##...",
            "...........##...",
            "...........##...",
            "................",
        )
        assertEquals(NavState.M_UNKNOWN, ManeuverIconClassifier.classify(uturn))

        val roundabout = grid(
            ".......##.......",
            "......####......",
            ".....######.....",
            ".......##.......",
            ".....######.....",
            "....##....##....",
            "...##......##...",
            "...##......##...",
            "...##......##...",
            "....##....##....",
            ".....######.....",
            ".......##.......",
            ".......##.......",
            ".......##.......",
            "................",
            "................",
        )
        assertEquals(NavState.M_UNKNOWN, ManeuverIconClassifier.classify(roundabout))
    }
}
