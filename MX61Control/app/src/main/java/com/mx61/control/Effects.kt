package com.mx61.control

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Um parâmetro de efeito (Data List do MX61, "Effect Parameter List"). Valor de 14 bits: MSB*128+LSB. */
class Prm(j: JSONObject) {
    val no = j.getInt("no")
    val name = j.getString("name")
    val min = j.getInt("min")
    val max = j.getInt("max")
    val dryWet = j.has("dw")
    private val d = j.getJSONObject("d")
    private val tab = d.optInt("t", -1)
    private val kind = d.optString("k", "")
    private val lo = d.optDouble("lo", 0.0)
    private val hi = d.optDouble("hi", 0.0)
    private val unit = d.optString("u", "")
    private val dec = d.optInt("d", 0)
    private val labL = d.optString("l", "")
    private val labR = d.optString("r", "")
    private val enum: List<String> = d.optJSONArray("v")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()

    /** Endereço baixo do parâmetro dentro do bloco (Parâmetro 1 = 0x04, 2 bytes cada). */
    val offset get() = 4 + 2 * (no - 1)

    fun fmt(v: Int): String {
        if (tab >= 0) return Effects.tables[tab]?.getOrNull(v) ?: "$v"
        return when (kind) {
            "bal" -> if (v < 64) "$labL${64 - v}>$labR" else if (v == 64) "$labL=$labR" else "$labL<$labR${v - 64}"
            "enum" -> enum.getOrNull(v - min) ?: "$v"
            "lin" -> {
                val x = if (max == min) lo else lo + (v - min) * (hi - lo) / (max - min)
                val s = String.format(Locale.US, "%.${dec}f", x)
                (if (lo < 0 && x > 0) "+" else "") + s + (if (unit.isNotEmpty()) " $unit" else "")
            }
            else -> "$v"
        }
    }
}

class Fx(j: JSONObject) {
    val name = j.getString("name")
    val msb = j.getInt("msb")
    val lsb = j.getInt("lsb")
    val rev = j.getBoolean("rev")
    val cho = j.getBoolean("cho")
    val ins = j.getBoolean("ins")
    val key get() = msb * 128 + lsb
    val params: Map<String, List<Prm>> = j.getJSONObject("params").let { o ->
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).map { Prm(a.getJSONObject(it)) } } }
    }
}

object Effects {
    var list: List<Fx> = emptyList()
    var tables: Map<Int, List<String>> = emptyMap()

    fun load(ctx: Context) {
        if (list.isNotEmpty()) return
        val j = JSONObject(ctx.assets.open("effects.json").bufferedReader().use { it.readText() })
        val t = j.getJSONObject("tables")
        tables = t.keys().asSequence().associate { k ->
            val a: JSONArray = t.getJSONArray(k); k.toInt() to (0 until a.length()).map { a.getString(it) }
        }
        val e = j.getJSONArray("effects")
        list = (0 until e.length()).map { Fx(e.getJSONObject(it)) }
    }

    fun find(key: Int, block: String): Fx? = list.firstOrNull { it.key == key && when (block) { "rev" -> it.rev; "cho" -> it.cho; else -> it.ins } }
    fun forBlock(block: String) = list.filter { when (block) { "rev" -> it.rev; "cho" -> it.cho; else -> it.ins } }
}
