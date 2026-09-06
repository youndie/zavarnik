package bench

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class OrderLine(
    val sku: String,
    val qty: Int,
    val unitPrice: Double,
)

@Serializable
data class Order(
    val customer: String,
    val lines: List<OrderLine>,
    val coupon: String? = null,
    val notes: String? = null,
)

@Serializable
data class QuoteLine(
    val sku: String,
    val qty: Int,
    val net: Long,
    val discount: Long,
)

@Serializable
data class Quote(
    val customer: String,
    val lines: List<QuoteLine>,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val errors: List<String>,
)

/** Money in minor units, boxed wherever a generic position asks for it — one of the brief's boxing sites. */
@JvmInline
value class Cents(
    val value: Long,
) {
    operator fun plus(other: Cents) = Cents(value + other.value)

    operator fun times(factor: Int) = Cents(value * factor)
}

/**
 * The "business logic" endpoint's work, written the way services are written — not the way a
 * benchmark would be tuned. Every one of the brief's candidate constructs is here in its natural
 * habitat: the regex compiled per call, chains of stdlib calls with intermediate lists, a debug
 * template evaluated for a logger that is off, suspend functions returning primitives.
 */
class Pricing {
    private val logger = LoggerFactory.getLogger(Pricing::class.java)
    private val tiers = mapOf("A" to 5, "C" to 10, "E" to 15, "G" to 0, "I" to 7, "K" to 20, "M" to 3, "O" to 12)

    suspend fun quote(order: Order): Quote {
        // RQ5: a constant constructed on every request.
        val skuPattern = Regex("^[A-Z]{2}-[0-9]{4}$")
        val errors =
            order.lines
                .filter { !skuPattern.matches(it.sku) }
                .map { "bad sku: ${it.sku}" } +
                order.lines.filter { it.qty <= 0 }.map { "bad qty for ${it.sku}: ${it.qty}" }
        // RQ4: the template is built whether or not debug is on.
        logger.debug(
            "quote for ${order.customer}: ${order.lines.size} lines, coupon=${order.coupon}, notes=${order.notes}",
        )

        // RQ3: chains with intermediate lists, the way they are usually written.
        val priced =
            order.lines
                .filter { it.qty > 0 }
                .map { line -> line to Cents((line.unitPrice * CENTS).toLong()) * line.qty }
                .sortedByDescending { (_, net) -> net.value }
                .map { (line, net) -> QuoteLine(line.sku, line.qty, net.value, discountFor(line, net).value) }
        val bySku = priced.groupBy { it.sku.substringBefore('-') }
        val heaviest = bySku.entries.maxByOrNull { (_, lines) -> lines.sumOf { it.net } }?.key
        logger.debug("heaviest prefix for ${order.customer}: $heaviest across ${bySku.keys.sorted()}")

        // RQ2: suspend functions returning Int/Long box on the way out; awaited concurrently as a service would.
        val (subtotal, couponDiscount) =
            coroutineScope {
                val sub = async { subtotalOf(priced) }
                val coupon = async { couponDiscount(order.coupon, priced) }
                sub.await() to coupon.await()
            }
        val lineDiscount = priced.sumOf { it.discount }
        val total = subtotal - lineDiscount - couponDiscount
        logger.debug("total for ${order.customer}: $total (sub $subtotal, discounts $lineDiscount + $couponDiscount)")
        return Quote(order.customer, priced, subtotal, lineDiscount + couponDiscount, total, errors)
    }

    private fun discountFor(
        line: OrderLine,
        net: Cents,
    ): Cents {
        val tier = tiers[line.sku.take(1)] ?: 0
        val volume = if (line.qty >= 10) 5 else 0
        return Cents(net.value * (tier + volume) / 100)
    }

    private suspend fun subtotalOf(lines: List<QuoteLine>): Long = lines.sumOf { it.net }

    private suspend fun couponDiscount(
        coupon: String?,
        lines: List<QuoteLine>,
    ): Long {
        if (coupon == null) return 0
        val percent = coupon.filter { it.isDigit() }.toIntOrNull() ?: return 0
        val eligible = lines.filter { it.discount == 0L }.sumOf { it.net }
        return eligible * percent / 100
    }

    private companion object {
        const val CENTS = 100
    }
}
