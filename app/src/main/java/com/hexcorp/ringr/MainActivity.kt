package com.hexcorp.ringr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hexcorp.ringr.ui.screens.FinalizeScreen
import com.hexcorp.ringr.ui.screens.LandingScreen
import com.hexcorp.ringr.ui.screens.TrimScreen
import com.hexcorp.ringr.ui.theme.RingBg
import com.hexcorp.ringr.ui.theme.RingDark
import com.hexcorp.ringr.ui.theme.RingRTheme
import com.hexcorp.ringr.viewmodel.RingRViewModel
import com.hexcorp.ringr.viewmodel.Step

class MainActivity : ComponentActivity() {

    private val viewModel: RingRViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RingRTheme {
                Surface(color = RingBg) {
                    RingRApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun RingRApp(viewModel: RingRViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.step != Step.LANDING) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_ringr_logo),
                    contentDescription = "Ring-R",
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(30.dp),
                )
                Text("Convert YouTube links into ringtone", style = MaterialTheme.typography.bodyMedium, color = RingDark)
            }
        }

        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
            when (uiState.step) {
                Step.LANDING -> LandingScreen(
                    loading = uiState.loading,
                    error = uiState.error,
                    onSubmit = viewModel::submitLink,
                )

                Step.TRIM -> uiState.job?.let { job ->
                    TrimScreen(
                        job = job,
                        loading = uiState.loading,
                        error = uiState.error,
                        onRename = viewModel::rename,
                        onBack = viewModel::backToLanding,
                        onProceed = viewModel::proceedToFinalize,
                    )
                }

                Step.FINALIZE -> uiState.job?.let { job ->
                    FinalizeScreen(
                        job = job,
                        onRename = viewModel::rename,
                        onBack = viewModel::backToTrim,
                        onMakeAnother = viewModel::makeAnother,
                    )
                }
            }
        }
    }
}
