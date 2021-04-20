# Sonect SDK for Android

In this document we will go through the necessary steps to integrate
Sonect SDK in your Android app. 

Contact support@sonect.ch if additional info is needed.


## Installation: 

### Add jitpack repo as a repository

e.g. in project build file

```Gradle
allprojects {
    repositories {
    	...
        maven { url 'https://jitpack.io' }
    }
}
```

### Add dependency to the SDK

Latest version of SDK: [![](https://jitpack.io/v/sonect/android-user-sdk.svg)](https://jitpack.io/#sonect/android-user-sdk)

Add `SDK` to `build.gradle` of your app. SDK could work with both `okhttp3` major versions 3 and 4. They have slightly incompatible changes so you **must** define which one you want to use.

You should

- Exclude one of `okhttp3` or `okhttp4` from dependencies
- Make sure that other lib is connected - both okhttp + loggingInterceptor

Sample of using `okhttp3` major version 3.

```Gradle
dependencies {
	...
    implementation ('com.github.sonect:android-user-sdk:{latestVersion}') {
        exclude module: "okhttp4"
    }
    
    // Okhttp must be provided as a separate dependency
    externalImplementation "com.squareup.okhttp3:okhttp:3.14.9"
    externalImplementation "com.squareup.okhttp3:logging-interceptor:3.14.9"
    ...
}
```

In order to avoid DEX limit app should enable multidex support

```Gradle
android {
   ...
   defaultConfig {
      ...
       multiDexEnabled true
   }
   ...
```

In order to simplify integration process and migrate to androidX you could define 2 lines in `gradle.properties`

```Gradle
android.enableJetifier=true
android.useAndroidX=true
```

## SDK Integration 

### Initialization (from v3.3)

- You must call `init` from your application class.
- You must implement `AppStorage` interface with your `Application `. In order to make it simple we've already provide `SonectApplication` that could be used as a parent if it suitable for you or you could implement it by yourself.

Sample implemnetation of the Application class based on `SonectApplication`:

```kotlin
open class SonectApplication : Application(), AppStorage {

    private val storage = mutableMapOf<Class<*>, Any>()

    override fun <T : Any> set(clazz: Class<T>, value: T) {
        storage[clazz] = value
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(clazz: Class<T>) = storage[clazz] as? T

}
```

### Fragment SDK integration

Main advantage of Fragment implementation is that your main app and SDK could live inside one single activity context and you don’t need to handle e.g. pin lock screen timeout issues when user jump back and forth between SDK and your app.

Note in case of Fragment implementation your app should be androidx.* compatible since we expose Fragment from androidx package.

### Configure SDK object

Whole SDK object should be configured via Builder

```kotlin
// Each integration should have it's own sdk token
val tokenSDK =
"NTU0ZmQ3MTAtYTdkMS0xMWU5LWEwMTgtMjMzYjc5Zjk2ZWFkOjQyNmI1ZGU1M2E5YzE5ZjgyMDk5NWNjOGY2NjZkMmIzOGViYjRmOTU2OWM4ZDU5ZGY4NzgxYmUzOGM3MzFjYjk="
// Signature must be created on server using partner's secret key
val signature = "SkJ+nMaTaopSeNtyJXi964FeYUpkNSABlBLlPa7hplQ="
// Id should be some value unique and constant for single user
val userId = "013ty8lhoy813t9bsdf"

val builder: SonectSDK.Config.Builder = SonectSDK.Config.Builder()
val sonectSDK = SonectSDK(
   this,
   builder
       .addPaymentPlugin(MyOverlayScreenPaymentPlugin())
       .userCredentials(SonectSDK.Config.UserCredentials(userId, tokenSDK, signature))
       .userConfig(
           SonectSDK.Config.UserConfig(
               SonectSDK.Config.UserConfig.Type.EMPLOYEE,
               100,
               200,
               10
           )
       )
       .sdkCallbacks(object : SdkActionsCallback {
           override fun onSdkLastFragmentClosed() {
               finish()
           }
       })
       .build()
)
```

#### User credentials

SonectSDK receives several parameters for user credentials:

| Param    | Type | Description |
|-----------|--------|---------------------------------------------------------------------------------------------------------------------|
| userId    | String | Any possible userID which is unique and persistent for each user. Could be any type of String partner app provides. |
| tokenSDK  | String | SDK provided by Sonect to determine partner app                                                                     |
| signature | String | Signature that was created on your server                                                                           |

#### Payment plugins

Payment plugin is required for a partner who wants to handle the payment process on its own. Object must implement ch.sonect.sdk.paymentPlugins.PaymentPlugin interface.

```kotlin
class MyOverlayScreenPaymentPlugin : PaymentPlugin {

    private lateinit var _listener: PaymentPlugin.ResultListener

    override fun init(paymentConfig: PaymentConfig?) {
      // ignore for now
    }

    override fun startPayment(
      amount: Int,
      immediateCapture: Boolean,
      listener: PaymentPlugin.ResultListener
    ) {
      _listener = listener
      val paymentIntent = Intent(this@MyActivity, CustomPaymentActivity::class.java)
      this@MyActivity.startActivityForResult(paymentIntent, CustomPaymentActivity.REQUEST_CODE)
    }

    override fun handleActivityResultForPayment(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
      if (requestCode == CustomPaymentActivity.REQUEST_CODE) {
        if (resultCode == Activity.RESULT_OK) {
          _listener.onTransactionSuccess(data?.getStringExtra(CustomPaymentActivity.PID), “CALCULATED_SIGNATURE”, "TRANSACTION_DATE")
        } else {
          _listener.onTransactionError("My fault, sorry")
        }
        return true
      }
      return false
    }

    override fun getPaymentMethodName(): String {
      return "Overlayed PM_TILE name"
    }

    override fun getPaymentMethodIcon(): Int {
      return R.mipmap.ic_launcher
    }
  
    override fun getAccountDescription(): String? {
      return "The awesome payment method!"
    }

    override fun getTextColor(): Int {
      return R.color.sonectRedViolet
    }

    override fun getBackgroundGradient(): Pair<Int, Int> {
      return Pair(R.color.sonectSoftBlue, R.color.sonectOrangeYellow)
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

```

![Sonect colors](https://api.monosnap.com/file/download?id=kXOe8FE6OuL9OzWFNFCpvZKXWIuUMk)

If you use Java, other methods that is not defined above should return empty values (null or empty).
In Kotlin they have default implementation.

In `startPayment` call SDK provides `PaymentPlugin.ResultListener` which should be called from partner’s app when transaction is finished.

Note that partner integration must also have server-server communication in order to provide info for transaction and validate transaction by provided reference ID.
If reference is not available, other details need to be provided. See [here](https://docs.google.com/document/d/1jDOqkFZrjj9v5gF5-U-Hss-XPMFPpmjpIAy4BSkTRfY/edit#heading=h.6s2dsca7es7b).

#### User config

| Param            | Type            | Description              |
| ---------------- | --------------- | ------------------------ |
| type             | UserConfig.Type | User's type              |
| dailyLimit       | Int             | User's daily limit       |
| monthlyLimit     | Int             | User's monthly limit     |
| transactionLimit | Int             | User's transaction limit |

**UserConfig.Type**

| Value                    | Description |
| ------------------------ | ----------- |
| UserConfig.Type.EMPLOYEE |             |
| UserConfig.Type.CUSTOMER |             |

#### SDK callbacks

SDK provides several callbacks. Some of them won’t be fired in case of deep partner app integration. The one which will be fired anyways and should be implemented is `onSdkLastFragmentClosed`. You need to provide an action that should be taken when the last fragment from SDK was closed, e.g. finishing wrapping activity.

```kotlin
.sdkCallbacks(object : SdkActionsCallback {
           override fun onSdkLastFragmentClosed() {
               finish()
           }
       })
```

### Launch SDK

Main entry point for SDK is ch.sonect.sdk.EntryPointFragment
When SDK object was configured EntryPointFragment could be added to the fragment stack

```kotlin
supportFragmentManager.beginTransaction().replace(R.id.container, sonectSDK.getStartFragment()).addToBackStack(null).commit()
```

### Provide activity callbacks to SDK

Whole sdk is built on fragments and in order to handle navigation stack properly BackPress event should be provided by wrapping Activity

```kotlin
override fun onBackPressed() {
   var backIsHandled = false
   for (fragment in supportFragmentManager.fragments) {
       if ((fragment as? EntryPointFragment)?.handleBack() == true) {
           // Handle back by ourselves, SDK won't handle it anymore
           backIsHandled = true
       }
   }
   if (!backIsHandled) {
       super.onBackPressed()
   }
}
```

If your payment processing flow includes redirection to other activity and result should be passed to your payment plugin you must route onActivityResult callback to SDK

```kotlin
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
```

`onHostedActivityResult` in `ActivityResultHandlingFragment` will route info to proper fragment in SDK stack. 

## Activity SDK implementation [Deprecated]

Sonect SDK could be started completely by inner SDK activity. Main advantage is that you don’t need to think about navigation flow. Main disadvantage is that in case you want to handle payments in the outer app you need to completely close SDK activity, handle onActivityResult and open SDK then on receipt screen. During switching between activities it’s possible that system could even kill outer activity and then recreate it which could lead to potential state loss (depends on outer app implementation).

### Start main SDK screen

```kotlin
btnStartSdkActivity.setOnClickListener {
   // Each integration should have it's own sdk token
   val tokenSDK =
       "NTU0ZmQ3MTAtYTdkMS0xMWU5LWEwMTgtMjMzYjc5Zjk2ZWFkOjQyNmI1ZGU1M2E5YzE5ZjgyMDk5NWNjOGY2NjZkMmIzOGViYjRmOTU2OWM4ZDU5ZGY4NzgxYmUzOGM3MzFjYjk="
   // Signature must be created on server using partner's secret key
   val signature = "SkJ+nMaTaopSeNtyJXi964FeYUpkNSABlBLlPa7hplQ="
   // Id shuold be some value unique and constant for single user
   val userId = "0041781472583"
   SDKEntryPointActivity.start(
       this, SonectSDK.Config.UserCredentials(userId, tokenSDK, signature),
       arrayListOf(
           SDKEntryPointActivity.PaymentMethodReference(
               "My payment method", R.mipmap.ic_launcher, "**** 1234", 302.44f
           )
       )
   )
}
```

### Handle withdrawal request

SDK Activity could be finished with SDKEntryPointActivity.RESULT_WITHDRAW which means that users initiate the withdrawal process.

```kotlin
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
```

#### Fields available during withdrawal request from user

| Value   | Type    | Description                                         |
|--------------------------------------------|--------|------------------------------------------------------------|
| SDKEntryPointActivity.AMOUNT_TO_WITHDRAW   | Int    | Amount to withdraw                                         |
| SDKEntryPointActivity.CURRENCY_TO_WITHDRAW | String | Currency to withdraw                                       |
| SDKEntryPointActivity.PAYMENT_METHOD       | String | Selected payment method that was passed with configuration |

### Open receipt screen after transaction complete

Sdk could be opened on receipt screen

```kotlin
btnResumeSdkWithReceipt.setOnClickListener {
   // Each integration should have it's own sdk token
   val tokenSDK =
       "NTU0ZmQ3MTAtYTdkMS0xMWU5LWEwMTgtMjMzYjc5Zjk2ZWFkOjQyNmI1ZGU1M2E5YzE5ZjgyMDk5NWNjOGY2NjZkMmIzOGViYjRmOTU2OWM4ZDU5ZGY4NzgxYmUzOGM3MzFjYjk="
   // Signature must be created on server using partner's secret key
   val signature = "SkJ+nMaTaopSeNtyJXi964FeYUpkNSABlBLlPa7hplQ="
   // Id should be some value unique and constant for single user
   val userId = "0041781472583"
   SDKEntryPointActivity.startWithdraw(
       this, SonectSDK.Config.UserCredentials(userId, tokenSDK, signature),
       SDKEntryPointActivity.NewTransactionReference(
           "My payment method", "**** 1234", "30", "CHF"
       ),
       arrayListOf(
           SDKEntryPointActivity.PaymentMethodReference(
               "My payment method", R.mipmap.ic_launcher, "**** 1234", 302.44f
           )
       )
   )
}
```

You must provide PaymentMethodReference to be able to return to dashboard and continue using SDK without closing and reopening it from outer app.

## Theming and styling

### Colors (Deprecated, prior v3.3)

You could provide 5 colors and 6 fonts to the app.

![Sonect colors](https://api.monosnap.com/file/download?id=300PYTgN9OUffCGbVDMJ0nNswZBI3l)

List of fields with default values

```xml
        <item name="sonectColor1">@color/softBlue</item>
        <item name="sonectColor2">@color/redViolet</item>
        <item name="sonectColor3">@color/flamingo</item>
        <item name="sonectColor4">@color/orangeYellow</item>
        <item name="sonectColor5">@color/greenyYellow</item>

        <item name="sonectFontBlack">@font/raleway_black</item>
        <item name="sonectFontBold">@font/raleway_bold</item>
        <item name="sonectFontRegular">@font/raleway_regular</item>
        <item name="sonectFontSemiBold">@font/raleway_semibold</item>
        <item name="sonectFontMedium">@font/raleway_medium</item>
        <item name="sonectFontLight">@font/raleway_light</item>

        <item name="sonectButtonTextColor">{sonect primary text color}</item>
```

`sonectButtonTextColor` is the text color which applies of buttons/chips with background.

!Notice that by definig colors and font you must provide references but not plain values, e.g. #ffffff will fail as well as san-serif-medium.

### Color (From v3.3)

```xml
        <item name="sonectBackgroundColor1">#000000</item>
        <item name="sonectBackgroundColor2">#202020</item>
        <item name="sonectBackgroundColor3">#393939</item>
        <item name="sonectBackgroundColor4">#000000</item>
        <item name="sonectBackgroundColor5">#000000</item>
        <item name="sonectBackgroundColor6">#000000</item>

        <item name="sonectBorderColor1">@android:color/transparent</item>
        <item name="sonectBorderColor2">#ffffff</item>

        <item name="sonectTitleColor">#ffffff</item>
        <item name="sonectTitleColorActive">#000000</item>

        <item name="sonectDescriptionColor">#9b9b9b</item>

        <item name="sonectAlertTextColor1">#ffffff</item>
        <item name="sonectAlertTextColor2">#ffffff</item>
        <item name="sonectAlertTextColor3">#ffffff</item>

        <item name="sonectStatusTextColor1">#ffffff</item>
        <item name="sonectStatusTextColor2">#ffffff</item>
        <item name="sonectStatusTextColor3">#ffffff</item>
        <item name="sonectStatusTextColor4">#ffffff</item>

        <item name="sonectIconColor1">#ffffff</item>
        <item name="sonectIconColor2">#ffffff</item>
        <item name="sonectIconColor3">#ffffff</item>

        <item name="sonectPrimaryColor1">#ff0069</item>
        <item name="sonectPrimaryColor2">#494949</item>
        <item name="sonectPrimaryColor3">#ffa300</item>
        <item name="sonectPrimaryColor4">#bfe500</item>
```



### SDK run time configuration

Some SDK values could be overriden by outer app.

#### SDK naming

In order to override name that is shown on dashboard instead of default 'Welcome to Sonect' you should pass branding manager into Config Builder.

```kotlin
builder.brandingManager(object : BrandingManager {
  override fun sdkName(): String? = "My Awesome Implementation"
})
```

### SDK bulid time configuration

Some SDK constants that control behaviour could be provided during the compile time. For that you could provide keys in resources*. 

*Check sample, there is a `configs.xml` file which contains all Sonect related keys.

#### Default barcode type

User could swith between `barcode` and `QRCode` inside receipt screen. You have ability to choose which one should be default.

```xml
<integer name="sonect_barcode_type_to_show">@integer/sonect_qrcode_type_enum</integer>
```

Default value is barcode.

#### Showing back button on root screens

By default SDK has no back button in toolbar for root screens (dashboard and same level screen with bottom navigation). You could define if you want to have this back button which will just close SDK by overriding resource config.

```xml
<bool name="sonect_should_show_back_on_root">true</bool>
```

#### Enabling possibility to verify user through Sonect

By default verification possibility is toggled of. If you want to let user start verification flow through Sonect process you should override resource config. 

```xml
<bool name="sonect_is_verification_flow_enabled">true</bool>
```

#### Show payment reference on receipt screen

Payment reference/operational ID could be shown on receipt. Developer could define on which type of receipt it should be shown by status.

```xml
<bool name="sonect_should_show_payment_reference_on_expired_receipt">false</bool>
<bool name="sonect_should_show_payment_reference_on_cancelled_receipt">false</bool>
<bool name="sonect_should_show_payment_reference_on_rejected_receipt">true</bool>
<bool name="sonect_should_show_payment_reference_on_confirmed_receipt">true</bool>
<bool name="sonect_should_show_payment_reference_on_failed_receipt">true</bool>
```

#### Not enough balance dialog

When user click on the `Confirm` button and has not enough balance we show dialog. Those 2 strings could be overriden.

```xml
// Title:secondary_not_enough_balance
// Message:not_enough_balance

<string name="secondary_not_enough_balance">Hey, where\'s Money?</string>
<string name="not_enough_balance">Seems like not enough $$$, need to do smth!</string>
```
