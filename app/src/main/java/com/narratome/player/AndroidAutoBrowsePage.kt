package com.narratome.player

internal data class AndroidAutoBrowsePage(
    val limit: Int,
    val offset: Int,
) {
    companion object {
        private const val DEFAULT_PAGE_SIZE = 50
        private const val MAX_PAGE_SIZE = 200

        fun from(page: Int, pageSize: Int): AndroidAutoBrowsePage {
            val safePage = page.coerceAtLeast(0)
            val safePageSize = pageSize
                .takeIf { it > 0 }
                ?.coerceAtMost(MAX_PAGE_SIZE)
                ?: DEFAULT_PAGE_SIZE
            val offset = (safePage.toLong() * safePageSize)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            return AndroidAutoBrowsePage(limit = safePageSize, offset = offset)
        }
    }
}
