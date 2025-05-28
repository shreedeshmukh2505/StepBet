package com.example.stepbet

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.stepbet.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if user is logged in
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            // User is not logged in, redirect to login screen
            startLoginActivity()
            return
        }

        try {
            // Set up Navigation Component with Bottom Navigation
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment

            if (navHostFragment != null) {
                val navController = navHostFragment.navController
                binding.bottomNav.setupWithNavController(navController)
            } else {
                // Fallback: Show a simple message if navigation fails
                android.util.Log.e("MainActivity", "NavHostFragment not found")
                showSimpleContent()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Navigation setup failed: ${e.message}")
            showSimpleContent()
        }
    }

    private fun showSimpleContent() {
        // Remove bottom navigation and show simple content
        binding.bottomNav.visibility = android.view.View.GONE

        // You can create a simple fragment or view here
        // For now, we'll just hide the nav host
        val navHost = findViewById<android.view.View>(R.id.nav_host_fragment)
        navHost?.visibility = android.view.View.GONE

        // Show a temporary message
        val textView = android.widget.TextView(this).apply {
            text = "Welcome to StepBet!\n\n" +
                    "User: ${FirebaseAuth.getInstance().currentUser?.phoneNumber}\n\n" +
                    "App is loading..."
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        // Add the text view to the main container
        val container = binding.root as android.view.ViewGroup
        container.addView(textView)
    }

    private fun startLoginActivity() {
        val intent = android.content.Intent(this, com.example.stepbet.auth.LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}