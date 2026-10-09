package de.flexy.stundenplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import de.flexy.stundenplan.ui.TimetableScreen
import de.flexy.stundenplan.ui.TimetableViewModel
import de.flexy.stundenplan.ui.theme.StundenplanTheme

class MainActivity : ComponentActivity() {

    private val vm: TimetableViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        de.flexy.stundenplan.system.SyncWorker.schedule(this)
        setContent {
            StundenplanTheme {
                TimetableScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshIfStale()
    }
}
