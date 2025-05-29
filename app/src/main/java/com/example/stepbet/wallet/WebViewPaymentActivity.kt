// WebViewPaymentActivity.kt - CLEAN VERSION
package com.example.stepbet.wallet

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stepbet.databinding.ActivityWebviewPaymentBinding

class WebViewPaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebviewPaymentBinding

    companion object {
        private const val TAG = "WebViewPayment"
        // STEP 1: Replace with your actual payment button ID from Razorpay dashboard
        // You can also make this dynamic or use a test button ID
        private const val PAYMENT_BUTTON_ID = "pl_QaRnsypsapqCW2" // Replace with YOUR actual ID

        // STEP 2: Alternative - Use test mode button ID (if you have one)
        // private const val PAYMENT_BUTTON_ID = "pl_test_xxxxxxxxx" // Your test button ID
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        val amount = intent.getIntExtra("amount", 100)
        val userPhone = intent.getStringExtra("userPhone") ?: ""
        val userEmail = intent.getStringExtra("userEmail") ?: ""

        setupWebView(amount, userPhone, userEmail)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Payment"
        }
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(amount: Int, phone: String, email: String) {
        binding.webview.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.allowContentAccess = true
            settings.allowFileAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = true

            // Add JavaScript interface for communication
            addJavascriptInterface(PaymentInterface(), "PaymentHandler")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.progressBar.visibility = android.view.View.GONE
                    android.util.Log.d(TAG, "Page loaded successfully: $url")

                    // Check if we're on a success page
                    url?.let { checkForPaymentCompletion(it) }

                    // Inject JavaScript to handle payment events
                    injectPaymentHandlers()
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    android.util.Log.e(TAG, "WebView error: ${error?.description}")
                    Toast.makeText(this@WebViewPaymentActivity, "Error loading payment page", Toast.LENGTH_SHORT).show()
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: ""
                    android.util.Log.d(TAG, "URL loading: $url")

                    // Handle Razorpay redirects
                    when {
                        url.contains("razorpay.com") && (url.contains("success") || url.contains("payment_id")) -> {
                            android.util.Log.d(TAG, "Payment success detected in URL: $url")
                            extractPaymentIdAndFinish(url, true)
                            return true
                        }
                        url.contains("razorpay.com") && (url.contains("failed") || url.contains("error")) -> {
                            android.util.Log.d(TAG, "Payment failure detected in URL: $url")
                            extractPaymentIdAndFinish(url, false)
                            return true
                        }
                        url.contains("razorpay.com") -> {
                            // Allow Razorpay pages to load but monitor them
                            android.util.Log.d(TAG, "Loading Razorpay page: $url")
                            return false
                        }
                        // Handle other payment completion indicators
                        url.contains("payment_success") || url.contains("success") -> {
                            extractPaymentIdAndFinish(url, true)
                            return true
                        }
                        url.contains("payment_failed") || url.contains("payment_error") -> {
                            extractPaymentIdAndFinish(url, false)
                            return true
                        }
                    }

                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    android.util.Log.d(TAG, "Page started loading: $url")

                    // Check URL as soon as it starts loading
                    url?.let { checkForPaymentCompletion(it) }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    android.util.Log.d(TAG, "Console: ${consoleMessage?.message()}")
                    return true
                }

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    android.util.Log.d(TAG, "JS Alert: $message")
                    return super.onJsAlert(view, url, message, result)
                }
            }

            // Load your HTML payment form with the actual payment button
            loadDataWithBaseURL("https://checkout.razorpay.com", generatePaymentHTML(amount, phone, email), "text/html", "UTF-8", null)
        }
    }

    private fun generatePaymentHTML(amount: Int, phone: String, email: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>StepBet Payment</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
                    margin: 0;
                    padding: 20px;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                
                .payment-container {
                    background: white;
                    padding: 40px;
                    border-radius: 20px;
                    box-shadow: 0 20px 40px rgba(0,0,0,0.1);
                    text-align: center;
                    max-width: 400px;
                    width: 100%;
                }
                
                .logo {
                    font-size: 28px;
                    font-weight: bold;
                    color: #673AB7;
                    margin-bottom: 10px;
                }
                
                .amount {
                    font-size: 36px;
                    font-weight: bold;
                    color: #2c3e50;
                    margin: 20px 0;
                }
                
                .description {
                    color: #7f8c8d;
                    margin-bottom: 30px;
                    font-size: 16px;
                }
                
                .user-info {
                    background: #f8f9fa;
                    padding: 15px;
                    border-radius: 10px;
                    margin-bottom: 30px;
                    font-size: 14px;
                    color: #6c757d;
                }
                
                .payment-button-container {
                    margin: 20px 0;
                }
                
                .loading {
                    display: none;
                    margin: 20px 0;
                    color: #673AB7;
                }
                
                .error-message {
                    display: none;
                    background: #ffe6e6;
                    color: #d63031;
                    padding: 15px;
                    border-radius: 10px;
                    margin: 20px 0;
                    border: 1px solid #fab1a0;
                }
                
                .success-message {
                    display: none;
                    background: #e6ffe6;
                    color: #00b894;
                    padding: 15px;
                    border-radius: 10px;
                    margin: 20px 0;
                    border: 1px solid #55a3ff;
                }
                
                /* Style the Razorpay button */
                .razorpay-payment-button {
                    background: #673AB7 !important;
                    border: none !important;
                    padding: 15px 30px !important;
                    border-radius: 10px !important;
                    font-size: 16px !important;
                    font-weight: bold !important;
                    width: 100% !important;
                    max-width: 300px !important;
                    transition: all 0.3s ease !important;
                }
                
                .razorpay-payment-button:hover {
                    background: #5e35b1 !important;
                    transform: translateY(-2px) !important;
                }
            </style>
        </head>
        <body>
            <div class="payment-container">
                <div class="logo">StepBet</div>
                <div class="amount">₹${amount}</div>
                <div class="description">Add money to your wallet</div>
                
                <div class="user-info">
                    <div><strong>Phone:</strong> ${phone}</div>
                    ${if (email.isNotEmpty()) "<div><strong>Email:</strong> $email</div>" else ""}
                </div>
                
                <div class="loading" id="loading">
                    <div>Processing payment...</div>
                </div>
                
                <div class="error-message" id="errorMessage">
                    Payment failed. Please try again.
                </div>
                
                <div class="success-message" id="successMessage">
                    Payment successful! Redirecting...
                </div>
                
                <div class="payment-button-container" id="paymentButtonContainer">
                    <!-- Your actual Razorpay payment button -->
                    <form>
                        <script 
                            src="https://checkout.razorpay.com/v1/payment-button.js" 
                            data-payment_button_id="$PAYMENT_BUTTON_ID" 
                            async
                            onerror="handleScriptError()"
                            onload="handleScriptLoad()">
                        </script>
                    </form>
                    
                    <!-- Fallback button if Razorpay fails -->
                    <button id="fallbackButton" style="display: none;" onclick="showFallbackMessage()">
                        Pay ₹${amount} (Fallback)
                    </button>
                </div>
            </div>

            <script>
                console.log('Payment page loaded');
                console.log('Using Payment Button ID: $PAYMENT_BUTTON_ID');
                
                // Handle script loading
                function handleScriptLoad() {
                    console.log('Razorpay script loaded successfully');
                }
                
                function handleScriptError() {
                    console.error('Failed to load Razorpay script');
                    document.getElementById('errorMessage').innerText = 'Failed to load payment system. Please check your internet connection.';
                    document.getElementById('errorMessage').style.display = 'block';
                    document.getElementById('fallbackButton').style.display = 'block';
                }
                
                function showFallbackMessage() {
                    if (typeof PaymentHandler !== 'undefined') {
                        PaymentHandler.onPaymentFailed('Payment button failed to load. Please try native payment method.');
                    }
                }
                
                // Global payment handlers
                window.onPaymentSuccess = function(response) {
                    console.log('Payment Success:', response);
                    document.getElementById('loading').style.display = 'none';
                    document.getElementById('successMessage').style.display = 'block';
                    document.getElementById('paymentButtonContainer').style.display = 'none';
                    
                    setTimeout(function() {
                        if (typeof PaymentHandler !== 'undefined') {
                            PaymentHandler.onPaymentSuccess(response.razorpay_payment_id || 'success');
                        }
                    }, 1500);
                };
                
                window.onPaymentError = function(response) {
                    console.log('Payment Error:', response);
                    document.getElementById('loading').style.display = 'none';
                    document.getElementById('errorMessage').style.display = 'block';
                    
                    // Show more specific error message
                    const errorMsg = response.error ? response.error.description : 'Payment failed';
                    document.getElementById('errorMessage').innerText = errorMsg;
                    
                    setTimeout(function() {
                        document.getElementById('errorMessage').style.display = 'none';
                    }, 5000);
                    
                    if (typeof PaymentHandler !== 'undefined') {
                        PaymentHandler.onPaymentFailed(errorMsg);
                    }
                };
                
                // Listen for Razorpay events
                document.addEventListener('DOMContentLoaded', function() {
                    console.log('DOM loaded');
                    
                    // Check if Razorpay button is loaded
                    setTimeout(function() {
                        const razorpayButton = document.querySelector('.razorpay-payment-button');
                        if (razorpayButton) {
                            console.log('Razorpay button found and loaded successfully');
                            
                            // Add click handler to show loading
                            razorpayButton.addEventListener('click', function() {
                                console.log('Payment button clicked');
                                document.getElementById('loading').style.display = 'block';
                            });
                        } else {
                            console.error('Razorpay button not found - check Payment Button ID');
                            document.getElementById('errorMessage').innerText = 'Payment button not found. Please check configuration.';
                            document.getElementById('errorMessage').style.display = 'block';
                            document.getElementById('fallbackButton').style.display = 'block';
                        }
                    }, 3000); // Increased timeout to allow more time for loading
                });
                
                // Enhanced error detection
                window.addEventListener('error', function(e) {
                    console.error('JavaScript Error:', e.message, e.filename, e.lineno);
                    if (e.message.includes('razorpay') || e.filename.includes('razorpay')) {
                        document.getElementById('errorMessage').innerText = 'Payment system error. Please try again.';
                        document.getElementById('errorMessage').style.display = 'block';
                    }
                });
                
                // Check if payment button loaded after 5 seconds
                setTimeout(function() {
                    const razorpayButton = document.querySelector('.razorpay-payment-button');
                    if (!razorpayButton) {
                        console.error('Payment button failed to load within 5 seconds');
                        document.getElementById('errorMessage').innerText = 'Payment button failed to load. Invalid Payment Button ID or network issue.';
                        document.getElementById('errorMessage').style.display = 'block';
                        document.getElementById('fallbackButton').style.display = 'block';
                    }
                }, 5000);
            </script>
        </body>
        </html>
        """
    }

    private fun injectPaymentHandlers() {
        val javascript = """
            // Override Razorpay handlers if they exist
            if (typeof Razorpay !== 'undefined') {
                console.log('Razorpay SDK detected');
            }
            
            // Global success handler
            window.addEventListener('message', function(event) {
                console.log('Message received:', event.data);
                
                if (event.data && event.data.type === 'payment_success') {
                    PaymentHandler.onPaymentSuccess(event.data.payment_id);
                } else if (event.data && event.data.type === 'payment_failed') {
                    PaymentHandler.onPaymentFailed(event.data.error);
                }
            });
        """

        binding.webview.evaluateJavascript(javascript) { result ->
            android.util.Log.d(TAG, "JavaScript injection result: $result")
        }
    }

    private fun checkForPaymentCompletion(url: String) {
        android.util.Log.d(TAG, "Checking URL for payment completion: $url")

        when {
            // Check for various success indicators
            url.contains("payment_id") && url.contains("success") -> {
                android.util.Log.d(TAG, "Payment success detected via URL parameters")
                extractPaymentIdAndFinish(url, true)
            }
            url.contains("razorpay_payment_id") -> {
                android.util.Log.d(TAG, "Razorpay payment ID found in URL")
                extractPaymentIdAndFinish(url, true)
            }
            url.contains("payment_status=success") || url.contains("status=success") -> {
                android.util.Log.d(TAG, "Payment status success detected")
                extractPaymentIdAndFinish(url, true)
            }
            url.contains("payment_status=failed") || url.contains("status=failed") -> {
                android.util.Log.d(TAG, "Payment status failed detected")
                extractPaymentIdAndFinish(url, false)
            }
            // Check for Razorpay success page patterns
            url.contains("checkout.razorpay.com") && url.contains("success") -> {
                android.util.Log.d(TAG, "Razorpay success page detected")
                extractPaymentIdAndFinish(url, true)
            }
        }
    }

    private fun extractPaymentIdAndFinish(url: String, isSuccess: Boolean) {
        try {
            android.util.Log.d(TAG, "Extracting payment info from URL: $url")

            val uri = android.net.Uri.parse(url)
            val paymentId = uri.getQueryParameter("payment_id")
                ?: uri.getQueryParameter("razorpay_payment_id")
                ?: uri.getQueryParameter("paymentId")
                ?: "payment_${System.currentTimeMillis()}"

            android.util.Log.d(TAG, "Extracted payment ID: $paymentId, Success: $isSuccess")

            if (isSuccess) {
                val paymentInterface = PaymentInterface()
                paymentInterface.onPaymentSuccess(paymentId)
            } else {
                val error = uri.getQueryParameter("error")
                    ?: uri.getQueryParameter("error_description")
                    ?: "Payment failed"
                val paymentInterface = PaymentInterface()
                paymentInterface.onPaymentFailed(error)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error extracting payment info: ${e.message}")
            val paymentInterface = PaymentInterface()
            paymentInterface.onPaymentFailed("Payment processing error: ${e.message}")
        }
    }

    // JavaScript Interface for communication between WebView and Android
    inner class PaymentInterface {
        @JavascriptInterface
        fun onPaymentSuccess(paymentId: String) {
            android.util.Log.d(TAG, "Payment Success: $paymentId")
            runOnUiThread {
                Toast.makeText(this@WebViewPaymentActivity, "Payment Successful!", Toast.LENGTH_LONG).show()

                // Pass result back to calling activity
                val resultIntent = android.content.Intent().apply {
                    putExtra("payment_id", paymentId)
                    putExtra("status", "success")
                    putExtra("amount", intent.getIntExtra("amount", 0))
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }

        @JavascriptInterface
        fun onPaymentFailed(error: String) {
            android.util.Log.e(TAG, "Payment Failed: $error")
            runOnUiThread {
                Toast.makeText(this@WebViewPaymentActivity, "Payment Failed: $error", Toast.LENGTH_LONG).show()

                val resultIntent = android.content.Intent().apply {
                    putExtra("status", "failed")
                    putExtra("error", error)
                }
                setResult(Activity.RESULT_CANCELED, resultIntent)
            }
        }

        @JavascriptInterface
        fun onPaymentCancelled() {
            android.util.Log.d(TAG, "Payment Cancelled")
            runOnUiThread {
                Toast.makeText(this@WebViewPaymentActivity, "Payment Cancelled", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    override fun onBackPressed() {
        if (binding.webview.canGoBack()) {
            binding.webview.goBack()
        } else {
            setResult(Activity.RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webview.destroy()
        super.onDestroy()
    }
}