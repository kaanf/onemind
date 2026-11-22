package com.onemind.backend.pos.infra.client.mock

import com.onemind.backend.pos.domain.PosMoney
import com.onemind.backend.pos.infra.client.PosAddress
import com.onemind.backend.pos.infra.client.PosCategory
import com.onemind.backend.pos.infra.client.PosDesk
import com.onemind.backend.pos.infra.client.PosDeskView
import com.onemind.backend.pos.infra.client.PosMenu
import com.onemind.backend.pos.infra.client.PosMerchant
import com.onemind.backend.pos.infra.client.PosPerson
import com.onemind.backend.pos.infra.client.PosPlace
import com.onemind.backend.pos.infra.client.PosPlaceState
import com.onemind.backend.pos.infra.client.PosProduct
import com.onemind.backend.pos.infra.client.PosSection
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.Currency

/**
 * The fixed part of the demo POS: one merchant, its places, menu, desks and staff. Dev/demo only
 * (product rule 2); every place name starts with "Demo".
 */
internal object MockCatalog {
    const val MERCHANT_ID = "demo-merchant"
    val ZONE: ZoneId = ZoneId.of("Europe/Prague")
    val CURRENCY: Currency = Currency.getInstance("CZK")

    /** A place with what the generator needs; [billPrefix] starts its bill IDs, [traffic] scales its bills. */
    class Place(
        val place: PosPlace,
        val billPrefix: String,
        val traffic: Double,
        val deskView: PosDeskView,
    )

    class Product(
        val product: PosProduct,
        val priceMinor: Long,
        val vatPercent: Int,
        val drink: Boolean,
    )

    /** Bills per night before the place's [Place.traffic] factor. */
    val BILLS_PER_NIGHT =
        mapOf(
            DayOfWeek.MONDAY to 45,
            DayOfWeek.TUESDAY to 50,
            DayOfWeek.WEDNESDAY to 60,
            DayOfWeek.THURSDAY to 80,
            DayOfWeek.FRIDAY to 130,
            DayOfWeek.SATURDAY to 140,
            DayOfWeek.SUNDAY to 55,
        )

    val STAFF =
        listOf(
            PosPerson("demo-person-1", "Anna Demo", "anna"),
            PosPerson("demo-person-2", "Tomáš Demo", "tomas"),
            PosPerson("demo-person-3", "Lucie Demo", "lucie"),
            PosPerson("demo-person-4", "Jakub Demo", "jakub"),
            PosPerson("demo-person-5", "Petra Demo", "petra"),
            PosPerson("demo-person-6", "Marek Demo", "marek"),
        )

    val CATEGORIES =
        listOf(
            PosCategory("demo-cat-beer", "Beer", null),
            PosCategory("demo-cat-wine", "Wine", null),
            PosCategory("demo-cat-spirits", "Spirits", null),
            PosCategory("demo-cat-soft", "Soft drinks & coffee", null),
            PosCategory("demo-cat-food", "Food", null),
        )

    val PRODUCTS =
        listOf(
            product(1, "beer", "Lager 0.5 l", "0.5 l", 6900, 21),
            product(2, "beer", "Lager 0.3 l", "0.3 l", 4900, 21),
            product(3, "beer", "Dark lager 0.5 l", "0.5 l", 6500, 21),
            product(4, "beer", "IPA 0.4 l", "0.4 l", 8900, 21),
            product(5, "beer", "Non-alcoholic beer 0.33 l", "0.33 l", 5500, 21),
            product(6, "wine", "House white 0.15 l", "0.15 l", 7900, 21),
            product(7, "wine", "House red 0.15 l", "0.15 l", 7900, 21),
            product(8, "wine", "Prosecco 0.1 l", "0.1 l", 9900, 21),
            product(9, "wine", "Riesling bottle 0.75 l", "0.75 l", 49000, 21),
            product(10, "spirits", "Rum 4 cl", "0.04 l", 9900, 21),
            product(11, "spirits", "Gin & tonic", "pcs", 12900, 21),
            product(12, "spirits", "Herbal liqueur 4 cl", "0.04 l", 7900, 21),
            product(13, "spirits", "Whisky 4 cl", "0.04 l", 13900, 21),
            product(14, "soft", "Cola 0.33 l", "0.33 l", 5900, 12),
            product(15, "soft", "Homemade lemonade 0.4 l", "0.4 l", 6900, 12),
            product(16, "soft", "Still water 0.75 l", "0.75 l", 7900, 12),
            product(17, "soft", "Espresso", "pcs", 5500, 12),
            product(18, "soft", "Cappuccino", "pcs", 6900, 12),
            product(19, "food", "Fried cheese", "pcs", 22900, 12, drink = false),
            product(20, "food", "Beef goulash", "pcs", 24900, 12, drink = false),
            product(21, "food", "Burger", "pcs", 28900, 12, drink = false),
            product(22, "food", "Nachos", "pcs", 16900, 12, drink = false),
            product(23, "food", "Pickled sausage", "pcs", 11900, 12, drink = false),
            product(24, "food", "Fries", "pcs", 8900, 12, drink = false),
        )

    val MENU = PosMenu(CATEGORIES, PRODUCTS.map { it.product })

    val PLACES =
        listOf(
            Place(
                PosPlace("demo-letna", "Demo · Bar Letná", PosPlaceState.ACTIVE, PosAddress("Demo 1", "Praha", "170 00", "CZ")),
                billPrefix = "LT",
                traffic = 1.0,
                deskView = desks("letna"),
            ),
            Place(
                PosPlace("demo-karlin", "Demo · Bistro Karlín", PosPlaceState.ACTIVE, PosAddress("Demo 2", "Praha", "186 00", "CZ")),
                billPrefix = "KR",
                traffic = 0.7,
                deskView = desks("karlin"),
            ),
            Place(
                PosPlace("demo-oldtown", "Demo · Old Town (closed)", PosPlaceState.DISABLED, PosAddress("Demo 3", "Praha", "110 00", "CZ")),
                billPrefix = "OT",
                traffic = 0.0,
                deskView = desks("oldtown"),
            ),
        )

    val MERCHANT =
        PosMerchant(
            merchantId = MERCHANT_ID,
            name = "Demo Hospitality",
            businessId = null,
            vatId = null,
            isVatPayer = true,
            countryCode = "CZ",
            currencyCode = CURRENCY.currencyCode,
            places = PLACES.map { it.place },
        )

    fun place(placeId: String): Place? = PLACES.find { it.place.placeId == placeId }

    fun placeByBillPrefix(prefix: String): Place? = PLACES.find { it.billPrefix == prefix }

    private fun product(
        number: Int,
        category: String,
        name: String,
        measure: String,
        priceMinor: Long,
        vatPercent: Int,
        drink: Boolean = true,
    ) = Product(
        PosProduct(
            productId = "demo-product-$number",
            categoryId = "demo-cat-$category",
            name = name,
            measure = measure,
            ean = null,
            price = PosMoney.decimal(priceMinor, CURRENCY),
            vatRate = vatPercent.toString(),
            enabled = true,
        ),
        priceMinor,
        vatPercent,
        drink,
    )

    private fun desks(place: String): PosDeskView {
        val sections =
            listOf(
                PosSection("demo-$place-hall", "Hall", "#4A90E2"),
                PosSection("demo-$place-bar", "Bar", "#F5A623"),
                PosSection("demo-$place-terrace", "Terrace", "#7ED321"),
            )
        val desks =
            (1..8).map { PosDesk("demo-$place-t$it", "demo-$place-hall", "Table $it", "T$it", virtual = false, removed = false) } +
                (1..2).map { PosDesk("demo-$place-b$it", "demo-$place-bar", "Bar $it", "B$it", virtual = false, removed = false) } +
                (1..2).map { PosDesk("demo-$place-g$it", "demo-$place-terrace", "Garden $it", "G$it", virtual = false, removed = false) } +
                PosDesk("demo-$place-takeaway", null, "Takeaway", null, virtual = true, removed = false)
        return PosDeskView(sections, desks)
    }
}
