import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class Item(val id: Int, val sku: String, val price: Double, val tags: List<String>)

private var sink = 0L
private fun blackhole(o: Any?) { sink += o.hashCode().toLong() }

fun main(args: Array<String>) {
    val arm = if (args.isEmpty()) "string" else args[0]
    val items = (1..50).map { Item(it, "SKU-" + it, it * 1.5, listOf("a", "b")) }
    val json = Json { ignoreUnknownKeys = true }
    val ser = ListSerializer(Item.serializer())

    // The shape of the brief's list endpoint: encode a page of rows, decode a body back.
    repeat(2000) { blackhole(json.encodeToString(ser, items)) }
    val text = json.encodeToString(ser, items)
    repeat(2000) { blackhole(json.decodeFromString(ser, text)) }

    // The pollution arm: ONE call, anywhere in the process, that goes through the tree.
    if (arm == "element") blackhole(json.encodeToJsonElement(ser, items))

    println(arm + " " + sink)
}
