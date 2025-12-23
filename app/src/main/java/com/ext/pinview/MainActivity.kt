package com.ext.pinview

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ext.custom_pin_view.PinView

/**
 * MainActivity - Demonstrates 3 different PinView use cases
 *
 * Example 1: 4-digit masked PIN (Box style) - App Lock
 * Example 2: 6-digit OTP (Underline style) - Verification
 * Example 3: 5-digit Custom PIN (Circle style) - Banking
 */
class MainActivity : AppCompatActivity() {

    private lateinit var pinViewAppLock: PinView
    private lateinit var pinViewOtp: PinView
    private lateinit var pinViewBank: PinView

    private lateinit var btnVerifyAppLock: Button
    private lateinit var btnVerifyOtp: Button
    private lateinit var btnVerifyBank: Button

    private lateinit var btnClearAppLock: Button
    private lateinit var btnClearOtp: Button
    private lateinit var btnClearBank: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupExample1_AppLock()
        setupExample2_OtpVerification()
        setupExample3_BankPin()
    }

    private fun initializeViews() {
        // Example 1 - App Lock
        pinViewAppLock = findViewById(R.id.pinViewAppLock)
        btnVerifyAppLock = findViewById(R.id.btnVerifyAppLock)
        btnClearAppLock = findViewById(R.id.btnClearAppLock)

        // Example 2 - OTP
        pinViewOtp = findViewById(R.id.pinViewOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        btnClearOtp = findViewById(R.id.btnClearOtp)

        // Example 3 - Bank PIN
        pinViewBank = findViewById(R.id.pinViewBank)
        btnVerifyBank = findViewById(R.id.btnVerifyBank)
        btnClearBank = findViewById(R.id.btnClearBank)
    }

    // ===== EXAMPLE 1: APP LOCK (4-digit masked PIN, Box style) =====
    private fun setupExample1_AppLock() {
        val correctPin = "1234"

        // Listen for PIN completion
        pinViewAppLock.setOnPinEnteredListener { enteredPin ->
            verifyPin(enteredPin, correctPin, pinViewAppLock, "App Lock")
        }

        // Manual verify button
        btnVerifyAppLock.setOnClickListener {
            val enteredPin = pinViewAppLock.getText()
            if (enteredPin.length == 4) {
                verifyPin(enteredPin, correctPin, pinViewAppLock, "App Lock")
            } else {
                showToast("Please enter 4 digits")
            }
        }

        // Clear button
        btnClearAppLock.setOnClickListener {
            pinViewAppLock.clear()
            showToast("App Lock PIN cleared")
        }

        // Listen to text changes
        pinViewAppLock.setOnTextChangedListener { text ->
            btnVerifyAppLock.isEnabled = text.length == 4
        }
    }

    // ===== EXAMPLE 2: OTP VERIFICATION (6-digit, Underline style) =====
    private fun setupExample2_OtpVerification() {
        val correctOtp = "123456"

        // Auto-submit when complete
        pinViewOtp.setOnPinEnteredListener { enteredOtp ->
            // Simulate API call delay
            pinViewOtp.postDelayed({
                verifyPin(enteredOtp, correctOtp, pinViewOtp, "OTP")
            }, 300)
        }

        // Manual verify button
        btnVerifyOtp.setOnClickListener {
            val enteredOtp = pinViewOtp.getText()
            if (enteredOtp.length == 6) {
                verifyPin(enteredOtp, correctOtp, pinViewOtp, "OTP")
            } else {
                showToast("Please enter 6 digits")
            }
        }

        // Clear button
        btnClearOtp.setOnClickListener {
            pinViewOtp.clear()
            showToast("OTP cleared")
        }

        // Real-time feedback
        pinViewOtp.setOnTextChangedListener { text ->
            btnVerifyOtp.isEnabled = text.length == 6
        }
    }

    // ===== EXAMPLE 3: BANK PIN (5-digit, Circle style, Custom colors) =====
    private fun setupExample3_BankPin() {
        val correctBankPin = "98765"

        // Listen for completion
        pinViewBank.setOnPinEnteredListener { enteredPin ->
            verifyPin(enteredPin, correctBankPin, pinViewBank, "Bank PIN")
        }

        // Manual verify button
        btnVerifyBank.setOnClickListener {
            val enteredPin = pinViewBank.getText()
            if (enteredPin.length == 5) {
                verifyPin(enteredPin, correctBankPin, pinViewBank, "Bank PIN")
            } else {
                showToast("Please enter 5 digits")
            }
        }

        // Clear button
        btnClearBank.setOnClickListener {
            pinViewBank.clear()
            showToast("Bank PIN cleared")
        }

        // Text change listener
        pinViewBank.setOnTextChangedListener { text ->
            btnVerifyBank.isEnabled = text.length == 5
        }
    }

    // ===== HELPER FUNCTIONS =====

    private fun verifyPin(entered: String, correct: String, pinView: PinView, label: String) {
        if (entered == correct) {
            showToast("✓ $label verified successfully!")
            pinView.clear()
        } else {
            showToast("✗ Incorrect $label")
            pinView.showError()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}