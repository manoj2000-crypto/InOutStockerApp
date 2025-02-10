package com.example.inoutstocker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SharedViewModel : ViewModel() {
    enum class FeatureType { INWARD, OUTWARD, AUDIT }

    private val _featureType = mutableStateOf(FeatureType.INWARD)
    val featureType: FeatureType get() = _featureType.value

    private val inwardScannedItems = mutableStateListOf<Pair<String, Pair<Int, List<Int>>>>()
    private val outwardScannedItems = mutableStateListOf<Pair<String, Pair<Int, List<Int>>>>()
    private val auditScannedItems = mutableStateListOf<Pair<String, Pair<Int, List<Int>>>>()

    val scannedItems: List<Pair<String, Pair<Int, List<Int>>>>
        get() = when (_featureType.value) {
            FeatureType.INWARD -> inwardScannedItems
            FeatureType.OUTWARD -> outwardScannedItems
            FeatureType.AUDIT -> auditScannedItems
        }

    fun setFeatureType(type: FeatureType) {
        _featureType.value = type
    }

    fun addScannedItem(lrno: String, totalPkgs: Int, boxNo: Int) {
        val targetList = when (_featureType.value) {
            FeatureType.INWARD -> inwardScannedItems
            FeatureType.OUTWARD -> outwardScannedItems
            FeatureType.AUDIT -> auditScannedItems
        }

        val index = targetList.indexOfFirst { it.first == lrno }
        if (index == -1) {
            targetList.add(lrno to (totalPkgs to listOf(boxNo)))
        } else {
            val (existingTotalPkgs, existingBoxes) = targetList[index].second
            if (!existingBoxes.contains(boxNo)) {
                targetList[index] = lrno to (existingTotalPkgs to existingBoxes + boxNo)
            }
        }
    }

    fun clearScannedItems() {
        when (_featureType.value) {
            FeatureType.INWARD -> inwardScannedItems.clear()
            FeatureType.OUTWARD -> outwardScannedItems.clear()
            FeatureType.AUDIT -> auditScannedItems.clear()
        }
    }

    // New methods for clearing only a specific feature's data
    fun clearInwardScannedItems() {
        inwardScannedItems.clear()
        //CLEAR OTHER DATA HERE LIKE CLASSES , OBJECTS, ETC
    }

    fun clearOutwardScannedItems() {
        outwardScannedItems.clear()
    }

    fun clearAuditScannedItems() {
        auditScannedItems.clear()
    }

    fun clearOutwardData() {
        outwardScannedItems.clear() // Clears only Outward Scanned Items
        _outwardScannedData.clear() // Clears only Outward-specific scanned data
    }

    // ---- New Methods for Outward Scanned Data ----
    private val _outwardScannedData = mutableStateListOf<Pair<String, Pair<Int, List<Int>>>>()
    val outwardScannedData: List<Pair<String, Pair<Int, List<Int>>>> get() = _outwardScannedData

    fun setOutwardScannedData(data: List<Pair<String, Pair<Int, List<Int>>>>) {
        _outwardScannedData.clear()
        _outwardScannedData.addAll(data)
    }

    var fromDate by mutableStateOf(getPreviousDate()) // Default: Previous Date
    var toDate by mutableStateOf(getCurrentDate())   // Default: Current Date
    var tableData by mutableStateOf<List<TableRowData>>(emptyList())

    fun setDates(from: String, to: String) {
        fromDate = from
        toDate = to
    }

    fun updateTableData(data: List<TableRowData>) {
        tableData = data
    }

    // Use a mutable state list so that Compose is aware when items change.
    private val _processedExcessLrs = mutableStateListOf<String>()
    val processedExcessLrs: List<String> get() = _processedExcessLrs

    fun addProcessedExcessLr(lr: String) {
        if (!_processedExcessLrs.contains(lr)) {
            _processedExcessLrs.add(lr)
        }
    }

}

// Utility Functions for Default Dates
fun getCurrentDate(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    return sdf.format(Date())
}

fun getPreviousDate(): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_MONTH, -1) // Subtract one day
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    return sdf.format(calendar.time)
}