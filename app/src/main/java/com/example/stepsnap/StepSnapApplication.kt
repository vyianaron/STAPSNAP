package com.example.stepsnap

import android.app.Application
import com.example.stepsnap.data.db.StepSnapDatabase
import com.example.stepsnap.data.repository.StepSnapRepository

class StepSnapApplication : Application() {
    val repository: StepSnapRepository by lazy {
        StepSnapRepository(StepSnapDatabase.getInstance(this))
    }
}
