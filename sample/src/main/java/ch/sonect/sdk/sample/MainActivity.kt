package ch.sonect.sdk.sample

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import ch.sonect.sdk.SDKEntryPointActivity
import ch.sonect.sdk.SonectSDK
import kotlinx.android.synthetic.main.activity_main.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    // Id shuold be some value unique and constant for single user
    var userId = ""
    var clientId = ""
    var clientSecret = ""
    var hmackKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        btnStartSdkFragment.setOnClickListener {
            userId = etUserId.text.toString()
            clientId = etClientId.text.toString()
            clientSecret = etClientSecret.text.toString()
            hmackKey = etHmackKey.text.toString()
            SdkWrapperActivity.start(
                this,
                chkLight.isChecked,
                userId,
                getTokenSDK(),
                calculateSignature(userId),
                getSelectedEnviroment(),
                chkSilentPm.isChecked || chkBothPm.isChecked,
                chkOverlayPm.isChecked || chkBothPm.isChecked,
                clientId,
                hmackKey
            )
        }

        userId = getDefaultUserId()
        clientId = getDefaultClientId()
        clientSecret = getDefaultClientSecret()
        hmackKey = getDefaulltHmackKey()
        etUserId.setText(userId)
        etClientId.setText(clientId)
        etClientSecret.setText(clientSecret)
        etHmackKey.setText(hmackKey)

        groupEnviroment.setOnCheckedChangeListener { group, checkedId ->
            userId = getDefaultUserId()
            clientId = getDefaultClientId()
            clientSecret = getDefaultClientSecret()
            hmackKey = getDefaulltHmackKey()
            etUserId.setText(userId)
            etClientId.setText(clientId)
            etClientSecret.setText(clientSecret)
            etHmackKey.setText(hmackKey)
        }

        btnStartSdkActivity.setOnClickListener {
            // Each integration should have it's own sdk token
            userId = etUserId.text.toString()
            val signature = calculateSignature(userId)
            SDKEntryPointActivity.start(
                this, SonectSDK.Config.UserCredentials(userId, getTokenSDK(), signature),
                arrayListOf(
                    SDKEntryPointActivity.PaymentMethodReference(
                        "BALANCE", R.mipmap.ic_launcher, "**** 1234", 302.44f
                    )
                ), isLightMode = chkLight.isChecked, environment = getSelectedEnviroment()
            )
        }

        btnResumeSdkWithReceipt.setOnClickListener {
            // Each integration should have it's own sdk token
            val tokenSDK =
                Base64.encodeToString("${clientId}:${clientSecret}".toByteArray(), Base64.DEFAULT)
                    .replace("\n", "")
            userId = etUserId.text.toString()
            val signature = calculateSignature(userId)
            SDKEntryPointActivity.startWithdraw(
                this, SonectSDK.Config.UserCredentials(userId, tokenSDK, signature),
                SDKEntryPointActivity.NewTransactionReference(
                    "BALANCE", "**** 1234", "30", "CHF"
                ),
                arrayListOf(
                    SDKEntryPointActivity.PaymentMethodReference(
                        "BALANCE", R.mipmap.ic_launcher, "**** 1234", 302.44f
                    )
                ), isLightMode = chkLight.isChecked
            )
        }
    }

    private fun getTokenSDK(): String {
        return Base64.encodeToString("${clientId}:${clientSecret}".toByteArray(), Base64.DEFAULT)
            .replace("\n", "")
    }

    fun getDefaultClientId(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "5c323120-5027-11e8-ad3f-7be7c251fc61"
            SonectSDK.Config.Enviroment.STAGING -> "8467e820-93fa-11e9-bdb7-3f7b70c4b6fe"
            SonectSDK.Config.Enviroment.PRODUCTION -> ""
        }
    }

    fun getDefaultClientSecret(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "b64407b409abbc4269771cbd1f7c28dbd498270defff3a606f5f4f2d27a4e07a"
            SonectSDK.Config.Enviroment.STAGING -> "8e049130b6533747ddd8bd3613c49aee51de14c925cfcdc49e0e64c0bda2dba6"
            SonectSDK.Config.Enviroment.PRODUCTION -> ""
        }
    }

    fun getDefaultUserId(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "5db00a3ff58170006eb331c4"
            SonectSDK.Config.Enviroment.STAGING -> "5d52879f41961500109b76f6"
            SonectSDK.Config.Enviroment.PRODUCTION -> ""
        }
    }

    fun getDefaulltHmackKey(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "0a4f1c697751b6a3fbf533eeb81752426928acfe202bdd256a76d1a205907d70"
            SonectSDK.Config.Enviroment.STAGING -> "1e2536aa1a371e517bef5d46afdfd6b28b79e9a674c5023280382616032d0b98"
            SonectSDK.Config.Enviroment.PRODUCTION -> ""
        }
    }

    private fun getSelectedEnviroment(): SonectSDK.Config.Enviroment {
        if (chkDev.isChecked) return SonectSDK.Config.Enviroment.DEV
        if (chkTest.isChecked) return SonectSDK.Config.Enviroment.STAGING
        if (chkProd.isChecked) return SonectSDK.Config.Enviroment.PRODUCTION
        throw IllegalStateException("Environment have not been selected yet")
    }

    private fun calculateSignature(uid: String): String {
        val hmacString = "${clientId}:$packageName:$uid"
        return Base64.encodeToString(createHmac(hmacString.toByteArray()), Base64.DEFAULT).trim()
    }

    fun createHmac(data: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(hmackKey.toByteArray(), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)

        val hmac = mac.doFinal(data)
        return hmac
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SDKEntryPointActivity.REQUEST_CODE) {
            if (resultCode == SDKEntryPointActivity.RESULT_WITHDRAW) {
                Log.e(
                    "!@#",
                    "User wants to withdraw ${data?.getIntExtra(
                        SDKEntryPointActivity.AMOUNT_TO_WITHDRAW,
                        -1
                    )} ${data?.getStringExtra(SDKEntryPointActivity.CURRENCY_TO_WITHDRAW)}"
                )
                btnResumeSdkWithReceipt.isEnabled = true
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}