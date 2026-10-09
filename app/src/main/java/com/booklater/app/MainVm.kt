package com.booklater.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainVm(app: Application) : AndroidViewModel(app) {
    private val store = (app as BookApp).store

    val saved = store.saved
    val domain = store.domain
    val theme = store.theme

    var query by mutableStateOf("")

    private val _results = MutableStateFlow<Load<List<Book>>>(Load.Idle)
    val results: StateFlow<Load<List<Book>>> = _results.asStateFlow()

    private val _details = MutableStateFlow<Map<String, Load<BookDetail>>>(emptyMap())
    val details: StateFlow<Map<String, Load<BookDetail>>> = _details.asStateFlow()

    private val known = HashMap<String, Book>()

    fun book(asin: String): Book? = known[asin] ?: saved.value.firstOrNull { it.asin == asin }

    private fun failure(e: Throwable): Load.Fail {
        if (e is CancellationException) throw e
        return if (e is AmazonException) {
            Load.Fail(e.message ?: "Error", e.captcha)
        } else {
            Load.Fail("${e.javaClass.simpleName}: ${e.message ?: "unknown problem"}")
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        _results.value = Load.Loading
        val d = domain.value
        viewModelScope.launch {
            _results.value = try {
                val list = withContext(Dispatchers.IO) { Amazon.search(d, q) }
                list.forEach { known[it.asin] = it }
                Load.Ok(list)
            } catch (e: Throwable) {
                failure(e)
            }
        }
    }

    fun loadDetail(asin: String, force: Boolean = false) {
        val cur = _details.value[asin]
        if (!force && (cur is Load.Ok || cur is Load.Loading)) return
        setDetail(asin, Load.Loading)
        val d = domain.value
        viewModelScope.launch {
            val r: Load<BookDetail> = try {
                val det = withContext(Dispatchers.IO) { Amazon.detail(d, asin, known[asin]) }
                known[asin] = det.book
                det.groups.forEach { g -> g.books.forEach { known.putIfAbsent(it.asin, it) } }
                Load.Ok(det)
            } catch (e: Throwable) {
                failure(e)
            }
            setDetail(asin, r)
        }
    }

    private fun setDetail(asin: String, l: Load<BookDetail>) {
        _details.value = _details.value + (asin to l)
    }

    fun toggle(b: Book) = store.toggle(b)
    fun clearSaved() = store.clearSaved()
    fun setTheme(t: Int) = store.setTheme(t)

    fun setDomain(d: String) {
        store.setDomain(d)
        _results.value = Load.Idle
        _details.value = emptyMap()
    }
}
