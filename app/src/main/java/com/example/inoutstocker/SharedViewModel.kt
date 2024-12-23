package com.example.inoutstocker

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateListOf

class SharedViewModel : ViewModel() {
    val scannedItems = mutableStateListOf<Pair<String, Pair<Int, List<Int>>>>()

    fun addScannedItem(lrno: String, totalPkgs: Int, boxNo: Int) {
        val index = scannedItems.indexOfFirst { it.first == lrno }
        if (index == -1) {
            scannedItems.add(lrno to (totalPkgs to listOf(boxNo)))
        } else {
            val (existingTotalPkgs, existingBoxes) = scannedItems[index].second
            if (!existingBoxes.contains(boxNo)) {
                scannedItems[index] = lrno to (existingTotalPkgs to existingBoxes + boxNo)
            }
        }
    }

    fun clearScannedItems() {
        scannedItems.clear()
    }
}