package io.github.x45651454.ao3reader

import android.app.Application
import io.github.x45651454.ao3reader.data.Ao3Client
import io.github.x45651454.ao3reader.data.LibraryDb
import io.github.x45651454.ao3reader.data.Repo

class App : Application() {
    val repo: Repo by lazy { Repo(Ao3Client(this), LibraryDb(this)) }
}
