package com.booklater.app

import android.app.Application

class BookApp : Application() {
    val store: Store by lazy { Store(this) }
}
