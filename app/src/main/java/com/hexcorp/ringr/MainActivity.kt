package com.hexcorp.ringr

import android.app.Activity
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.hexcorp.ringr.ui.components.BackgroundShapes
import com.hexcorp.ringr.ui.screens.FinalizeScreen
import com.hexcorp.ringr.ui.screens.LandingScreen
import com.hexcorp.ringr.ui.screens.TrimScreen
import com.hexcorp.ringr.ui.theme.RingRTheme
import com.hexcorp.ringr.viewmodel.RingRViewModel
import com.hexcorp.ringr.viewmodel.Step
import kotlinx.coroutines.delay

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

    var pillMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            pillMessage = it
            viewModel.clearError()
        }
    }

    LaunchedEffect(pillMessage) {
        if (pillMessage != null) {
            delay(3000)
            pillMessage = null
        }
    }

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

        AnimatedVisibility(
            visible = pillMessage != null,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .zIndex(10f),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = pillMessage ?: "",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.step != Step.LANDING && uiState.step != Step.LOADING) {
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
                    Spacer(Modifier.height(8.dp))
                    Text("Convert YouTube links into ringtone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }

            Box(modifier = Modifier.weight(1f).then(
                if (uiState.step == Step.LOADING) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )) {
                AnimatedContent(
                    targetState = uiState.step,
                    transitionSpec = {
                        if (targetState == Step.LOADING || initialState == Step.LOADING) {
                            scaleIn(spring(stiffness = Spring.StiffnessLow), initialScale = 0f) +
                                fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                            scaleOut(spring(stiffness = Spring.StiffnessLow), targetScale = 0f) +
                                fadeOut(spring(stiffness = Spring.StiffnessLow))
                        } else {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            val slideIn = slideInHorizontally(
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                            ) { it * dir } + fadeIn(spring()) +
                                scaleIn(spring(), initialScale = 0.92f)
                            val slideOut = slideOutHorizontally(
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                            ) { -it * dir } + fadeOut(spring())
                            slideIn togetherWith slideOut
                        }
                    },
                ) { step ->
                    when (step) {
                    Step.LANDING -> LandingScreen(
                        loading = uiState.loading,
                        error = uiState.error,
                        onSubmit = viewModel::submitLink,
                    )

                    Step.LOADING -> {
                        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading))
                        val progress by animateLottieCompositionAsState(
                            composition = composition,
                            iterations = Int.MAX_VALUE,
                        )
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val primaryArgb = primaryColor.toArgb()
                        val dynamicProperties = rememberLottieDynamicProperties(
                            rememberLottieDynamicProperty(
                                property = LottieProperty.COLOR,
                                keyPath = arrayOf("**"),
                                value = primaryArgb,
                            ),
                            rememberLottieDynamicProperty(
                                property = LottieProperty.STROKE_COLOR,
                                keyPath = arrayOf("**"),
                                value = primaryArgb,
                            ),
                        )
                        val messages = listOf(
                            "Cooking your ringtone...",
                            "Fetching dope tunes...",
                            "Vibing it up...",
                            "Definitely working...",
                            "Good choice of tune...",
                        )
                        var messageIndex by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(4000)
                                messageIndex = (messageIndex + 1) % messages.size
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                            shape = CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LottieAnimation(
                                        composition = composition,
                                        progress = { progress },
                                        modifier = Modifier.size(192.dp),
                                        dynamicProperties = dynamicProperties,
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                                Spacer(Modifier.height(40.dp))
                                Text(
                                    text = messages[messageIndex],
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (uiState.job?.sourceFile == null) {
                                Button(
                                    onClick = { viewModel.cancelLoading() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 48.dp)
                                        .width(160.dp)
                                        .height(44.dp),
                                ) {
                                    Text("CANCEL", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

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
                            onBack = viewModel::backToLanding,
                            onMakeAnother = viewModel::makeAnother,
                        )
                    }
                }
            }
        }
    }

        var backPressedTime by remember { mutableStateOf(0L) }
        val context = LocalContext.current
        BackHandler(enabled = true) {
            when {
                uiState.step == Step.LOADING -> { }
                uiState.step == Step.LANDING -> {
                    val now = System.currentTimeMillis()
                    if (now - backPressedTime < 2000) {
                        (context as? Activity)?.finish()
                    } else {
                        backPressedTime = now
                        pillMessage = "Press back again to exit"
                    }
                }
                else -> viewModel.backToLanding()
            }
        }
    }
}
