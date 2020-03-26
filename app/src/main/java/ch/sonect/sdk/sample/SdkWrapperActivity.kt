package ch.sonect.sdk.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import ch.sonect.sdk.ActivityResultHandlingFragment
import ch.sonect.sdk.EntryPointFragment
import ch.sonect.sdk.SonectSDK
import ch.sonect.sdk.paymentPlugins.PaymentConfig
import ch.sonect.sdk.paymentPlugins.PaymentPlugin
import ch.sonect.sdk.profile.screen.SdkActionsCallback
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SdkWrapperActivity : AppCompatActivity() {

    companion object {
        const val HMK = "hmk"
        const val LM = "lm"
        const val UID = "uid"
        const val TSDK = "toksdk"
        const val SIGN = "signature"
        const val CID = "clientId"
        internal const val ENV = "enviroment"

        fun start(
            activity: Activity, lightMode: Boolean, userId: String,
            tokenSDK: String, signature: String,
            environment: SonectSDK.Config.Enviroment,
            clientId: String,
            hmackKey: String
        ) {
            val newActivity = Intent(activity, SdkWrapperActivity::class.java)
            newActivity.putExtra(LM, lightMode)
            newActivity.putExtra(UID, userId)
            newActivity.putExtra(TSDK, tokenSDK)
            newActivity.putExtra(SIGN, signature)
            newActivity.putExtra(ENV, environment)
            newActivity.putExtra(CID, clientId)
            newActivity.putExtra(HMK, hmackKey)
            activity.startActivity(newActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wrapper)

        val signature_start =
            intent.getStringExtra(CID) + ":" + packageName + ":" + intent.getStringExtra(UID)

        val builder: SonectSDK.Config.Builder = SonectSDK.Config.Builder()
        val configBuilder = builder
            .addPaymentPlugin(
                MyOverlayScreenPaymentPlugin(
                    signature_start,
                    intent.getStringExtra(HMK)
                )
            )
            .addPaymentPlugin(
                MySilentPaymentPlugin(
                    signature_start,
                    intent.getStringExtra(HMK)
                )
            )
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

        supportFragmentManager.beginTransaction()
            .replace(R.id.container, sonectSDK.getStartFragment())
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

    class MyOverlayScreenPaymentPlugin(
        val signatureStart: String,
        val hmk: String
    ) : PaymentPlugin {

        private lateinit var _listener: PaymentPlugin.ResultListener

        lateinit var signatureEnd: String

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

            signatureEnd = ":$amount:$currency:CAPTURED"
            _listener = listener
            val paymentIntent = Intent(currentActivityContext, CustomPaymentActivity::class.java)
            currentActivityContext.startActivityForResult(
                paymentIntent,
                CustomPaymentActivity.REQUEST_CODE
            )
        }

        override fun handleActivityResultForPayment(
            requestCode: Int,
            resultCode: Int,
            data: Intent?
        ): Boolean {
            if (requestCode == CustomPaymentActivity.REQUEST_CODE) {
                if (resultCode == Activity.RESULT_OK) {
                    val sign =
                        signatureStart + ":" + data?.getStringExtra(CustomPaymentActivity.PID) + signatureEnd

                    _listener.onTransactionSuccess(
                        Math.abs(data?.getStringExtra(CustomPaymentActivity.PID)!!.toInt())
                            .toString(),
                        calculateSignature(sign)
                    )
                } else {
                    _listener.onTransactionError("My fault, sorry")
                }
                return true
            }
            return false
        }

        //TODO those are for simulating signature
        private fun calculateSignature(uid: String): String {
            return Base64.encodeToString(createHmac(uid.toByteArray()), Base64.DEFAULT)
                .trim()
        }

        fun createHmac(data: ByteArray): ByteArray {
            val keySpec = SecretKeySpec(
                hmk.toByteArray(),
                "HmacSHA256"
            )
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(keySpec)

            val hmac = mac.doFinal(data)
            return hmac
        }

        override fun getPaymentMethod(): String {
            return "DIRECT_DEBIT"
        }

        override fun getPaymentMethodName(): String {
            return "Overlayed PM_TILE name"
        }

        override fun getPaymentMethodId(): String {
            return ""
        }

        override fun getBalance(): Float? = 178f

        override fun getPaymentMethodIcon(): Int {
            return R.mipmap.ic_launcher
        }
    }

    class MySilentPaymentPlugin(
        val signatureStart: String,
        val hmk: String
    ) : PaymentPlugin {

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
            val signatureEnd = ":$amount:$currency:CAPTURED"
            val ref = Math.random().toString()
            val sign = "$signatureStart:$ref$signatureEnd"

            listener.onTransactionSuccess(ref, calculateSignature(sign))
        }

        //TODO those are for simulating signature
        private fun calculateSignature(uid: String): String {
            return Base64.encodeToString(createHmac(uid.toByteArray()), Base64.DEFAULT)
                .trim()
        }

        fun createHmac(data: ByteArray): ByteArray {
            val keySpec = SecretKeySpec(
                hmk.toByteArray(),
                "HmacSHA256"
            )
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(keySpec)

            val hmac = mac.doFinal(data)
            return hmac
        }

        override fun getPaymentMethod(): String {
            return "DIRECT_DEBIT"
        }

        override fun handleActivityResultForPayment(
            requestCode: Int,
            resultCode: Int,
            data: Intent?
        ): Boolean {
            // ignore, we don't overlay anything, no result should be handled
            return false
        }

        override fun getPaymentMethodName(): String {
            return "Silent PM_TILE name"
        }

        override fun getPaymentMethodId(): String {
            return ""
        }

        override fun getPaymentMethodIcon(): Int {
            return R.mipmap.ic_launcher_beta
        }
    }
}