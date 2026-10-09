package com.booklater.app

data class Book(
    val asin: String,
    val title: String,
    val author: String = "",
    val imageUrl: String = "",
    val rating: String = "",
    val ratingCount: String = ""
)

data class Review(
    val author: String,
    val title: String,
    val stars: String,
    val body: String,
    val date: String = ""
)

data class Fact(val label: String, val value: String)
data class StarShare(val stars: Int, val percent: Int)
data class TextSection(val title: String, val body: String)
data class BookGroup(val title: String, val books: List<Book>)

data class BookDetail(
    val book: Book,
    val description: String,
    val facts: List<Fact>,
    val sections: List<TextSection>,
    val customersSay: String,
    val histogram: List<StarShare>,
    val reviews: List<Review>,
    val groups: List<BookGroup>
)

sealed interface Load<out T> {
    object Idle : Load<Nothing>
    object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Fail(val message: String, val captcha: Boolean = false) : Load<Nothing>
}

val AMAZON_STORES = listOf(
    "United States" to "www.amazon.com",
    "United Kingdom" to "www.amazon.co.uk",
    "Canada" to "www.amazon.ca",
    "Germany" to "www.amazon.de",
    "France" to "www.amazon.fr",
    "Italy" to "www.amazon.it",
    "Spain" to "www.amazon.es",
    "India" to "www.amazon.in",
    "Australia" to "www.amazon.com.au",
    "Japan" to "www.amazon.co.jp"
)
