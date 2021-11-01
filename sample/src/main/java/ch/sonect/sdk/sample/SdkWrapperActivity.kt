package ch.sonect.sdk.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import ch.sonect.sdk.ActivityResultHandlingFragment
import ch.sonect.sdk.EntryPointFragment
import ch.sonect.sdk.SonectSDK
import ch.sonect.sdk.contract.BrandingManager
import ch.sonect.sdk.paymentPlugins.PaymentConfig
import ch.sonect.sdk.paymentPlugins.PaymentPlugin
import ch.sonect.sdk.profile.screen.SdkActionsCallback
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SdkWrapperActivity : AppCompatActivity() {

    companion object {
        const val HMK = "hmk"
        const val LM = "lm"
        const val CUSTOM_THEME = "CUSTOM_THEME"
        const val UID = "uid"
        const val TSDK = "toksdk"
        const val SIGN = "signature"
        const val CID = "clientId"
        const val SPM = "silentPM"
        const val OPM = "overlaidPM"
        const val UT = "type"
        const val TRIAL = "trial"
        const val FIELDS = "fields"
        const val LT = "limits"
        internal const val ENV = "enviroment"
        const val FEES = "fees"

        fun start(
            activity: Activity,
            lightMode: Boolean,
            userId: String,
            tokenSDK: String,
            signature: String,
            environment: SonectSDK.Config.Enviroment,
            includeSilentPaymentPlugin: Boolean,
            includeOverlaidPaymentPlugin: Boolean,
            clientId: String,
            hmackKey: String,
            userType: SonectSDK.Config.UserConfig.Type? = null,
            isTrial: Boolean = false,
            signatureFields: LinkedHashMap<String, Any?> = linkedMapOf(),
            limits: String? = null,
            @StyleRes customTheme: Int = -1,
            fees: String
        ) {
            val newActivity = Intent(activity, SdkWrapperActivity::class.java)
            newActivity.putExtra(LM, lightMode)
            newActivity.putExtra(UID, userId)
            newActivity.putExtra(TSDK, tokenSDK)
            newActivity.putExtra(SIGN, signature)
            newActivity.putExtra(ENV, environment)
            newActivity.putExtra(CID, clientId)
            newActivity.putExtra(HMK, hmackKey)
            newActivity.putExtra(SPM, includeSilentPaymentPlugin)
            newActivity.putExtra(OPM, includeOverlaidPaymentPlugin)
            newActivity.putExtra(UT, userType)
            newActivity.putExtra(TRIAL, isTrial)
            newActivity.putExtra(FIELDS, signatureFields)
            newActivity.putExtra(LT, limits)
            newActivity.putExtra(CUSTOM_THEME, customTheme)
            if (!fees.isBlank()) newActivity.putExtra(FEES, fees)
            activity.startActivity(newActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val customTheme = intent.getIntExtra(CUSTOM_THEME, -1)
        if (customTheme != -1) {
            setTheme(customTheme)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wrapper)

        val signature_start =
            intent.getStringExtra(CID) + ":" + packageName + ":" + intent.getStringExtra(UID)

        val builder: SonectSDK.Config.Builder = SonectSDK.Config.Builder()

        val str = intent.getStringExtra(LT)
        val gson = Gson()
        val entityType: Type =
            object : TypeToken<LinkedHashMap<String, Int?>>() {}.type
        val linkedHashMap: LinkedHashMap<String, Int?> = gson.fromJson(str, entityType)

        val userConfig = SonectSDK.Config.UserConfig(
            dailyLimit = linkedHashMap["daily"],
            weeklyLimit = linkedHashMap["weekly"],
            monthlyLimit = linkedHashMap["monthly"],
            yearlyLimit = linkedHashMap["yearly"],
            transactionLimit = linkedHashMap["transaction"],
            dailyLimitMax = linkedHashMap["dailyMax"],
            weeklyLimitMax = linkedHashMap["weeklyMax"],
            monthlyLimitMax = linkedHashMap["monthlyMax"],
            yearlyLimitMax = linkedHashMap["yearlyMax"],
            transactionLimitMax = linkedHashMap["transactionMax"],
            type = intent.getSerializableExtra(UT) as? SonectSDK.Config.UserConfig.Type,
            isTrial = intent.getBooleanExtra(TRIAL, false),
            fees = intent.getStringExtra(FEES)?.toFloat() ?: 1.5f
        )

        val configBuilder = builder
            .enviroment(intent.getSerializableExtra(ENV) as SonectSDK.Config.Enviroment)
            .userCredentials(
                SonectSDK.Config.UserCredentials(
                    intent.getStringExtra(UID) ?: "",
                    intent.getStringExtra(TSDK) ?: "",
                    intent.getStringExtra(SIGN) ?: ""
                )
            )
            .userConfiguration(userConfig)
            .sdkCallbacks(object : SdkActionsCallback {
                override fun onSdkLastFragmentClosed() {
                    finish()
                }
            })
            .brandingManager(object : BrandingManager {
                override fun sdkName(): String? = "The Awesome Sample"
            })

        if (intent.getBooleanExtra(SPM, false)) {
            builder.addPaymentPlugin(
                MySilentPaymentPlugin(
                    signature_start,
                    intent.getStringExtra(HMK) ?: ""
                )
            )
        }

        if (intent.getBooleanExtra(OPM, false)) {
            builder.addPaymentPlugin(
                MyOverlayScreenPaymentPlugin(
                    signature_start,
                    intent.getStringExtra(HMK) ?: ""
                )
            )
        }

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

    inner class MyOverlayScreenPaymentPlugin(
        val signatureStart: String,
        val hmk: String
    ) : PaymentPlugin {

        private lateinit var _listener: PaymentPlugin.ResultListener

        lateinit var signatureEnd: String
        lateinit var date: String

        override fun init(paymentConfig: PaymentConfig?) {
            // ignore for now
        }

        override fun startPayment(
            amount: Int,
            fees: Float,
            currency: String,
            immediateCapture: Boolean,
            listener: PaymentPlugin.ResultListener
        ) {
            date = System.currentTimeMillis().toString()
            signatureEnd = ":$amount:$currency:$date"
            _listener = listener
            val paymentIntent = Intent(this@SdkWrapperActivity, CustomPaymentActivity::class.java)
            this@SdkWrapperActivity.startActivityForResult(
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
                        calculateSignature(sign), date
                    )
                } else {
                    _listener.onTransactionError("My fault, sorry")
                }
                return true
            }
            return false
        }

        // TODO those are for simulating signature
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
            amount: Int,
            fees: Float,
            currency: String,
            immediateCapture: Boolean,
            listener: PaymentPlugin.ResultListener
        ) {
            val date = System.currentTimeMillis().toString()
            val signatureEnd = ":$amount:$currency:$date"
            val ref = Math.random().toString()
            val sign = "$signatureStart:$ref$signatureEnd"

            listener.onTransactionSuccess(ref, calculateSignature(sign), date)
        }

        // TODO those are for simulating signature
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

        override fun getPaymentMethodIcon(): Int {
            return R.mipmap.ic_launcher_beta
        }

        override fun getAccountDescription(): String? {
            return "The awesome payment method!"
        }

        override fun getTextColor(): Int {
            return R.color.sonectWhite
        }

        override fun getBackgroundGradient(): Pair<Int, Int> {
            return Pair(R.color.color5, R.color.color3)
        }

        override fun getCurrency(): String? {
            return "ZKF"
        }

        override fun getAccountNumber(): String? {
            return "1234 5643"
        }

        override fun getBalance(): Float? {
            return 25f
        }
    }
}