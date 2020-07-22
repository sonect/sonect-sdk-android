package ch.sonect.sdk.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_payment.*

class CustomPaymentActivity : AppCompatActivity() {

    companion object {
        const val PID = "pidkey"
        val REQUEST_CODE = 1569
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_payment)

        btnPaymentWithOptionA.setOnClickListener {
            val data = Intent()
            data.putExtra(PID, Math.random().hashCode().toString())
            setResult(Activity.RESULT_OK, data)
            finish()
        }

        btnPaymentWithOptionB.setOnClickListener {
            val data = Intent()
            data.putExtra(PID, Math.random().hashCode().toString())
            setResult(Activity.RESULT_OK, data)
            finish()
        }

        btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
}