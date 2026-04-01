package com.linkyrun.app

data class GameState(
    val start: String,
    val goal: String,
    val difficulty: String,
    val wiki: String = "namu",
    var startTime: Long = 0L,
    var hops: Int = 0,
    var path: MutableList<String> = mutableListOf(),
    var active: Boolean = true,
    var elapsed: Long = 0L,
    var dayNum: Int? = null
)
