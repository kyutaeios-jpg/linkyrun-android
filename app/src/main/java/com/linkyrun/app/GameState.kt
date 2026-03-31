package com.linkyrun.app

data class GameState(
    val start: String,
    val goal: String,
    val difficulty: String,
    val wiki: String = "namu",
    val startTime: Long = System.currentTimeMillis(),
    var hops: Int = 0,
    var path: MutableList<String> = mutableListOf(),
    var active: Boolean = true,
    var elapsed: Long = 0L,
    var dayNum: Int? = null
)
