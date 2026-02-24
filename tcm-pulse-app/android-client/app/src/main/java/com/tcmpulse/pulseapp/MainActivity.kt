package com.tcmpulse.pulseapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tcmpulse.pulseapp.ui.screens.HistoryScreen
import com.tcmpulse.pulseapp.ui.screens.HomeScreen
import com.tcmpulse.pulseapp.ui.screens.PrescriptionsScreen
import com.tcmpulse.pulseapp.ui.screens.ProfileScreen
import com.tcmpulse.pulseapp.ui.screens.PulseCollectionScreen
import com.tcmpulse.pulseapp.ui.screens.PulseReportScreen
import com.tcmpulse.pulseapp.ui.theme.TCMPulseTheme
import com.tcmpulse.pulseapp.viewmodel.PulseViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: PulseViewModel by viewModels()

    // 动态权限请求（Android 12+ 需要 BLUETOOTH_SCAN / BLUETOOTH_CONNECT；
    // 低版本需要 ACCESS_FINE_LOCATION 才能扫描 BLE）
    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // 无论结果如何都进入采集流程；WatchBluetoothManager 内部处理无权限情况
        viewModel.startPulseCollection()
    }

    fun requestBlePermissionsAndStartScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        blePermissionLauncher.launch(perms)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TCMPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TCMPulseApp(
                        viewModel = viewModel,
                        onRequestBlePermissions = ::requestBlePermissionsAndStartScan
                    )
                }
            }
        }
    }
}

@Composable
fun TCMPulseApp(
    viewModel: PulseViewModel,
    onRequestBlePermissions: () -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {

        // 首页
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

        // 脉诊采集
        composable("pulse_collection") {
            PulseCollectionScreen(
                collectionState = viewModel.collectionState.value,
                onStartCollection = onRequestBlePermissions,
                onCancelCollection = {
                    viewModel.cancelCollection()
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
                onViewReport = { navController.navigate("pulse_report/latest") },
                waveformData = viewModel.waveformData.value
            )
        }

        // 脉诊报告
        composable(
            route = "pulse_report/{recordId}",
            arguments = listOf(navArgument("recordId") { type = NavType.StringType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString("recordId")
            PulseReportScreen(
                record = viewModel.getRecordDetail(recordId),
                recommendations = viewModel.recommendations.value,
                onBack = { navController.popBackStack() },
                onShare = { viewModel.shareReport(recordId) },
                onViewPrescription = { navController.navigate("prescriptions") }
            )
        }

        // 历史记录
        composable("history") {
            HistoryScreen(
                records = viewModel.recentRecords.value,
                onBack = { navController.popBackStack() },
                onRecordClick = { id -> navController.navigate("pulse_report/$id") }
            )
        }

        // 方剂推荐
        composable("prescriptions") {
            PrescriptionsScreen(
                recommendations = viewModel.recommendations.value,
                onBack = { navController.popBackStack() }
            )
        }

        // 个人中心
        composable("profile") {
            ProfileScreen(
                totalRecords = viewModel.recentRecords.value.size,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
