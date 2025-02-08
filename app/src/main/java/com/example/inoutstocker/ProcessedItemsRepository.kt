package com.example.inoutstocker

import android.util.Log

// ProcessedItemsRepository.kt
object ProcessedItemsRepository {
    // Using a mutable list to store processed items. You can customize the type as needed.
    private val _processedItems = mutableListOf<Map<String, Any>>()
    val processedItems: List<Map<String, Any>>
        get() = _processedItems

    fun addProcessedItems(items: List<Map<String, Any>>) {
        items.forEach { item ->
            // Extract the LRNO value from each processed item (assuming it's stored under the key "LRNO")
            val lrNo = item["LRNO"]
            Log.d("ProcessedItemsRepository", "Adding processed LRNO: $lrNo")
        }
        // Optionally, you can perform filtering or avoid duplicates here
        _processedItems.addAll(items)
        Log.d("ProcessedItemsRepository", "Total processed items count: ${_processedItems.size}")
    }

    // Helper function to extract LRNO values from the processed items.
    fun getProcessedLRNos(): List<String> {
        return _processedItems.mapNotNull { it["LRNO"] as? String }
    }

    // Optional: clear the stored items when needed
    fun clear() {
        _processedItems.clear()
        Log.d("ProcessedItemsRepository", "Cleared all processed items")
    }
}