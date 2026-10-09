package com.booklater.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Tiny persistence layer: saved books + settings in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("booklater", Context.MODE_PRIVATE)

    private val _saved = MutableStateFlow(readSaved())
    val saved: StateFlow<List<Book>> = _saved.asStateFlow()

    private val _domain = MutableStateFlow(prefs.getString("domain", "www.amazon.com") ?: "www.amazon.com")
    val domain: StateFlow<String> = _domain.asStateFlow()

    /** 0 = system, 1 = light, 2 = dark */
    private val _theme = MutableStateFlow(prefs.getInt("theme", 0))
    val theme: StateFlow<Int> = _theme.asStateFlow()

    fun toggle(book: Book) {
        val cur = _saved.value
        _saved.value = if (cur.any { it.asin == book.asin }) {
            cur.filter { it.asin != book.asin }
        } else {
            listOf(book) + cur
        }
        writeSaved()
    }

    fun clearSaved() {
        _saved.value = emptyList()
        writeSaved()
    }

    fun setDomain(d: String) {
        _domain.value = d
        prefs.edit().putString("domain", d).apply()
    }

    fun setTheme(t: Int) {
        _theme.value = t
        prefs.edit().putInt("theme", t).apply()
    }

    private fun readSaved(): List<Book> = try {
        val arr = JSONArray(prefs.getString("saved", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Book(
                asin = o.getString("asin"),
                title = o.optString("title"),
                author = o.optString("author"),
                imageUrl = o.optString("image"),
                rating = o.optString("rating"),
                ratingCount = o.optString("count")
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeSaved() {
        val arr = JSONArray()
        _saved.value.forEach { b ->
            arr.put(
                JSONObject()
                    .put("asin", b.asin)
                    .put("title", b.title)
                    .put("author", b.author)
                    .put("image", b.imageUrl)
                    .put("rating", b.rating)
                    .put("count", b.ratingCount)
            )
        }
        prefs.edit().putString("saved", arr.toString()).apply()
    }
}
