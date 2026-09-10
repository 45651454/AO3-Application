package com.example.ao3application

import android.app.Application
import com.example.ao3application.data.Ao3Client
import com.example.ao3application.data.LibraryDb
import com.example.ao3application.data.Repo

class App : Application() {
    val repo: Repo by lazy { Repo(Ao3Client(this), LibraryDb(this)) }
}
