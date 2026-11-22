package com.onemind.backend.pos.infra.client.mock

import com.onemind.backend.pos.domain.PosMoney
import com.onemind.backend.pos.infra.client.PosBill
import com.onemind.backend.pos.infra.client.PosBillDetail
import com.onemind.backend.pos.infra.client.PosBillItem
import com.onemind.backend.pos.infra.client.PosItemDiscount
import com.onemind.backend.pos.infra.client.PosPayment
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.SplittableRandom

/**
 * The bills of one place and night, from 16:00 until before 05:00 (Europe/Prague), seeded by place and
 * date: the same night always gives the same bills, whatever was asked before. Times are built from
 * local times (DST gaps move forward, overlaps get both offsets); refunds and deletions happen after
 * payment and only show once the clock has passed them ([GeneratedBill.asOf]).
 */
internal object MockBillGenerator {
    private val OPENING = LocalTime.of(16, 0)
    private const val LAST_SESSION_MINUTE = 630 // 02:30
    private val LAST_PAYMENT = LocalTime.of(4, 55)
    private val FIXED_SESSION = LocalTime.of(1, 45)
    private val FIXED_PAYMENT = LocalTime.of(2, 30)
    private const val HAPPY_HOUR_MINUTES = 120 // 16:00–18:00
    private val BILL_DATE = DateTimeFormatter.BASIC_ISO_DATE

    /** A bill as it ends up; [asOf] gives what the POS shows at a given instant. */
    class GeneratedBill(
        private val detail: PosBillDetail,
        private val refundedAt: OffsetDateTime?,
        private val deletedAt: OffsetDateTime?,
    ) {
        val billId: String get() = detail.bill.billId

        /** The bill as of [now]: `null` before it is paid, refunded or deleted only once that happened. */
        fun asOf(now: Instant): PosBillDetail? {
            val paidAt = detail.bill.paidAt
            if (paidAt.toInstant().isAfter(now)) return null
            val refunded = refundedAt?.takeUnless { it.toInstant().isAfter(now) }
            val deleted = deletedAt?.takeUnless { it.toInstant().isAfter(now) }
            val lastModifiedAt = listOfNotNull(paidAt, refunded, deleted).maxBy { it.toInstant() }
            return detail.copy(
                bill = detail.bill.copy(refunded = refunded != null, deleted = deleted != null, lastModifiedAt = lastModifiedAt),
            )
        }
    }

    fun night(
        place: MockCatalog.Place,
        date: LocalDate,
    ): List<GeneratedBill> {
        val random = SplittableRandom("${place.place.placeId}|$date".hashCode().toLong())
        val base = MockCatalog.BILLS_PER_NIGHT.getValue(date.dayOfWeek) * place.traffic
        val count = (base * (0.85 + random.nextDouble() * 0.3)).toInt()
        val lastPayment = date.plusDays(1).atTime(LAST_PAYMENT).atZone(MockCatalog.ZONE)

        val bills =
            (1..count).map { seq ->
                val sessionMinute = (random.nextInt(LAST_SESSION_MINUTE + 1) + random.nextInt(LAST_SESSION_MINUTE + 1)) / 2
                val session = date.atTime(OPENING).plusMinutes(sessionMinute.toLong()).atZone(MockCatalog.ZONE)
                val paid = minOf(session.plusMinutes(random.nextLong(15, 151)), lastPayment)
                bill(place, billId(place, date, seq), session, paid, sessionMinute < HAPPY_HOUR_MINUTES, random)
            }
        // One bill paid at 02:30 every night: on the autumn DST night once per offset, in spring at 03:30.
        val fixedSession = LocalDateTime.of(date.plusDays(1), FIXED_SESSION).atZone(MockCatalog.ZONE)
        val fixedPaid = LocalDateTime.of(date.plusDays(1), FIXED_PAYMENT).atZone(MockCatalog.ZONE)
        val fixed =
            listOf(fixedPaid.withEarlierOffsetAtOverlap(), fixedPaid.withLaterOffsetAtOverlap()).distinct().mapIndexed { i, paid ->
                bill(place, billId(place, date, 900 + i), fixedSession, paid, happyHour = false, random = random)
            }
        return bills + fixed
    }

    fun billId(
        place: MockCatalog.Place,
        date: LocalDate,
        seq: Int,
    ) = "${place.billPrefix}${date.format(BILL_DATE)}${seq.toString().padStart(3, '0')}"

    /** The place and night a bill ID of [billId] belongs to, or `null` if it isn't one of ours. */
    fun parseBillId(billId: String): Pair<MockCatalog.Place, LocalDate>? {
        if (billId.length != 13) return null
        val place = MockCatalog.placeByBillPrefix(billId.take(2)) ?: return null
        val date = runCatching { LocalDate.parse(billId.substring(2, 10), BILL_DATE) }.getOrNull() ?: return null
        return place to date
    }

    private fun bill(
        place: MockCatalog.Place,
        billId: String,
        session: ZonedDateTime,
        paid: ZonedDateTime,
        happyHour: Boolean,
        random: SplittableRandom,
    ): GeneratedBill {
        val desks = place.deskView.desks
        val desk = if (random.nextInt(100) < 8) desks.last() else desks[random.nextInt(desks.size - 1)]
        val guests = 1 + random.nextInt(5)
        val lines = (1..guests + random.nextInt(guests * 2)).map { line(random, happyHour) }

        val totalMinor = lines.sumOf { it.totalMinor }
        val withoutTaxMinor = lines.sumOf { it.withoutTaxMinor }
        val discountMinor = lines.sumOf { it.discountMinor }
        val byCard = random.nextInt(100) < 65
        val tipsMinor = if (byCard && random.nextInt(100) < 35) roundToCrowns(totalMinor * random.nextLong(5, 11) / 100) else 0
        val payments =
            if (random.nextInt(100) < 5) {
                val half = roundToCrowns(totalMinor / 2)
                listOf(payment(CARD, half + tipsMinor), payment(CASH, totalMinor - half))
            } else {
                listOf(payment(if (byCard) CARD else CASH, totalMinor + tipsMinor))
            }
        val creator = MockCatalog.STAFF[random.nextInt(MockCatalog.STAFF.size)]
        val payer = if (random.nextInt(100) < 80) creator else MockCatalog.STAFF[random.nextInt(MockCatalog.STAFF.size)]
        val createdAt = maxOf(session, paid.minusMinutes(2))
        val outcome = random.nextInt(100)

        val bill =
            PosBill(
                billId = billId,
                sessionCreated = session.toOffsetDateTime(),
                createdAt = createdAt.toOffsetDateTime(),
                paidAt = paid.toOffsetDateTime(),
                fiscalizedAt = paid.plusSeconds(3).toOffsetDateTime(),
                finalPrice = money(totalMinor),
                finalPriceWithoutTax = money(withoutTaxMinor),
                discount = money(discountMinor),
                rounding = money(0),
                tips = money(tipsMinor),
                currencyCode = MockCatalog.CURRENCY.currencyCode,
                refunded = false,
                deleted = false,
                paymentMethod = payments.first().paymentMethod,
                payments = payments,
                createdBy = creator,
                paidBy = payer,
                personCount = guests,
                deskId = desk.deskId,
                lastModifiedAt = paid.toOffsetDateTime(),
            )
        return GeneratedBill(
            PosBillDetail(bill, lines.map { it.item }),
            refundedAt = paid.plusHours(20).toOffsetDateTime().takeIf { outcome == 0 },
            deletedAt = paid.plusMinutes(10).toOffsetDateTime().takeIf { outcome == 1 },
        )
    }

    private class Line(
        val item: PosBillItem,
        val totalMinor: Long,
        val withoutTaxMinor: Long,
        val discountMinor: Long,
    )

    private fun line(
        random: SplittableRandom,
        happyHour: Boolean,
    ): Line {
        val drinks = MockCatalog.PRODUCTS.filter { it.drink }
        val food = MockCatalog.PRODUCTS.filterNot { it.drink }
        val product = if (random.nextInt(100) < 70) drinks[random.nextInt(drinks.size)] else food[random.nextInt(food.size)]
        val amount = if (random.nextInt(100) < 80) 1L else 2L
        val originalMinor = product.priceMinor * amount
        val discount =
            when {
                happyHour && product.drink && random.nextInt(100) < 40 -> percentDiscount(originalMinor, 20, "HAPPY_HOUR")
                random.nextInt(100) < 4 -> percentDiscount(originalMinor, 50, "EMPLOYEE")
                random.nextInt(100) < 3 -> percentDiscount(originalMinor, 10, "LOYALTY")
                random.nextInt(100) < 2 -> absoluteDiscount(originalMinor, minOf(5000, originalMinor), "MANAGER")
                else -> null
            }
        val discountMinor = discount?.second ?: 0
        val totalMinor = originalMinor - discountMinor
        val withoutTaxMinor =
            BigDecimal.valueOf(totalMinor * 100).divide(BigDecimal.valueOf(100L + product.vatPercent), 0, RoundingMode.HALF_UP).toLong()
        val item =
            PosBillItem(
                name = product.product.name,
                amount = amount.toString(),
                measure = product.product.measure,
                price = money(product.priceMinor),
                vatRate = product.vatPercent.toString(),
                productId = product.product.productId,
                categoryId = product.product.categoryId,
                ean = null,
                discounts = listOfNotNull(discount?.first),
            )
        return Line(item, totalMinor, withoutTaxMinor, discountMinor)
    }

    private fun percentDiscount(
        originalMinor: Long,
        percent: Int,
        type: String,
    ): Pair<PosItemDiscount, Long> {
        val discountMinor = originalMinor * percent / 100
        return PosItemDiscount(money(originalMinor), "PERCENT", percent.toString(), null, type, null) to discountMinor
    }

    private fun absoluteDiscount(
        originalMinor: Long,
        discountMinor: Long,
        type: String,
    ): Pair<PosItemDiscount, Long> =
        PosItemDiscount(money(originalMinor), "ABSOLUTE", null, money(discountMinor), type, null) to discountMinor

    private fun payment(
        method: String,
        minor: Long,
    ) = PosPayment(method, money(minor))

    private fun roundToCrowns(minor: Long) = (minor + 50) / 100 * 100

    private fun money(minor: Long) = PosMoney.decimal(minor, MockCatalog.CURRENCY)

    private const val CARD = "card"
    private const val CASH = "cash"
}
