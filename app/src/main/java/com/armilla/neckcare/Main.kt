package com.armilla.neckcare

import com.armilla.neckcare.ui.stage.ArmillaStage
import com.armilla.neckcare.ui.theme.ArmillaTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            ArmillaTheme {
                ArmillaStage()
            }
        }
    }

