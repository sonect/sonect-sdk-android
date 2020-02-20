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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        btnStartSdkFragment.setOnClickListener {
            userId = etUserId.text.toString()
            SdkWrapperActivity.start(
                this, chkLight.isChecked, userId, getTokenSDK(), calculateSignature(userId), getSelectedEnviroment()
            )
        }

        userId = getDefaultUserId()
        etUserId.setText(userId)

        groupEnviroment.setOnCheckedChangeListener { group, checkedId ->
            userId = getDefaultUserId()
            etUserId.setText(userId)
        }

        btnStartSdkActivity.setOnClickListener {
            // Each integration should have it's own sdk token
            userId = etUserId.text.toString()
            val signature = calculateSignature(userId)
            SDKEntryPointActivity.start(
                this, SonectSDK.Config.UserCredentials(userId, getTokenSDK(), signature,""),
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
                Base64.encodeToString("${getClientId()}:${getClientSecret()}".toByteArray(), Base64.DEFAULT)
                    .replace("\n", "")
            userId = etUserId.text.toString()
            val signature = calculateSignature(userId)
            SDKEntryPointActivity.startWithdraw(
                this, SonectSDK.Config.UserCredentials(userId, tokenSDK, signature, ""),
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
        return Base64.encodeToString("${getClientId()}:${getClientSecret()}".toByteArray(), Base64.DEFAULT)
            .replace("\n", "")
    }

    fun getClientId(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "554fd710-a7d1-11e9-a018-233b79f96ead"
            SonectSDK.Config.Enviroment.STAGING -> "08828a10-bdaf-11e9-be4c-5db5328cafa4"
            SonectSDK.Config.Enviroment.PRODUCTION -> ""
        }
    }

    fun getClientSecret(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "426b5de53a9c19f820995cc8f666d2b38ebb4f9569c8d59df8781be38c731cb9"
            SonectSDK.Config.Enviroment.STAGING -> "c999d5adab9b065b166bce6e58b84050349088ab8e7948248088068c7c534f60"
            SonectSDK.Config.Enviroment.PRODUCTION -> ""
        }
    }

    fun getDefaultUserId(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "etLiIhOADD3F7EUZgdDackmmZbRji5"
            SonectSDK.Config.Enviroment.STAGING -> "A1MrFAOjZ24YQJHexSrlC3yskOOuGS"
            SonectSDK.Config.Enviroment.PRODUCTION -> ""
        }
    }

    fun getHmackKey(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "fcadbb8602f6885bedd71bd9afdb1d8a4831e1f1bff24118581335906f8b3d48"
            SonectSDK.Config.Enviroment.STAGING -> ""
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
        val hmacString = "${getClientId()}:$packageName:$uid"
        return Base64.encodeToString(createHmac(hmacString.toByteArray()), Base64.DEFAULT).trim()
    }

    fun createHmac(data: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(getHmackKey().toByteArray(), "HmacSHA256")
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