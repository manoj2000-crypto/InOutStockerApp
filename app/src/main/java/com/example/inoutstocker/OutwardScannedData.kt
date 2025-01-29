package com.example.inoutstocker

data class OutwardScannedData(
    val lrno: String, val totalPkgs: Int, val boxNos: List<Int>
)