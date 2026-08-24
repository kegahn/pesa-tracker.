package ke.mpesa.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ke.mpesa.tracker.ui.MainViewModel
import ke.mpesa.tracker.ui.PesaTrackerTheme
import ke.mpesa.tracker.ui.RootScreen

class MainActivity : ComponentActivity() {

    private val permissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var granted by remember { mutableStateOf(hasSmsPermission()) }
            val vm: MainViewModel = viewModel()

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                granted = result[Manifest.permission.READ_SMS] == true
                if (granted) vm.importInbox()
            }

            LaunchedEffect(granted) {
                if (granted) vm.importIfEmpty()
            }

            PesaTrackerTheme {
                RootScreen(
                    vm = vm,
                    hasSmsPermission = granted,
                    onRequestPermission = { launcher.launch(permissions) }
                )
            }
        }
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
}
