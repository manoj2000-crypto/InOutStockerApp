package com.vtc3pl.inoutstocker

data class ExcessLRInfo(
    val lr: String,
    val scannedCount: Int,
    val totalDiff: Int,
    val missingItemsStr: String
)