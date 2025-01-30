package com.example.inoutstocker

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

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

    // ---- New Methods for Outward Scanned Data ----
    private val _outwardScannedData = mutableStateListOf<Pair<String, Pair<Int, List<Int>>>>()
    val outwardScannedData: List<Pair<String, Pair<Int, List<Int>>>> get() = _outwardScannedData

    fun setOutwardScannedData(data: List<Pair<String, Pair<Int, List<Int>>>>) {
        _outwardScannedData.clear()
        _outwardScannedData.addAll(data)
    }

}