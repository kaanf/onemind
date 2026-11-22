package com.onemind.backend.pos

import com.onemind.backend.pos.infra.client.PosBillDetail
import com.onemind.backend.pos.infra.client.PosBillPage
import com.onemind.backend.pos.infra.client.PosBillQuery
import com.onemind.backend.pos.infra.client.PosDeskView
import com.onemind.backend.pos.infra.client.PosMenu
import com.onemind.backend.pos.infra.client.PosMerchant
import com.onemind.backend.pos.infra.client.PosPlaceRef
import com.onemind.backend.pos.infra.client.PosPlaceState
import com.onemind.backend.pos.infra.client.PosProvider
import com.onemind.backend.pos.infra.client.mock.MockPosProvider
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The demo POS under any merchant ID starting with [PREFIX] (so every test has its own connection),
 * with knobs to change what it reports, and a check that no POS call runs inside a transaction.
 */
class TestPosProvider(
    private val mock: MockPosProvider,
) : PosProvider {
    companion object {
        const val PREFIX = "test-demo-"
    }

    val callsInTransaction = mutableListOf<String>()
    val failing = mutableSetOf<String>()
    var droppedProduct: String? = null
    var droppedDesk: String? = null
    var renamedPlace: Pair<String, String>? = null
    var disabledPlace: String? = null
    var hiddenPlace: String? = null

    /** Calls per place: `"menu:demo-karlin"`, …, to see what the sync asked for. */
    val placeCalls = mutableListOf<String>()

    /** This product's price comes with more fraction digits than the currency has. */
    var subMinorPriceProduct: String? = null

    /** Menus of this place fail. */
    var failingMenuPlace: String? = null

    /** Bill details of nights on or after this date fail, as if the POS went away mid-import. */
    var failDetailsFrom: LocalDate? = null

    private fun call(
        what: String,
        merchantId: String,
    ) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) callsInTransaction += what
        check(merchantId !in failing) { "POS unavailable" }
    }

    private fun PosPlaceRef.demo() = copy(merchantId = "demo-merchant")

    override fun merchant(merchantId: String): PosMerchant? {
        call("merchant", merchantId)
        if (!merchantId.startsWith(PREFIX)) return null
        val merchant = mock.merchant("demo-merchant") ?: return null
        return merchant.copy(
            merchantId = merchantId,
            places =
                merchant.places
                    .map { place ->
                        place.copy(
                            name = renamedPlace?.takeIf { it.first == place.placeId }?.second ?: place.name,
                            state = if (place.placeId == disabledPlace) PosPlaceState.DISABLED else place.state,
                        )
                    }.filterNot { it.placeId == hiddenPlace },
        )
    }

    override fun bills(
        place: PosPlaceRef,
        query: PosBillQuery,
    ): PosBillPage {
        call("bills", place.merchantId)
        placeCalls += "bills:${place.placeId}"
        return mock.bills(place.demo(), query)
    }

    override fun billDetail(
        place: PosPlaceRef,
        billId: String,
    ): PosBillDetail? {
        call("billDetail", place.merchantId)
        val night = LocalDate.parse(billId.substring(2, 10), DateTimeFormatter.BASIC_ISO_DATE)
        check(failDetailsFrom?.let { night.isBefore(it) } ?: true) { "POS unavailable" }
        return mock.billDetail(place.demo(), billId)
    }

    override fun deskView(place: PosPlaceRef): PosDeskView {
        call("deskView", place.merchantId)
        placeCalls += "deskView:${place.placeId}"
        val view = mock.deskView(place.demo())
        return view.copy(desks = view.desks.filterNot { it.deskId == droppedDesk })
    }

    override fun menu(place: PosPlaceRef): PosMenu {
        call("menu", place.merchantId)
        placeCalls += "menu:${place.placeId}"
        check(place.placeId != failingMenuPlace) { "POS unavailable" }
        val menu = mock.menu(place.demo())
        return menu.copy(
            products =
                menu.products
                    .filterNot { it.productId == droppedProduct }
                    .map { if (it.productId == subMinorPriceProduct) it.copy(price = "12.345") else it },
        )
    }

    fun reset() {
        callsInTransaction.clear()
        failing.clear()
        droppedProduct = null
        droppedDesk = null
        renamedPlace = null
        disabledPlace = null
        hiddenPlace = null
        failDetailsFrom = null
        placeCalls.clear()
        subMinorPriceProduct = null
        failingMenuPlace = null
    }
}
