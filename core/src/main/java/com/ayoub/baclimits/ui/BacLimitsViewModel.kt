package com.ayoub.baclimits.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import com.ayoub.baclimits.data.AppUpdaterManager
import com.ayoub.baclimits.data.ExerciseData
import com.ayoub.baclimits.data.FluencyRating
import com.ayoub.baclimits.data.GradeFeedback
import com.ayoub.baclimits.data.LatexFormatter
import com.ayoub.baclimits.data.LimitsRepository
import com.ayoub.baclimits.data.LocalizedMessage
import com.ayoub.baclimits.data.ProgressSyncManager
import com.ayoub.baclimits.data.SolutionData
import com.ayoub.baclimits.data.StudentDatabase
import com.ayoub.baclimits.data.StudentStats
import com.ayoub.baclimits.data.SyncStatusSnapshot
import com.ayoub.baclimits.data.UpdateCheckState
import com.ayoub.baclimits.math.LimitsCasEngine
import com.ayoub.baclimits.ui.i18n.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.Executors

enum class MainTab {
    Practice,
    Progress,
    History,
    Settings
}

enum class HistoryFilter {
    All,
    Correct,
    Wrong,
    Pending
}

data class BacLimitsUiState(
    val lang: String = "ar",
    val selectedTab: MainTab = MainTab.Practice,
    val username: String? = null,
    val displayName: String = "",
    val recentUsers: List<String> = emptyList(),
    val stats: StudentStats? = null,
    val selectedChapter: String = "limits",
    val selectedTechnique: String = "adaptive",
    val selectedDifficulty: Int = 0,
    val exercise: ExerciseData? = null,
    val hintsShown: List<LocalizedMessage> = emptyList(),
    val lastGrade: GradeFeedback? = null,
    val solution: SolutionData? = null,
    val currentFluency: FluencyRating? = null,
    val chronoDisplaySec: Double = 0.0,
    val answerField: TextFieldValue = TextFieldValue(""),
    val livePreviewLatex: String = "",
    val showSystemKeyboard: Boolean = false,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val isLoadingExercise: Boolean = false,
    val isStartingExercise: Boolean = false,
    val isPausingExercise: Boolean = false,
    val isSubmitting: Boolean = false,
    val isLoadingHint: Boolean = false,
    val isLoadingSolution: Boolean = false,
    val isSyncingAccount: Boolean = false,
    val snackbarMessage: String? = null,
    val showTechniqueSheet: Boolean = false,
    val showDifficultySheet: Boolean = false,
    val showSeedDialog: Boolean = false,
    val showSolutionSheet: Boolean = false,
    val showSyncSheet: Boolean = false,
    val showUpdaterSheet: Boolean = false,
    val fullscreenFormulaLatex: String? = null,
    val historyFilter: HistoryFilter = HistoryFilter.All
) {
    val isFocusLocked: Boolean get() = exercise != null && !exercise.isCompleted
}

class BacLimitsViewModel(application: Application) : AndroidViewModel(application) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newFixedThreadPool(4)

    private val db = StudentDatabase(application)
    val syncManager: ProgressSyncManager
    private val repo: LimitsRepository
    val updaterManager: AppUpdaterManager

    private val _uiState = MutableStateFlow(BacLimitsUiState())
    val uiState: StateFlow<BacLimitsUiState> = _uiState.asStateFlow()

    private var timerBaseElapsedSec: Double = 0.0
    private var timerAnchorMs: Long? = null

    private val timerTickRunnable = object : Runnable {
        override fun run() {
            val anchor = timerAnchorMs ?: return
            val ex = _uiState.value.exercise ?: return
            if (ex.timer.state != "running" || ex.isCompleted) {
                timerAnchorMs = null
                return
            }
            val elapsed = timerBaseElapsedSec + ((SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0L) / 1000.0)
            _uiState.update { it.copy(chronoDisplaySec = elapsed) }
            mainHandler.postDelayed(this, 250L)
        }
    }

    private val _syncState = MutableStateFlow(
        SyncStatusSnapshot(
            label = "↻ Connecting...",
            colorHex = "#60A5FA",
            isNetworkConnected = false,
            isServerReachable = false,
            pendingCount = 0,
            serverUrl = "",
            lastSyncAt = 0L,
            lastError = null
        )
    )
    val syncState: StateFlow<SyncStatusSnapshot> = _syncState.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateCheckState())
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    private val _pendingInstallFile = MutableStateFlow<File?>(null)
    val pendingInstallFile: StateFlow<File?> = _pendingInstallFile.asStateFlow()

    private var deferredApkAfterPermission: File? = null

    init {
        syncManager = ProgressSyncManager(application, db) { snapshot ->
            mainHandler.post {
                _syncState.value = snapshot
            }
        }
        repo = LimitsRepository(application, db, syncManager)
        updaterManager = AppUpdaterManager(
            context = application,
            onStateChanged = { next ->
                mainHandler.post { _updateState.value = next }
            },
            onRequestInstallPermission = { file ->
                mainHandler.post {
                    deferredApkAfterPermission = file
                    _pendingInstallFile.value = file
                }
            }
        )

        _syncState.value = syncManager.getCurrentSnapshot()
        _updateState.value = updaterManager.getCurrentState()

        val (savedUser, initialLang) = repo.getInitialSession()
        val initialChapter = repo.getInitialChapter()
        val recent = repo.getRecentUsernames()
        val showIme = repo.getShowSystemKeyboard()

        _uiState.update {
            it.copy(
                lang = initialLang,
                selectedChapter = initialChapter,
                recentUsers = recent,
                showSystemKeyboard = showIme
            )
        }

        syncManager.startMonitoring()
        updaterManager.checkLatestRelease(silent = true)

        if (savedUser != null) {
            login(savedUser, explicitLang = initialLang, isAutoResume = true)
        }
    }

    fun onAppResumed() {
        syncManager.triggerSyncNow(forceHealth = true)
    }

    fun consumePendingInstallRequest() {
        _pendingInstallFile.value = null
    }

    fun onInstallPermissionGranted() {
        deferredApkAfterPermission?.let { file ->
            deferredApkAfterPermission = null
            updaterManager.launchInstaller(file)
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == MainTab.Progress || tab == MainTab.History) {
            refreshStatsSilently()
        }
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _uiState.update { it.copy(historyFilter = filter) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showSnackbar(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun setShowTechniqueSheet(show: Boolean) {
        _uiState.update { it.copy(showTechniqueSheet = show) }
    }

    fun setShowDifficultySheet(show: Boolean) {
        _uiState.update { it.copy(showDifficultySheet = show) }
    }

    fun setShowSeedDialog(show: Boolean) {
        _uiState.update { it.copy(showSeedDialog = show) }
    }

    fun setShowSolutionSheet(show: Boolean) {
        _uiState.update { it.copy(showSolutionSheet = show) }
    }

    fun setShowSyncSheet(show: Boolean) {
        _uiState.update { it.copy(showSyncSheet = show) }
    }

    fun setShowUpdaterSheet(show: Boolean) {
        _uiState.update { it.copy(showUpdaterSheet = show) }
    }

    fun openFullscreenFormula(latex: String) {
        _uiState.update { it.copy(fullscreenFormulaLatex = latex) }
    }

    fun closeFullscreenFormula() {
        _uiState.update { it.copy(fullscreenFormulaLatex = null) }
    }

    fun toggleSystemKeyboard() {
        val next = !_uiState.value.showSystemKeyboard
        repo.setShowSystemKeyboard(next)
        _uiState.update { it.copy(showSystemKeyboard = next) }
    }

    fun toggleLanguage() {
        val current = _uiState.value.lang
        val next = if (current == "ar") "fr" else "ar"
        repo.saveLang(next)
        _uiState.update { it.copy(lang = next) }
        val user = _uiState.value.username ?: return
        ioExecutor.execute {
            repo.updateLang(user, next)
        }
    }

    fun login(rawName: String, explicitLang: String? = null, isAutoResume: Boolean = false) {
        val clean = rawName.trim()
        val lang = explicitLang ?: _uiState.value.lang
        if (clean.length !in 2..32) {
            _uiState.update {
                it.copy(
                    loginError = if (lang == "fr") {
                        "Le nom d'utilisateur doit contenir entre 2 et 32 caractères."
                    } else {
                        "يجب أن يتراوح اسم التلميذ بين 2 و 32 حرفاً."
                    }
                )
            }
            return
        }

        _uiState.update { it.copy(isLoggingIn = true, loginError = null) }

        ioExecutor.execute {
            val loginRes = repo.login(clean, lang)
            loginRes.onSuccess { stats ->
                val activeUser = stats.username.ifBlank { clean }
                val activeDisplay = stats.displayName.ifBlank { clean }
                repo.saveSession(activeUser, lang)
                val recent = repo.getRecentUsernames()

                val resumeRes = repo.resume(activeUser, lang).getOrNull()
                val resumedEx = resumeRes?.exercise
                val isCompleted = resumeRes == null || !resumeRes.hasExercise || resumedEx == null ||
                        resumedEx.isCompleted || resumeRes.lastGrade?.isCorrect == true || resumeRes.solution != null

                if (!isCompleted && resumedEx != null) {
                    val ans = resumeRes.studentAnswer
                    val preview = LatexFormatter.asciiToPreviewLatex(ans)
                    mainHandler.post {
                        _uiState.update {
                            it.copy(
                                isLoggingIn = false,
                                loginError = null,
                                username = activeUser,
                                displayName = activeDisplay,
                                stats = stats,
                                recentUsers = recent,
                                selectedChapter = resumedEx.chapter,
                                exercise = resumedEx,
                                hintsShown = resumeRes.hints,
                                lastGrade = resumeRes.lastGrade,
                                solution = resumeRes.solution,
                                currentFluency = resumeRes.fluency,
                                answerField = TextFieldValue(ans, TextRange(ans.length)),
                                livePreviewLatex = preview,
                                selectedTab = MainTab.Practice
                            )
                        }
                        syncTimerFromExercise(resumedEx)
                    }
                } else {
                    val targetChapter = _uiState.value.selectedChapter
                    val exRes = repo.nextExercise(
                        username = activeUser,
                        chapter = targetChapter,
                        technique = _uiState.value.selectedTechnique,
                        difficulty = _uiState.value.selectedDifficulty,
                        seed = null,
                        lang = lang
                    )
                    val loadedEx = exRes.getOrNull()
                    mainHandler.post {
                        _uiState.update {
                            it.copy(
                                isLoggingIn = false,
                                loginError = null,
                                username = activeUser,
                                displayName = activeDisplay,
                                stats = stats,
                                recentUsers = recent,
                                selectedChapter = loadedEx?.chapter ?: targetChapter,
                                exercise = loadedEx,
                                hintsShown = emptyList(),
                                lastGrade = null,
                                solution = null,
                                currentFluency = null,
                                answerField = TextFieldValue(""),
                                livePreviewLatex = "",
                                selectedTab = MainTab.Practice,
                                snackbarMessage = exRes.exceptionOrNull()?.message
                            )
                        }
                        syncTimerFromExercise(loadedEx)
                    }
                }
            }.onFailure { err ->
                mainHandler.post {
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            loginError = if (isAutoResume) null else (err.message ?: "Login error")
                        )
                    }
                }
            }
        }
    }

    private fun syncTimerFromExercise(ex: ExerciseData?) {
        mainHandler.removeCallbacks(timerTickRunnable)
        if (ex == null) {
            timerBaseElapsedSec = 0.0
            timerAnchorMs = null
            _uiState.update { it.copy(chronoDisplaySec = 0.0) }
            return
        }
        val t = ex.timer
        val base = if (t.state == "running") t.liveElapsedSec.coerceAtLeast(t.elapsedSec) else t.elapsedSec
        timerBaseElapsedSec = base
        if (t.state == "running" && !ex.isCompleted) {
            timerAnchorMs = SystemClock.elapsedRealtime()
            _uiState.update { it.copy(chronoDisplaySec = base) }
            mainHandler.postDelayed(timerTickRunnable, 250L)
        } else {
            timerAnchorMs = null
            _uiState.update { it.copy(chronoDisplaySec = base) }
        }
    }

    private fun currentClientSolveTimeSec(): Double {
        val anchor = timerAnchorMs
        return if (anchor != null) {
            timerBaseElapsedSec + ((SystemClock.elapsedRealtime() - anchor).coerceAtLeast(0L) / 1000.0)
        } else {
            _uiState.value.chronoDisplaySec
        }
    }

    private fun checkFocusLockBlocked(targetSeed: String? = null): Boolean {
        val st = _uiState.value
        val activeEx = st.exercise
        if (activeEx != null && !activeEx.isCompleted) {
            if (!targetSeed.isNullOrBlank() && targetSeed.equals(activeEx.seed, ignoreCase = true)) {
                return false
            }
            val msg = AppStrings.forLang(st.lang).focusLockSwitchBlocked
            _uiState.update {
                it.copy(
                    snackbarMessage = msg,
                    showTechniqueSheet = false,
                    showDifficultySheet = false,
                    showSeedDialog = false
                )
            }
            return true
        }
        return false
    }

    fun logout() {
        syncTimerFromExercise(null)
        repo.clearSession()
        val recent = repo.getRecentUsernames()
        _uiState.update {
            BacLimitsUiState(
                lang = it.lang,
                selectedChapter = it.selectedChapter,
                recentUsers = recent,
                showSystemKeyboard = it.showSystemKeyboard
            )
        }
    }

    fun selectChapter(chapter: String) {
        val normalized = if (chapter == "sequences") "sequences" else "limits"
        if (normalized == _uiState.value.selectedChapter && _uiState.value.exercise != null) {
            return
        }
        if (checkFocusLockBlocked()) return
        repo.saveChapter(normalized)
        _uiState.update {
            it.copy(
                selectedChapter = normalized,
                selectedTechnique = "adaptive"
            )
        }
        loadNextExercise(
            chapter = normalized,
            technique = "adaptive",
            difficulty = _uiState.value.selectedDifficulty,
            seed = null
        )
    }

    fun selectTechniqueAndLoad(technique: String) {
        if (checkFocusLockBlocked()) return
        val nextChapter = if (technique == "adaptive") {
            _uiState.value.selectedChapter
        } else {
            LimitsCasEngine.chapterOfTechnique(technique)
        }
        repo.saveChapter(nextChapter)
        _uiState.update {
            it.copy(
                selectedChapter = nextChapter,
                selectedTechnique = technique,
                showTechniqueSheet = false
            )
        }
        loadNextExercise(
            chapter = nextChapter,
            technique = technique,
            difficulty = _uiState.value.selectedDifficulty,
            seed = null
        )
    }

    fun selectDifficultyAndLoad(difficulty: Int) {
        if (checkFocusLockBlocked()) return
        _uiState.update {
            it.copy(
                selectedDifficulty = difficulty,
                showDifficultySheet = false
            )
        }
        loadNextExercise(
            chapter = _uiState.value.selectedChapter,
            technique = _uiState.value.selectedTechnique,
            difficulty = difficulty,
            seed = null
        )
    }

    fun loadNextExercise(
        chapter: String = _uiState.value.selectedChapter,
        technique: String = _uiState.value.selectedTechnique,
        difficulty: Int = _uiState.value.selectedDifficulty,
        seed: String? = null
    ) {
        val user = _uiState.value.username ?: return
        val currentEx = _uiState.value.exercise
        if (!seed.isNullOrBlank() && currentEx != null && seed.equals(currentEx.seed, ignoreCase = true)) {
            _uiState.update { it.copy(showSeedDialog = false, selectedTab = MainTab.Practice) }
            return
        }
        if (checkFocusLockBlocked(seed)) return
        if (_uiState.value.isLoadingExercise) return

        val lang = _uiState.value.lang
        _uiState.update {
            it.copy(
                isLoadingExercise = true,
                showSeedDialog = false,
                selectedTab = MainTab.Practice
            )
        }

        ioExecutor.execute {
            val res = repo.nextExercise(
                username = user,
                chapter = chapter,
                technique = technique,
                difficulty = difficulty,
                seed = seed,
                lang = lang
            )
            mainHandler.post {
                res.onSuccess { ex ->
                    repo.saveChapter(ex.chapter)
                    _uiState.update {
                        it.copy(
                            isLoadingExercise = false,
                            selectedChapter = ex.chapter,
                            exercise = ex,
                            hintsShown = emptyList(),
                            lastGrade = null,
                            solution = null,
                            currentFluency = null,
                            answerField = TextFieldValue(""),
                            livePreviewLatex = ""
                        )
                    }
                    syncTimerFromExercise(ex)
                    refreshStatsSilently()
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingExercise = false,
                            snackbarMessage = err.message ?: "Error loading exercise"
                        )
                    }
                }
            }
        }
    }

    fun startExercise() {
        val user = _uiState.value.username ?: return
        val ex = _uiState.value.exercise ?: return
        if (_uiState.value.isStartingExercise || ex.isRevealed) return

        val lang = _uiState.value.lang
        _uiState.update { it.copy(isStartingExercise = true) }
        ioExecutor.execute {
            val res = repo.startExercise(user, ex.seed, lang)
            mainHandler.post {
                res.onSuccess { started ->
                    _uiState.update {
                        it.copy(
                            isStartingExercise = false,
                            exercise = started
                        )
                    }
                    syncTimerFromExercise(started)
                    refreshStatsSilently()
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isStartingExercise = false,
                            snackbarMessage = err.message ?: "Error starting exercise"
                        )
                    }
                }
            }
        }
    }

    fun pauseOrResumeChrono() {
        val user = _uiState.value.username ?: return
        val ex = _uiState.value.exercise ?: return
        if (_uiState.value.isPausingExercise || !ex.isRevealed || ex.isCompleted) return

        val lang = _uiState.value.lang
        val isPaused = ex.timer.state == "paused"
        _uiState.update { it.copy(isPausingExercise = true) }

        ioExecutor.execute {
            if (isPaused) {
                val res = repo.startExercise(user, ex.seed, lang)
                mainHandler.post {
                    res.onSuccess { resumed ->
                        _uiState.update {
                            it.copy(
                                isPausingExercise = false,
                                exercise = resumed
                            )
                        }
                        syncTimerFromExercise(resumed)
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isPausingExercise = false,
                                snackbarMessage = err.message ?: "Error resuming timer"
                            )
                        }
                    }
                }
            } else {
                val res = repo.pauseExercise(user, ex.seed, lang)
                mainHandler.post {
                    res.onSuccess { paused ->
                        val updatedEx = ex.copy(
                            isRevealed = paused.isRevealed,
                            isCompleted = paused.isCompleted,
                            timer = paused.timer
                        )
                        _uiState.update {
                            it.copy(
                                isPausingExercise = false,
                                exercise = updatedEx
                            )
                        }
                        syncTimerFromExercise(updatedEx)
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isPausingExercise = false,
                                snackbarMessage = err.message ?: "Error pausing timer"
                            )
                        }
                    }
                }
            }
        }
    }

    fun requestNextHint() {
        val user = _uiState.value.username ?: return
        val ex = _uiState.value.exercise ?: return
        if (!ex.isRevealed) return
        val currentIdx = _uiState.value.hintsShown.size
        if (_uiState.value.isLoadingHint || currentIdx >= ex.totalHints) return

        val lang = _uiState.value.lang
        _uiState.update { it.copy(isLoadingHint = true) }
        ioExecutor.execute {
            val res = repo.requestHint(user, ex.seed, currentIdx, lang)
            mainHandler.post {
                res.onSuccess { hint ->
                    _uiState.update {
                        it.copy(
                            isLoadingHint = false,
                            hintsShown = it.hintsShown + hint
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingHint = false,
                            snackbarMessage = err.message ?: "Error loading hint"
                        )
                    }
                }
            }
        }
    }

    fun submitAnswer() {
        val user = _uiState.value.username ?: return
        val ex = _uiState.value.exercise ?: return
        if (!ex.isRevealed || ex.isCompleted) return
        val answerText = _uiState.value.answerField.text.trim()
        if (_uiState.value.isSubmitting || answerText.isEmpty()) return

        val lang = _uiState.value.lang
        val elapsedSec = currentClientSolveTimeSec()
        _uiState.update { it.copy(isSubmitting = true) }
        ioExecutor.execute {
            val res = repo.submitAnswer(user, ex.seed, answerText, elapsedSec, _uiState.value.stats, lang)
            mainHandler.post {
                res.onSuccess { submitResult ->
                    val completedNow = submitResult.isCompleted || ex.isCompleted || submitResult.grade.isCorrect
                    val nextTimer = submitResult.timer ?: ex.timer.copy(
                        state = if (completedNow) "completed" else ex.timer.state,
                        elapsedSec = elapsedSec,
                        liveElapsedSec = elapsedSec
                    )
                    val updatedEx = ex.copy(
                        isCompleted = completedNow,
                        timer = nextTimer
                    )
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            exercise = updatedEx,
                            lastGrade = submitResult.grade,
                            stats = submitResult.stats ?: it.stats,
                            solution = submitResult.solution ?: it.solution,
                            currentFluency = submitResult.fluency ?: it.currentFluency
                        )
                    }
                    syncTimerFromExercise(updatedEx)
                    refreshStatsSilently()
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            snackbarMessage = err.message ?: "Error submitting answer"
                        )
                    }
                }
            }
        }
    }

    fun openSolutionSheet() {
        val ex = _uiState.value.exercise ?: return
        if (!ex.isRevealed) return
        _uiState.update { it.copy(showSolutionSheet = true) }
        val user = _uiState.value.username ?: return
        if (_uiState.value.solution != null || _uiState.value.isLoadingSolution) return

        val lang = _uiState.value.lang
        _uiState.update { it.copy(isLoadingSolution = true) }
        ioExecutor.execute {
            val res = repo.requestSolution(user, ex.seed, _uiState.value.stats, lang)
            mainHandler.post {
                res.onSuccess { solRes ->
                    val updatedEx = ex.copy(
                        isCompleted = solRes.isCompleted,
                        fullLimitLatex = solRes.fullLimitLatex ?: ex.fullLimitLatex,
                        timer = solRes.timer ?: ex.timer.copy(state = "completed")
                    )
                    _uiState.update {
                        it.copy(
                            isLoadingSolution = false,
                            exercise = updatedEx,
                            solution = solRes.solution,
                            stats = solRes.stats ?: it.stats,
                            currentFluency = solRes.fluency ?: it.currentFluency
                        )
                    }
                    syncTimerFromExercise(updatedEx)
                    refreshStatsSilently()
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingSolution = false,
                            snackbarMessage = err.message ?: "Error loading solution"
                        )
                    }
                }
            }
        }
    }

    fun refreshStatsSilently() {
        val user = _uiState.value.username ?: return
        val lang = _uiState.value.lang
        ioExecutor.execute {
            val res = repo.stats(user, _uiState.value.stats, lang)
            res.onSuccess { fresh ->
                mainHandler.post {
                    _uiState.update { it.copy(stats = fresh) }
                }
            }
        }
    }

    fun syncAndReloadAccount() {
        if (_uiState.value.isSyncingAccount) return
        _uiState.update { it.copy(isSyncingAccount = true) }

        syncManager.triggerSyncNow(forceHealth = true) { _ ->
            val user = _uiState.value.username
            val lang = _uiState.value.lang
            if (user == null) {
                mainHandler.post {
                    _uiState.update { it.copy(isSyncingAccount = false) }
                }
                return@triggerSyncNow
            }
            ioExecutor.execute {
                val statsRes = repo.stats(user, _uiState.value.stats, lang)
                val resumeRes = repo.resume(user, lang).getOrNull()
                mainHandler.post {
                    val resumedEx = resumeRes?.exercise
                    val isCompleted = resumeRes == null || !resumeRes.hasExercise || resumedEx == null ||
                            resumedEx.isCompleted || resumeRes.lastGrade?.isCorrect == true || resumeRes.solution != null
                    var syncedExToTick: ExerciseData? = null
                    _uiState.update { state ->
                        val nextStats = statsRes.getOrNull() ?: state.stats
                        if (!isCompleted && state.exercise == null && resumedEx != null) {
                            val ans = resumeRes.studentAnswer
                            syncedExToTick = resumedEx
                            state.copy(
                                isSyncingAccount = false,
                                stats = nextStats,
                                selectedChapter = resumedEx.chapter,
                                exercise = resumedEx,
                                hintsShown = resumeRes.hints,
                                lastGrade = resumeRes.lastGrade,
                                solution = resumeRes.solution,
                                currentFluency = resumeRes.fluency,
                                answerField = TextFieldValue(ans, TextRange(ans.length)),
                                livePreviewLatex = LatexFormatter.asciiToPreviewLatex(ans)
                            )
                        } else {
                            state.copy(
                                isSyncingAccount = false,
                                stats = nextStats
                            )
                        }
                    }
                    syncedExToTick?.let { syncTimerFromExercise(it) }
                }
            }
        }
    }

    fun saveServerConfig(url: String, token: String) {
        syncManager.saveSyncConfig(url, token, triggerSync = false)
        syncAndReloadAccount()
    }

    fun resetDefaultServer() {
        syncManager.resetToDefaultServer(triggerSync = false)
        syncAndReloadAccount()
    }

    fun updateAnswerField(value: TextFieldValue) {
        val preview = LatexFormatter.asciiToPreviewLatex(value.text)
        _uiState.update {
            it.copy(
                answerField = value,
                livePreviewLatex = preview
            )
        }
    }

    fun insertMathToken(token: String, cursorBackOffset: Int = 0) {
        val current = _uiState.value.answerField
        val text = current.text
        val start = minOf(current.selection.start, current.selection.end).coerceIn(0, text.length)
        val end = maxOf(current.selection.start, current.selection.end).coerceIn(0, text.length)

        val nextText = text.substring(0, start) + token + text.substring(end)
        val nextCursor = (start + token.length - cursorBackOffset).coerceIn(0, nextText.length)
        updateAnswerField(TextFieldValue(nextText, TextRange(nextCursor)))
    }

    fun backspaceMathInput() {
        val current = _uiState.value.answerField
        val text = current.text
        if (text.isEmpty()) return

        val start = minOf(current.selection.start, current.selection.end).coerceIn(0, text.length)
        val end = maxOf(current.selection.start, current.selection.end).coerceIn(0, text.length)

        if (start != end) {
            val nextText = text.substring(0, start) + text.substring(end)
            updateAnswerField(TextFieldValue(nextText, TextRange(start)))
            return
        }

        if (start <= 0) return

        // Delete multi-char function prefixes as a clean single unit when cursor sits right after "("
        val prefixDeletes = listOf(
            "sin()", "cos()", "tan()", "ln()", "exp()", "abs()", "e^()",
            "sqrt(", "cbrt(", "qdrt(", "⁴√(",
            "sin(", "cos(", "tan(", "ln(", "exp(", "abs(", "e^(", "√(", "∛("
        )
        val before = text.substring(0, start)
        for (p in prefixDeletes) {
            if (before.endsWith(p)) {
                val delStart = start - p.length
                val nextText = text.substring(0, delStart) + text.substring(end)
                updateAnswerField(TextFieldValue(nextText, TextRange(delStart)))
                return
            }
        }

        val delStart = Character.offsetByCodePoints(text, start, -1)
        val nextText = text.substring(0, delStart) + text.substring(end)
        updateAnswerField(TextFieldValue(nextText, TextRange(delStart)))
    }

    fun clearMathInput() {
        updateAnswerField(TextFieldValue("", TextRange.Zero))
    }

    override fun onCleared() {
        mainHandler.removeCallbacks(timerTickRunnable)
        syncManager.stopMonitoring()
        ioExecutor.shutdownNow()
        super.onCleared()
    }
}
