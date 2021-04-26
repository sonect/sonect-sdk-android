package ch.sonect.sdk.sample

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import ch.sonect.sdk.SDKEntryPointActivity
import ch.sonect.sdk.SonectSDK
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


class MainActivity : AppCompatActivity() {

    // Id shuold be some value unique and constant for single user
    var userId = ""
    var clientId = ""
    var clientSecret = ""
    var hmackKey = ""

    private val themesMapping = mapOf(
        "BackgroundColor1" to R.style.HighlightTheme_BackgroundColor1,
        "BackgroundColor2" to R.style.HighlightTheme_BackgroundColor2,
        "BackgroundColor3" to R.style.HighlightTheme_BackgroundColor3,
        "BackgroundColor4" to R.style.HighlightTheme_BackgroundColor4,
        "BackgroundColor5" to R.style.HighlightTheme_BackgroundColor5,
        "BackgroundColor6" to R.style.HighlightTheme_BackgroundColor6,

        "BorderColor1" to R.style.HighlightTheme_BorderColor1,
        "BorderColor2" to R.style.HighlightTheme_BorderColor2,

        "TitleColor" to R.style.HighlightTheme_TitleColor,
        "TitleColorActive" to R.style.HighlightTheme_TitleColorActive,

        "DescriptionColor" to R.style.HighlightTheme_DescriptionColor,

        "AlertTextColor1" to R.style.HighlightTheme_AlertTextColor1,
        "AlertTextColor2" to R.style.HighlightTheme_AlertTextColor2,
        "AlertTextColor3" to R.style.HighlightTheme_AlertTextColor3,

        "StatusTextColor1" to R.style.HighlightTheme_StatusTextColor1,
        "StatusTextColor2" to R.style.HighlightTheme_StatusTextColor2,
        "StatusTextColor3" to R.style.HighlightTheme_StatusTextColor3,
        "StatusTextColor4" to R.style.HighlightTheme_StatusTextColor4,

        "IconColor1" to R.style.HighlightTheme_IconColor1,
        "IconColor2" to R.style.HighlightTheme_IconColor2,
        "IconColor3" to R.style.HighlightTheme_IconColor3,

        "PrimaryColor1" to R.style.HighlightTheme_PrimaryColor1,
        "PrimaryColor2" to R.style.HighlightTheme_PrimaryColor2,
        "PrimaryColor3" to R.style.HighlightTheme_PrimaryColor3,
        "PrimaryColor4" to R.style.HighlightTheme_PrimaryColor4,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        btnStartSdkFragment.setOnClickListener {
            userId = etUserId.text.toString()
            clientId = etClientId.text.toString()
            clientSecret = etClientSecret.text.toString()
            hmackKey = etHmackKey.text.toString()

            val limits = linkedMapOf(
                "daily" to dailyLimitET.text.toString().toIntOrNull(),
                "weekly" to weeklyLimitET.text.toString().toIntOrNull(),
                "monthly" to monthlyLimitET.text.toString().toIntOrNull(),
                "yearly" to yearlyLimitET.text.toString().toIntOrNull(),
                "transaction" to transactionLimitET.text.toString().toIntOrNull(),
                "dailyMax" to dailyLimitMaxET.text.toString().toIntOrNull(),
                "weeklyMax" to weeklyLimitMaxET.text.toString().toIntOrNull(),
                "monthlyMax" to monthlyLimitMaxET.text.toString().toIntOrNull(),
                "yearlyMax" to yearlyLimitMaxET.text.toString().toIntOrNull(),
                "transactionMax" to transactionLimitMaxET.text.toString().toIntOrNull()
            )

            val userType = when {
                customerRB.isChecked -> SonectSDK.Config.UserConfig.Type.CUSTOMER
                employeeRB.isChecked -> SonectSDK.Config.UserConfig.Type.EMPLOYEE
                else ->  null
            }

            val theme = if (chkHighlight.isChecked) {
                themesMapping[dropDownView.selectedItem as? String] ?: -1
            } else {
                -1
            }

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
                hmackKey,
                userType = userType,
                isTrial = trialCB.isChecked,
                limits = Gson().toJson(limits),
                customTheme = theme
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

        chkHighlight.setOnCheckedChangeListener { _, isChecked ->
            dropDownView.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            themesMapping.keys.toTypedArray()
        )
        dropDownView.adapter = adapter
    }

    private fun getTokenSDK(): String {
        return Base64.encodeToString("${clientId}:${clientSecret}".toByteArray(), Base64.DEFAULT)
            .replace("\n", "")
    }

    fun getDefaultClientId(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "40bd1c70-7988-11ea-831a-9be9ab365269"
            SonectSDK.Config.Enviroment.STAGING -> "cceff710-79a3-11ea-92ad-652ad420aac6"
            SonectSDK.Config.Enviroment.PRODUCTION -> ""
        }
    }

    fun getDefaultClientSecret(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "fae024ad2a9d4d024f517ef98910721b3a4af9c6ff98cc57ae9c3fa21c3171c6"
            SonectSDK.Config.Enviroment.STAGING -> "447617077c073f8495c196ddcbbd92bd547e90249f172f9432cd18eb2ebe6a71"
            SonectSDK.Config.Enviroment.PRODUCTION -> ""
        }
    }

    fun getDefaultUserId(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "5ed90b13f952051a08a65e73"
            SonectSDK.Config.Enviroment.STAGING -> "4100801"
            SonectSDK.Config.Enviroment.PRODUCTION -> ""
        }
    }

    fun getDefaulltHmackKey(): String {
        return when (getSelectedEnviroment()) {
            SonectSDK.Config.Enviroment.DEV -> "ca1f3441b76fabdd539da659f90c31134bb4f5e41b9c41772b093aa8b3d71a20"
            SonectSDK.Config.Enviroment.STAGING -> "a2469d2222d54c5cc51930220882e12eaea3c015206e3abf774a13799371b81d"
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