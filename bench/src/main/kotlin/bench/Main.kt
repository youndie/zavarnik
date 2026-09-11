package bench

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.jakarta.Jetty
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The benchmark service of the optimizer research: three kinds of endpoint the brief names.
 *
 * - `/echo` — the engine and nothing else;
 * - `/items` — JSON CRUD over an in-memory store, kotlinx.serialization in and out;
 * - `/business` — what a service actually does per request: validation with a regex, collection
 *   chains over the order lines, pricing rules, debug logging that is switched off.
 *
 * Every construct the brief calls a candidate is here on purpose, in the shape a service would
 * really have it: the regex built inside the handler, `logger.debug` with a template, `suspend`
 * functions returning `Int`, a `value class` used generically, chains over lists.
 */
fun main() {
    val port = System.getProperty("bench.port")?.toInt() ?: 18100
    val store = ItemStore()
    val pricing = Pricing()
    // The engine is the only thing that differs between the variants of the engine phase
    // (docs/research/research-engines.md): same jars, same process, same routes, one -D.
    when (val engine = System.getProperty("bench.engine") ?: "cio") {
        "cio" -> embeddedServer(CIO, port = port) { bench(store, pricing) }.start(wait = true)
        "netty" -> embeddedServer(Netty, port = port) { bench(store, pricing) }.start(wait = true)
        "jetty" -> embeddedServer(Jetty, port = port) { bench(store, pricing) }.start(wait = true)
        else -> error("unknown bench.engine: $engine (cio, netty, jetty)")
    }
}

fun Application.bench(
    store: ItemStore,
    pricing: Pricing,
) {
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respondText("ok") }
        get("/echo") { call.respondText(call.request.queryParameters["msg"] ?: "") }
        post("/echo") { call.respondText(call.receiveText()) }

        // The one endpoint that is not about the engine: it blocks the thread it runs on, the way a
        // JDBC driver does, and measures what a dispatcher's parallelism is *for*.
        get("/blocking") {
            Thread.sleep(5)
            call.respondText("ok")
        }

        post("/items") { call.respond(HttpStatusCode.Created, store.create(call.receive<NewItem>())) }
        get("/items") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
            call.respond(store.list(limit))
        }
        get("/items/{id}") {
            val item = store.get(call.parameters["id"]!!.toLong())
            if (item == null) call.respond(HttpStatusCode.NotFound) else call.respond(item)
        }
        put("/items/{id}") {
            val updated = store.update(call.parameters["id"]!!.toLong(), call.receive<NewItem>())
            if (updated == null) call.respond(HttpStatusCode.NotFound) else call.respond(updated)
        }
        delete("/items/{id}") {
            call.respond(
                if (store.delete(
                        call.parameters["id"]!!.toLong(),
                    )
                ) {
                    HttpStatusCode.NoContent
                } else {
                    HttpStatusCode.NotFound
                },
            )
        }

        post("/business") {
            val order = call.receive<Order>()
            val result = pricing.quote(order)
            if (result.errors.isEmpty()) {
                call.respond(
                    result,
                )
            } else {
                call.respond(HttpStatusCode.UnprocessableEntity, result)
            }
        }
    }
}

@Serializable
data class NewItem(
    val sku: String,
    val name: String,
    val price: Double,
    val tags: List<String> = emptyList(),
)

@Serializable
data class Item(
    val id: Long,
    val sku: String,
    val name: String,
    val price: Double,
    val tags: List<String>,
)

class ItemStore {
    private val ids = AtomicLong()
    private val items = ConcurrentHashMap<Long, Item>()

    fun create(new: NewItem): Item {
        val item = Item(ids.incrementAndGet(), new.sku, new.name, new.price, new.tags)
        items[item.id] = item
        return item
    }

    fun get(id: Long): Item? = items[id]

    fun list(limit: Int): List<Item> = items.values.sortedByDescending { it.id }.take(limit)

    fun update(
        id: Long,
        new: NewItem,
    ): Item? =
        items.computeIfPresent(
            id,
        ) { _, old -> old.copy(sku = new.sku, name = new.name, price = new.price, tags = new.tags) }

    fun delete(id: Long): Boolean = items.remove(id) != null
}
