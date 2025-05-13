package com.vtc3pl.inoutstocker.utils

/**
 * Parses scanned barcode data into a Triple containing LRNO, PkgsNo, and BoxNo.
 * @param data The scanned barcode data in the format "LRNO=PNA0001009366;PkgsNo=28;BoxNo=1;"
 * @return A Triple containing LRNO, PkgsNo, and BoxNo, or null if parsing fails.
 */

fun parseScannedData(data: String): Triple<String, Int, Int>? {
    val map = data.split(";").mapNotNull {
        val parts = it.split("=")
        if (parts.size == 2) parts[0] to parts[1] else null
    }.toMap()

    val lrno = map["LRNO"] ?: return null
    val pkgsNo = map["PkgsNo"]?.toIntOrNull() ?: return null
    val boxNo = map["BoxNo"]?.toIntOrNull() ?: return null

    return Triple(lrno, pkgsNo, boxNo)
}