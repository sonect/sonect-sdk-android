# Sonect SDK for Android [PRELIMINARY]

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

Add to `build.gradle` of your app

```Gradle
dependencies {
	...
    implementation ('com.github.sonect:android-user-sdk:{latestVersion}@aar') {
        transitive = true
        exclude group: "idenfySdk"
        exclude group: "io.anyline"
    }
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
       currentActivityContext: Activity,
       amount: Int,
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

   override fun getPaymentMethodId(): String {
       return "**** 1249"
   }

   override fun getBalance(): Float? = 178f

   override fun getPaymentMethodIcon(): Int {
       return R.mipmap.ic_launcher
   }

}

```

In `startPayment` call SDK provides `PaymentPlugin.ResultListener` which should be called from partner’s app when transaction is finished.

Note that partner integration must also have server-server communication in order to provide info for transaction and validate transaction by provided reference ID.
If reference is not available, other details need to be provided. See [here](https://docs.google.com/document/d/1jDOqkFZrjj9v5gF5-U-Hss-XPMFPpmjpIAy4BSkTRfY/edit#heading=h.6s2dsca7es7b).

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

## Activity SDK implementation

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

As a developer you must provide 5 colors and 6 fonts to the app. You could use default colors provided by sonect OR use your own but still it's requried to provide that values in your Theme.

![Sonect colors](https://api.monosnap.com/file/download?id=300PYTgN9OUffCGbVDMJ0nNswZBI3l)

Here the list of required fields with values that could be taken AS IS

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
```

!Notice that by definig colors and font you must provide references but not plain values, e.g. #ffffff will fail as well as san-serif-medium.

## Proguard / R8
In case you're using proguard or R8 to obfuscate and optimize your code,
the following rules should be enough to maintain all expected functionality.
Please let us know if you find any issues.

```xml
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
```

This is needed to maintain json serialization after proguard.
