package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.PdfOperationResult
import com.example.data.PdfToolRoute
import com.example.data.ThemeMode
import com.example.ui.PdfMasterViewModel
import com.example.ui.components.ProcessingStateDialogs
import com.example.ui.screens.CompressPdfScreen
import com.example.ui.screens.ExtractAssetsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MergePdfScreen
import com.example.ui.screens.OrganizePagesScreen
import com.example.ui.screens.PdfToImagesScreen
import com.example.ui.screens.PdfToWordScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.SecurityPdfScreen
import com.example.ui.screens.SignatureScreen
import com.example.ui.screens.SplitExtractScreen
import com.example.ui.screens.WatermarkScreen
import com.example.ui.theme.MawidAlLawzTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI or ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        val incomingPdfUri: Uri? = if (intent?.action == Intent.ACTION_VIEW) {
            intent.data
        } else {
            null
        }

        setContent {
            val viewModel: PdfMasterViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Force authentic Arabic RTL layout direction regardless of emulator locale.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MawidAlLawzTheme(darkTheme = darkTheme) {
                    MawidAlLawzPdfApp(
                        viewModel = viewModel,
                        incomingPdfUri = incomingPdfUri
                    )
                }
            }
        }
    }
}

@Composable
private fun MawidAlLawzPdfApp(
    viewModel: PdfMasterViewModel,
    incomingPdfUri: Uri?
) {
    val navController = rememberNavController()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val activePdf by viewModel.activePdf.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val savedSignatures by viewModel.savedSignatures.collectAsStateWithLifecycle()
    val mergeCandidates by viewModel.mergeCandidates.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Pending action targets for SAF document creation / folder selection
    var pendingSaveResult by remember { mutableStateOf<PdfOperationResult?>(null) }
    var pendingFolderResult by remember { mutableStateOf<PdfOperationResult?>(null) }
    var openViewerAfterPick by remember { mutableStateOf(false) }

    val openSinglePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val navigateToViewer = openViewerAfterPick
            openViewerAfterPick = false
            viewModel.loadPdfFromUri(uri) {
                if (navigateToViewer) {
                    navController.navigate(PdfToolRoute.VIEWER.route) {
                        launchSingleTop = true
                    }
                }
            }
        } else {
            openViewerAfterPick = false
        }
    }

    val openMultiplePdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addMergeFiles(uris)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = pendingSaveResult
        pendingSaveResult = null
        if (uri != null && target != null) {
            viewModel.saveResultToUri(target, uri)
        }
    }

    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val target = pendingFolderResult
        pendingFolderResult = null
        if (treeUri != null && target != null) {
            viewModel.saveMultipleResultsToFolder(target, treeUri)
        }
    }

    LaunchedEffect(incomingPdfUri) {
        if (incomingPdfUri != null) {
            viewModel.loadPdfFromUri(incomingPdfUri) {
                navController.navigate(PdfToolRoute.VIEWER.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusBanner()
    }

    val launchSinglePdfPicker: (Boolean) -> Unit = { navigateViewer ->
        openViewerAfterPick = navigateViewer
        openSinglePdfLauncher.launch(arrayOf("application/pdf"))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { outerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = outerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = PdfToolRoute.HOME.route
            ) {
                composable(PdfToolRoute.HOME.route) {
                    HomeScreen(
                        themeMode = themeMode,
                        activePdf = activePdf,
                        recentFiles = recentFiles,
                        onCycleTheme = viewModel::cycleThemeMode,
                        onPickPdfAndOpenViewer = { launchSinglePdfPicker(true) },
                        onCreateSamplePdf = {
                            viewModel.createAndLoadSamplePdf {
                                navController.navigate(PdfToolRoute.VIEWER.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onSelectTool = { route ->
                            navController.navigate(route.route) {
                                launchSingleTop = true
                            }
                        },
                        onOpenRecentFile = { rec ->
                            viewModel.loadPdfFromUri(Uri.parse(rec.uriString)) {
                                navController.navigate(PdfToolRoute.VIEWER.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onClearRecentFiles = viewModel::clearRecentHistory
                    )
                }

                composable(PdfToolRoute.VIEWER.route) {
                    PdfViewerScreen(
                        activePdf = activePdf,
                        pageRenderer = viewModel.pageRenderer,
                        onNavigateBack = { navController.popBackStack() },
                        onPickPdf = { launchSinglePdfPicker(false) },
                        onCreateSamplePdf = { viewModel.createAndLoadSamplePdf() },
                        onSharePdf = viewModel::shareActivePdf,
                        onJumpToTool = { route ->
                            navController.navigate(route.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(PdfToolRoute.SPLIT.route) {
                    SplitExtractScreen(
                        activePdf = activePdf,
                        pageRenderer = viewModel.pageRenderer,
                        onNavigateBack = { navController.popBackStack() },
                        onPickPdf = { launchSinglePdfPicker(false) },
                        onCreateSamplePdf = { viewModel.createAndLoadSamplePdf() },
                        onExecuteSplit = viewModel::runSplitOperation
                    )
                }

                composable(PdfToolRoute.TO_IMAGES.route) {
                    PdfToImagesScreen(
                        activePdf = activePdf,
                        pageRenderer = viewModel.pageRenderer,
                        onNavigateBack = { navController.popBackStack() },
                        onPickPdf = { launchSinglePdfPicker(false) },
                        onCreateSamplePdf = { viewModel.createAndLoadSamplePdf() },
                        onExecuteConvert = viewModel::runPdfToImages
                    )
                }

                composable(PdfToolRoute.SIGNATURE.route) {
                    SignatureScreen(
                        activePdf = activePdf,
                        savedSignatures = savedSignatures,
                        pageRenderer = viewModel.pageRenderer,
                        onNavigateBack = { navController.popBackStack() },
                        onPickPdf = { launchSinglePdfPicker(false) },
                        onCreateSamplePdf = { viewModel.createAndLoadSamplePdf() },
                        onSaveDrawnSignature = viewModel::saveSignatureBitmap,
                        onImportSignatureUri = viewModel::importSignatureImageUri,
                        onDeleteSignature = viewModel::deleteSavedSignature,
                        onApplySignatures = viewModel::runApplySignatures
                    )
                }

                composable(PdfToolRoute.WATERMARK.route) {
                    WatermarkScreen(
                        activePdf = activePdf,
                        pageRenderer = viewModel.pageRenderer,
                        onNavigateBack = { navController.popBackStack() },
                        onPickPdf = { launchSinglePdfPicker(false) },
                        onCreateSamplePdf = { viewModel.createAndLoadSamplePdf() },
                        onImportWatermarkImage = viewModel::importImageToTempFile,
                        onApplyWatermark = viewModel::runWatermark
                    )
                }

                composable(PdfToolRoute.TO_WORD.route) {
                    PdfToWordScreen(
                        activePdf = activePdf,
                        onNavigateBack = { navController.popBackStack() },
                        onPickPdf = { launchSinglePdfPicker(false) },
                        onCreateSamplePdf = { viewModel.createAndLoadSamplePdf() },
                        onExecuteConvertToWord = viewModel::runPdfToWord
                    )
                }

                composable(PdfToolRoute.MERGE.route) {
                    MergePdfScreen(
                        candidates = mergeCandidates,
                        activePdf = activePdf,
                        onNavigateBack = { navController.popBackStack() },
                        onPickMultiplePdfs = { openMultiplePdfsLauncher.launch(arrayOf("application/pdf")) },
                        onAddActivePdf = viewModel::addActivePdfToMerge,
                        onMoveItem = viewModel::moveMergeCandidate,
                        onRemoveItem = viewModel::removeMergeCandidate,
                        onClearAll = viewModel::clearMergeCandidates,
                        onExecuteMerge = viewModel::runMergePdfs
                    )
                }

                composable(PdfToolRoute.ORGANIZE.route) {
                    OrganizePagesScreen(
                        activePdf = activePdf,
                        pageRenderer = viewModel.pageRenderer,
                        onNavigateBack = { navController.popBackStack() },
                        onPickPdf = { launchSinglePdfPicker(false) },
                        onCreateSamplePdf = { viewModel.createAndLoadSamplePdf() },
                        onSaveOrganizedPdf = viewModel::runOrganizePages
                    )
                }

                composable(PdfToolRoute.COMPRESS.route) {
                    CompressPdfScreen(
                        activePdf = activePdf,
                        onNavigateBack = { navController.popBackStack() },
                        onPickPdf = { launchSinglePdfPicker(false) },
                        onCreateSamplePdf = { viewModel.createAndLoadSamplePdf() },
                        onExecuteCompress = viewModel::runCompressPdf
                    )
                }

                composable(PdfToolRoute.SECURITY.route) {
                    SecurityPdfScreen(
                        activePdf = activePdf,
                        onNavigateBack = { navController.popBackStack() },
                        onPickPdf = { launchSinglePdfPicker(false) },
                        onCreateSamplePdf = { viewModel.createAndLoadSamplePdf() },
                        onProtectPdf = viewModel::runProtectPdf,
                        onUnlockPdf = viewModel::runUnlockPdf
                    )
                }

                composable(PdfToolRoute.EXTRACT_ASSETS.route) {
                    ExtractAssetsScreen(
                        activePdf = activePdf,
                        onNavigateBack = { navController.popBackStack() },
                        onPickPdf = { launchSinglePdfPicker(false) },
                        onCreateSamplePdf = { viewModel.createAndLoadSamplePdf() },
                        onExecuteExtractAssets = viewModel::runExtractTextAndImages
                    )
                }
            }

            // Global Processing Progress, Error, and Pre-Save Preview / Save / Share Dialogs
            ProcessingStateDialogs(
                state = processingState,
                pageRenderer = viewModel.pageRenderer,
                onCancelRunning = viewModel::cancelActiveOperation,
                onDismiss = viewModel::dismissProcessingState,
                onSavePrimary = { result ->
                    pendingSaveResult = result
                    createDocumentLauncher.launch(result.suggestedFileName)
                },
                onSaveMultipleToFolder = { result ->
                    pendingFolderResult = result
                    openDocumentTreeLauncher.launch(null)
                },
                onShare = { result ->
                    viewModel.shareResult(result)
                },
                onOpenInViewer = { result ->
                    viewModel.dismissProcessingState()
                    viewModel.loadLocalPdfFile(result.primaryOutputFile, result.suggestedFileName) {
                        navController.navigate(PdfToolRoute.VIEWER.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}
