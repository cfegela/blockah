package com.blockah

import android.app.Application
import com.blockah.data.BlocklistRepository

class BlockahApp : Application() {

    lateinit var repository: BlocklistRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = BlocklistRepository(this)
    }

    companion object {
        lateinit var instance: BlockahApp
            private set
    }
}
