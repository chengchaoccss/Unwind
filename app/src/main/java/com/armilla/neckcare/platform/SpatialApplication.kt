package com.armilla.neckcare.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.armilla.neckcare.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        launch(::mainApp)
    }
}
