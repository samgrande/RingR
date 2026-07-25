package com.hexcorp.ringr

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.hexcorp.ringr.ui.components.BackgroundShapes
import com.hexcorp.ringr.ui.screens.FinalizeScreen
import com.hexcorp.ringr.ui.screens.LandingScreen
import com.hexcorp.ringr.ui.screens.TrimScreen
import com.hexcorp.ringr.ui.theme.RingRTheme
import com.hexcorp.ringr.viewmodel.RingRViewModel
import com.hexcorp.ringr.viewmodel.Step

class MainActivity : ComponentActivity() {

    private val viewModel: RingRViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RingRTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RingRApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun RingRApp(viewModel: RingRViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val backgroundColor = MaterialTheme.colorScheme.background
    val isDark = isSystemInDarkTheme()
    val window = (LocalContext.current as? Activity)?.window

    SideEffect {
        window?.let {
            it.statusBarColor = backgroundColor.toArgb()
            WindowInsetsControllerCompat(it, it.decorView).isAppearanceLightStatusBars = !isDark
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackgroundShapes()

        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.step != Step.LANDING) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_ringr_logo),
                        contentDescription = "Ring-R",
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(30.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                    )
                    Text("Convert YouTube links into ringtone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }

            Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
                AnimatedContent(
                    targetState = uiState.step,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        slideInVertically(tween(300)) { it * dir } + fadeIn(tween(200)) togetherWith
                        slideOutVertically(tween(300)) { -it * dir } + fadeOut(tween(200))
                    },
                ) { step ->
                    when (step) {
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
}
}
