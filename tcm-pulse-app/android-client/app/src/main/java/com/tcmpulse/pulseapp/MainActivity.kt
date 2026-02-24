package com.tcmpulse.pulseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tcmpulse.pulseapp.data.model.PulseCollectionState
import com.tcmpulse.pulseapp.ui.screens.HomeScreen
import com.tcmpulse.pulseapp.ui.screens.PulseCollectionScreen
import com.tcmpulse.pulseapp.ui.screens.PulseReportScreen
import com.tcmpulse.pulseapp.ui.theme.TCMPulseTheme
import com.tcmpulse.pulseapp.viewmodel.PulseViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val viewModel: PulseViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            TCMPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TCMPulseApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun TCMPulseApp(viewModel: PulseViewModel) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onStartPulseCheck = { navController.navigate("pulse_collection") },
                onViewHistory = { navController.navigate("history") },
                onViewPrescriptions = { navController.navigate("prescriptions") },
                onViewProfile = { navController.navigate("profile") },
                recentRecords = viewModel.recentRecords.value,
                healthScore = viewModel.healthScore.value
            )
        }
        
        composable("pulse_collection") {
            PulseCollectionScreen(
                collectionState = viewModel.collectionState.value,
                onStartCollection = { viewModel.startPulseCollection() },
                onCancelCollection = { viewModel.cancelCollection() },
                onBack = { navController.popBackStack() },
                waveformData = viewModel.waveformData.value
            )
        }
        
        composable("pulse_report/{recordId}") { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString("recordId")
            PulseReportScreen(
                record = viewModel.getRecordDetail(recordId),
                recommendations = viewModel.recommendations.value,
                onBack = { navController.popBackStack() },
                onShare = { viewModel.shareReport(recordId) },
                onViewPrescription = { prescriptionId ->
                    navController.navigate("prescription/$prescriptionId")
                }
            )
        }
    }
}
