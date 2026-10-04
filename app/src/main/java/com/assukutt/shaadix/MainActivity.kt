package com.assukutt.shaadix

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.assukutt.shaadix.ui.ShaadiXApp
import com.assukutt.shaadix.ui.theme.ShaadiXTheme
import com.assukutt.shaadix.data.PaymentOrderResult
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import com.razorpay.ExternalWalletListener
import org.json.JSONObject
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), PaymentResultWithDataListener, ExternalWalletListener {
    private var paymentResultHandler: ((String, String, String) -> Unit)? = null

    fun setPaymentResultHandler(handler: (String, String, String) -> Unit) {
        paymentResultHandler = handler
    }

    fun openCheckout(order: PaymentOrderResult, customerName: String, email: String, phone: String) {
        val key = order.keyId
        val orderId = order.orderId
        if (key.isNullOrBlank() || orderId.isNullOrBlank() || order.amount <= 0L) {
            Toast.makeText(this, "Online checkout is not configured. Please choose Pay later.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val options = JSONObject().apply {
                put("key", key)
                put("amount", order.amount)
                put("currency", order.currency)
                put("name", "ShaadiX")
                put("description", "Event booking")
                put("order_id", orderId)
                put("prefill", JSONObject().apply {
                    put("name", customerName)
                    put("email", email)
                    put("contact", phone.filter(Char::isDigit).takeLast(10))
                })
                put("theme", JSONObject().put("color", "#5A35A8"))
            }
            Checkout().open(this, options)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open payment checkout. Please try again.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPaymentSuccess(paymentId: String?, paymentData: PaymentData?) {
        val data = paymentData?.data
        val orderId = data?.optString("razorpay_order_id").orEmpty()
        val signature = data?.optString("razorpay_signature").orEmpty()
        if (!paymentId.isNullOrBlank() && orderId.isNotBlank() && signature.isNotBlank()) {
            paymentResultHandler?.invoke(orderId, paymentId, signature)
        } else {
            Toast.makeText(this, "Payment completed, but verification details were missing.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
        Toast.makeText(this, description?.takeIf { it.isNotBlank() } ?: "Payment was not completed.", Toast.LENGTH_LONG).show()
    }

    override fun onExternalWalletSelected(walletName: String?, paymentData: PaymentData?) {
        Toast.makeText(this, "Selected ${walletName ?: "wallet"}.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings=(application as AppContext).container.preferences
            val darkTheme by settings.darkTheme.collectAsState(initial=false)
            val scope=rememberCoroutineScope()
            ShaadiXTheme(darkTheme) { Surface(Modifier.fillMaxSize()) { ShaadiXApp(darkTheme=darkTheme,onToggleTheme={enabled->scope.launch{settings.setDarkTheme(enabled)}},onOpenCheckout={order,name,email,phone->openCheckout(order,name,email,phone)}) } }
        }
    }
}

