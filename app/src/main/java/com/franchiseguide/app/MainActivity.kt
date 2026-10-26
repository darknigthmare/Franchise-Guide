package com.franchiseguide.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.franchiseguide.app.data.AppRepository
import com.franchiseguide.app.ui.FranchiseGuideApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = AppRepository.getInstance(applicationContext)
        setContent { FranchiseGuideApp(repository) }
    }
}
