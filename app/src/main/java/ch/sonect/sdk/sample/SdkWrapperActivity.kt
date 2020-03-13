package ch.sonect.sdk.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ch.sonect.sdk.ActivityResultHandlingFragment
import ch.sonect.sdk.EntryPointFragment
import ch.sonect.sdk.SonectSDK
import ch.sonect.sdk.paymentPlugins.PaymentConfig
import ch.sonect.sdk.paymentPlugins.PaymentPlugin
import ch.sonect.sdk.profile.screen.SdkActionsCallback

class SdkWrapperActivity : AppCompatActivity() {

    companion object {
        const val LM = "lm"
        const val UID = "uid"
        const val TSDK = "toksdk"
        const val SIGN = "signature"
        internal const val ENV = "enviroment"

        fun start(
            activity: Activity, lightMode: Boolean, userId: String,
            tokenSDK: String, signature: String,
            environment: SonectSDK.Config.Enviroment
        ) {
            val newActivity = Intent(activity, SdkWrapperActivity::class.java)
            newActivity.putExtra(LM, lightMode)
            newActivity.putExtra(UID, userId)
            newActivity.putExtra(TSDK, tokenSDK)
            newActivity.putExtra(SIGN, signature)
            newActivity.putExtra(ENV, environment)
            activity.startActivity(newActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wrapper)

        val builder: SonectSDK.Config.Builder = SonectSDK.Config.Builder()
        val configBuilder = builder
            .addPaymentPlugin(MyOverlayScreenPaymentPlugin())
            .addPaymentPlugin(MySilentPaymentPlugin())
            .enviroment(intent.getSerializableExtra(ENV) as SonectSDK.Config.Enviroment)
            .userCredentials(
                SonectSDK.Config.UserCredentials(
                    intent.getStringExtra(UID),
                    intent.getStringExtra(TSDK), intent.getStringExtra(SIGN)
                )
            )
            .sdkCallbacks(object : SdkActionsCallback {
                override fun onSdkLastFragmentClosed() {
                    finish()
                }
            })
        if (intent.getBooleanExtra(LM, false)) {
            configBuilder.setLightTheme()
        }
        val config = configBuilder.build()
        val sonectSDK = SonectSDK(
            this,
            config
        )

        supportFragmentManager.beginTransaction().replace(R.id.container, sonectSDK.getStartFragment())
            .addToBackStack(null).commit()
    }

    override fun onBackPressed() {
        var backIsHandled = false
        for (fragment in supportFragmentManager.fragments) {
            if ((fragment as? EntryPointFragment)?.handleBack() == true) {
                // Handle back by ourselfs, SDK won't handle it anymore
                backIsHandled = true
            }
        }
        if (!backIsHandled) {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        for (fragment in supportFragmentManager.fragments) {
            if (fragment is ActivityResultHandlingFragment) {
                fragment.onHostedActivityResult(requestCode, resultCode, data)
            } else {
                fragment?.onActivityResult(requestCode, resultCode, data)
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    class MyOverlayScreenPaymentPlugin : PaymentPlugin {

        private lateinit var _listener: PaymentPlugin.ResultListener


        override fun init(paymentConfig: PaymentConfig?) {
            // ignore for now
        }

        override fun startPayment(
            currentActivityContext: Activity,
            amount: Int,
            currency: String,
            immediateCapture: Boolean,
            listener: PaymentPlugin.ResultListener
        ) {
            _listener = listener
            val paymentIntent = Intent(currentActivityContext, CustomPaymentActivity::class.java)
            currentActivityContext.startActivityForResult(paymentIntent, CustomPaymentActivity.REQUEST_CODE)
        }

        override fun handleActivityResultForPayment(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
            if (requestCode == CustomPaymentActivity.REQUEST_CODE) {
                if (resultCode == Activity.RESULT_OK) {
                    _listener.onTransactionSuccess(data?.getStringExtra(CustomPaymentActivity.PID))
                } else {
                    _listener.onTransactionError("My fault, sorry")
                }
                return true
            }
            return false
        }

        override fun getPaymentMethod(): String {
            return "BALANCE"
        }

        override fun getPaymentMethodName(): String {
            return "Overlayed PM_TILE name"
        }

        override fun getPaymentMethodId(): String {
            return "**** 1249"
        }

        override fun getBalance(): Float? = 178f

        override fun getPaymentMethodIcon(): Int {
            return R.mipmap.ic_launcher
        }
    }

    class MySilentPaymentPlugin : PaymentPlugin {

        override fun init(paymentConfig: PaymentConfig?) {
            // ignore for now
        }

        override fun startPayment(
            currentActivityContext: Activity,
            amount: Int,
            currency: String,
            immediateCapture: Boolean,
            listener: PaymentPlugin.ResultListener
        ) {
            listener.onTransactionSuccess(Math.random().hashCode().toString())
        }

        override fun getPaymentMethod(): String {
            return "BALANCE"
        }

        override fun handleActivityResultForPayment(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
            // ignore, we don't overlay anything, no result should be handled
            return false
        }

        override fun getPaymentMethodName(): String {
            return "Silent PM_TILE name"
        }

        override fun getPaymentMethodId(): String {
            return "**** 1892"
        }

        override fun getPaymentMethodIcon(): Int {
            return R.mipmap.ic_launcher_beta
        }
    }
}