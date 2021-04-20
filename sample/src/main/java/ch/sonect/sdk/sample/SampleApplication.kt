package ch.sonect.sdk.sample

import ch.sonect.common.outer.SonectApplication
import ch.sonect.sdk.SonectSDK

class SampleApplication : SonectApplication() {

    override fun onCreate() {
        super.onCreate()
        SonectSDK.init(this)
    }

}