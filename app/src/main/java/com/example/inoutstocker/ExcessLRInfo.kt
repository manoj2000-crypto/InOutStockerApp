package com.example.inoutstocker

data class ExcessLRInfo(
    val lr: String,
    val scannedCount: Int,
    val totalDiff: Int,
    val missingItemsStr: String
)