package com.onemind.sales.presentation.products

import com.onemind.core.presentation.util.UiText

data class ProductsState(
    val content: ProductsContent = ProductsContent.Loading,
    /** A refresh request is running while figures stay on screen. */
    val isRefreshing: Boolean = false,
    /** The chip picked; `null` = *All*. */
    val categoryId: String? = null,
    /** The row picked for the chart; `null` = the first visible one. */
    val productKey: String? = null,
) {
    val canRefresh: Boolean get() = content is ProductsContent.Ready || content is ProductsContent.Failed

    /** Products of the chosen chip. */
    val visibleProducts: List<ProductRow>
        get() = (content as? ProductsContent.Ready)?.products?.visibleProducts(categoryId).orEmpty()

    /** The product the chart shows: the picked one while visible, else the first visible. */
    val selectedProduct: ProductRow?
        get() = visibleProducts.let { list -> list.firstOrNull { it.key == productKey } ?: list.firstOrNull() }
}

sealed interface ProductsContent {
    data object Loading : ProductsContent

    data object NoVenue : ProductsContent

    data class Failed(
        val message: UiText,
    ) : ProductsContent

    data class Ready(
        val products: ProductsUi,
        val refreshError: UiText? = null,
    ) : ProductsContent
}

sealed interface ProductsAction {
    /** `null` = *All*. */
    data class SelectCategory(
        val categoryId: String?,
    ) : ProductsAction

    data class SelectProduct(
        val key: String,
    ) : ProductsAction

    data object Refresh : ProductsAction
}
