package com.kite.folding.pape.rmobileclean.img

data class PhotoItem(
    val id: Long,
    val path: String,
    val size: Long,
    val dateAdded: Long,
    val displayName: String,
    var isSelected: Boolean = false
)

data class PhotoGroup(
    val date: String,
    val photos: MutableList<PhotoItem>,
    var isAllSelected: Boolean = false
) {
    fun getTotalSize(): Long {
        return photos.sumOf { it.size }
    }
}