package sample

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class Item(
    val id: Int,
    val name: String,
    val tags: List<String>,
    val price: Double,
)

@Serializable
data class Order(
    val items: List<Item>,
    val note: String? = null,
)

@Serializable
data class Receipt(
    val count: Int,
    val total: Double,
    val note: String?,
)

/** A few routes that exercise coroutines, serialization and indy lambdas — the classes RQ4 asks about. */
fun main() {
    val port = System.getProperty("sample.port")?.toInt() ?: 8080
    embeddedServer(CIO, port = port) {
        install(ContentNegotiation) { json() }
        routing {
            get("/health") { call.respond(HttpStatusCode.OK, mapOf("status" to "ok")) }
            get("/api/warm") {
                val items =
                    coroutineScope {
                        (1..50)
                            .map { i ->
                                async {
                                    delay(1)
                                    Item(i, "item-$i", listOf("a", "b", "c$i"), i * 1.5)
                                }
                            }.awaitAll()
                    }
                call.respond(items)
            }
            post("/api/order") {
                val order = call.receive<Order>()
                call.respond(Receipt(order.items.size, order.items.sumOf { it.price }, order.note))
            }
        }
    }.start(wait = true)
}
