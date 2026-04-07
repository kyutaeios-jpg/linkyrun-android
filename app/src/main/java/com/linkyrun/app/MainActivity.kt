package com.linkyrun.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    // 지원 언어 → (위키코드, UI언어명, 데일리위키)
    data class LangConfig(
        val code: String,       // namu / en / ko / ja / de / fr
        val label: String,      // 버튼에 표시
        val wikiForDaily: String,
        val subtitle: String,
        val howToPlay: String,
        val rule1: String, val rule2: String, val rule3: String, val rule4: String,
        val btnStart: String,
        val btnStats: String,
        val btnRanking: String,
        val dailyBadge: String,
        val btnDailyStart: String,
        val diffTitle: String,
        val diffEasy: String, val diffMedium: String, val diffHard: String, val diffVeryHard: String,
        val diffEasyDesc: String, val diffMediumDesc: String, val diffHardDesc: String, val diffVeryHardDesc: String,
        val langSettingTitle: String, val langSettingHint: String, val btnBack: String,
        val rankEmpty: String,
        val rankTabDaily: String, val rankTabEasy: String, val rankTabMedium: String,
        val rankTabHard: String, val rankTabVeryHard: String,
        val hopsUnit: String
    )

    private val LANGS = mapOf(
        "namu" to LangConfig("namu","🇰🇷 나무위키","namu",
            "링크를 타고 목표 페이지까지 달려가세요","게임 방법",
            "1. 난이도 선택 → 시작·목표 페이지 자동 배정",
            "2. 내부 링크만 클릭해서 목표 페이지까지 이동",
            "3. 최단 시간 · 최소 클릭으로 도달하면 승리!",
            "4. 뒤로 가기 · 검색 · 외부 링크 사용 금지",
            "게임 시작하기  ▶","내 기록 📊","랭킹 🏆",
            "오늘의 챌린지","📅 오늘의 챌린지 시작",
            "난이도 선택","쉬움","보통","어려움","매우 어려움",
            "역링크 500개 이상","역링크 120개 이상","역링크 40개 이상","역링크 10개 이상",
            "🌐 언어 설정","탭하여 변경 →","← 뒤로",
            "기록이 없습니다",
            "📅 데일리","🟢 쉬움","🟡 보통","🔴 어려움","💀 매우 어려움","홉"),

        "ko" to LangConfig("ko","🇰🇷 한국어 위키피디아","ko",
            "링크를 타고 목표 페이지까지 달려가세요","게임 방법",
            "1. 난이도 선택 → 시작·목표 페이지 자동 배정",
            "2. 내부 링크만 클릭해서 목표 페이지까지 이동",
            "3. 최단 시간 · 최소 클릭으로 도달하면 승리!",
            "4. 뒤로 가기 · 검색 · 외부 링크 사용 금지",
            "게임 시작하기  ▶","내 기록 📊","랭킹 🏆",
            "오늘의 챌린지","📅 오늘의 챌린지 시작",
            "난이도 선택","쉬움","보통","어려움","매우 어려움",
            "역링크 500개 이상","역링크 120개 이상","역링크 40개 이상","역링크 10개 이상",
            "🌐 언어 설정","탭하여 변경 →","← 뒤로",
            "기록이 없습니다",
            "📅 데일리","🟢 쉬움","🟡 보통","🔴 어려움","💀 매우 어려움","홉"),

        "en" to LangConfig("en","🇺🇸 English Wikipedia","en",
            "Click links to reach the goal page","How to Play",
            "1. Choose difficulty → Start & goal pages are assigned",
            "2. Only click internal links to navigate",
            "3. Reach the goal in shortest time & fewest hops!",
            "4. No back button · No search · No external links",
            "Start Game  ▶","My Stats 📊","Ranking 🏆",
            "Today's Challenge","📅 Start Today's Challenge",
            "Select Difficulty","Easy","Medium","Hard","Very Hard",
            "500+ backlinks","120+ backlinks","40+ backlinks","10+ backlinks",
            "🌐 Language","Tap to change →","← Back",
            "No records yet",
            "📅 Daily","🟢 Easy","🟡 Medium","🔴 Hard","💀 Very Hard","hops"),

        "ja" to LangConfig("ja","🇯🇵 日本語 ウィキペディア","ja",
            "リンクをたどってゴールページへ","遊び方",
            "1. 難易度を選ぶ → スタート・ゴールが決まる",
            "2. 内部リンクだけクリックしてゴールへ",
            "3. 最短時間・最少クリックでゴール！",
            "4. 戻るボタン・検索・外部リンク禁止",
            "ゲーム開始  ▶","記録 📊","ランキング 🏆",
            "今日のチャレンジ","📅 今日のチャレンジを開始",
            "難易度選択","かんたん","ふつう","むずかしい","超むずかしい",
            "リンク500以上","リンク120以上","リンク40以上","リンク10以上",
            "🌐 言語設定","タップして変更 →","← 戻る",
            "記録なし",
            "📅 デイリー","🟢 かんたん","🟡 ふつう","🔴 むずかしい","💀 超むずかしい","クリック"),

        "de" to LangConfig("de","🇩🇪 Deutsche Wikipedia","de",
            "Klicke Links bis zum Ziel","Spielregeln",
            "1. Schwierigkeit wählen → Start & Ziel werden zugewiesen",
            "2. Nur interne Links anklicken",
            "3. Ziel in kürzester Zeit & wenigsten Klicks!",
            "4. Kein Zurück · Keine Suche · Keine externen Links",
            "Spiel starten  ▶","Meine Stats 📊","Rangliste 🏆",
            "Tages-Challenge","📅 Tages-Challenge starten",
            "Schwierigkeit","Leicht","Mittel","Schwer","Sehr schwer",
            "500+ Links","120+ Links","40+ Links","10+ Links",
            "🌐 Sprache","Tippen zum Ändern →","← Zurück",
            "Keine Einträge",
            "📅 Täglich","🟢 Leicht","🟡 Mittel","🔴 Schwer","💀 Sehr schwer","Klicks"),

        "fr" to LangConfig("fr","🇫🇷 Wikipédia français","fr",
            "Cliquez les liens pour atteindre la cible","Comment jouer",
            "1. Choisir la difficulté → pages de départ et d'arrivée",
            "2. Cliquer uniquement les liens internes",
            "3. Atteindre le but le plus vite possible !",
            "4. Pas de retour · Pas de recherche · Pas de liens externes",
            "Démarrer  ▶","Mes stats 📊","Classement 🏆",
            "Défi du jour","📅 Démarrer le défi du jour",
            "Difficulté","Facile","Moyen","Difficile","Très difficile",
            "500+ liens","120+ liens","40+ liens","10+ liens",
            "🌐 Langue","Appuyer pour changer →","← Retour",
            "Aucun résultat",
            "📅 Quotidien","🟢 Facile","🟡 Moyen","🔴 Difficile","💀 Très difficile","clics"),

        "es" to LangConfig("es","🇪🇸 Wikipedia español","es",
            "Haz clic en los enlaces para llegar a la meta","Cómo jugar",
            "1. Elige la dificultad → se asignan páginas de inicio y meta",
            "2. Solo haz clic en enlaces internos para navegar",
            "3. ¡Llega a la meta en el menor tiempo y clics!",
            "4. Sin retroceso · Sin búsqueda · Sin enlaces externos",
            "Empezar  ▶","Mis stats 📊","Clasificación 🏆",
            "Desafío diario","📅 Empezar desafío diario",
            "Dificultad","Fácil","Medio","Difícil","Muy difícil",
            "500+ enlaces","120+ enlaces","40+ enlaces","10+ enlaces",
            "🌐 Idioma","Toca para cambiar →","← Volver",
            "Sin registros",
            "📅 Diario","🟢 Fácil","🟡 Medio","🔴 Difícil","💀 Muy difícil","clics"),

        "pt" to LangConfig("pt","🇵🇹 Wikipédia português","pt",
            "Clique nos links para chegar à meta","Como jogar",
            "1. Escolha a dificuldade → páginas de início e meta são atribuídas",
            "2. Clique apenas em links internos para navegar",
            "3. Chegue à meta no menor tempo e cliques!",
            "4. Sem voltar · Sem busca · Sem links externos",
            "Começar  ▶","Minhas stats 📊","Ranking 🏆",
            "Desafio diário","📅 Começar desafio diário",
            "Dificuldade","Fácil","Médio","Difícil","Muito difícil",
            "500+ links","120+ links","40+ links","10+ links",
            "🌐 Idioma","Toque para mudar →","← Voltar",
            "Sem registros",
            "📅 Diário","🟢 Fácil","🟡 Médio","🔴 Difícil","💀 Muito difícil","cliques"),

        "it" to LangConfig("it","🇮🇹 Wikipedia italiano","it",
            "Clicca i link per raggiungere l'obiettivo","Come giocare",
            "1. Scegli la difficoltà → pagine di partenza e obiettivo assegnate",
            "2. Clicca solo sui link interni per navigare",
            "3. Raggiungi l'obiettivo nel minor tempo e clic!",
            "4. Niente indietro · Niente ricerca · Niente link esterni",
            "Inizia  ▶","Le mie stats 📊","Classifica 🏆",
            "Sfida giornaliera","📅 Inizia sfida giornaliera",
            "Difficoltà","Facile","Medio","Difficile","Molto difficile",
            "500+ link","120+ link","40+ link","10+ link",
            "🌐 Lingua","Tocca per cambiare →","← Indietro",
            "Nessun risultato",
            "📅 Giornaliero","🟢 Facile","🟡 Medio","🔴 Difficile","💀 Molto difficile","clic")
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var statsPrefs: SharedPreferences
    private lateinit var loadingOverlay: View
    private lateinit var screenIntro: View
    private lateinit var screenDiff: View
    private lateinit var screenRanking: View
    private lateinit var screenStats: View
    private lateinit var lbWikiTabs: LinearLayout
    private lateinit var lbContent: LinearLayout
    private lateinit var lbLoading: View
    private lateinit var statsWikiTabs: LinearLayout
    private lateinit var statsContent: LinearLayout

    private var dailyInfo: ApiClient.GameInfo? = null
    private var pendingChallenge: ApiClient.GameInfo? = null
    private var currentLang = "namu"
    private var lbCurrentWiki = "namu"
    private var lbCurrentDiff = "easy"
    private var statsCurrentWiki = "namu"
    private var statsCurrentDiff = "all"

    private val DIFF_TAB_IDS = mapOf(
        "easy" to R.id.lbTabEasy, "medium" to R.id.lbTabMedium,
        "hard" to R.id.lbTabHard, "very_hard" to R.id.lbTabVeryHard, "daily" to R.id.lbTabDaily
    )
    private val STATS_DIFF_TAB_IDS = mapOf(
        "all" to R.id.statsTabAll,
        "daily" to R.id.statsTabDaily,
        "easy" to R.id.statsTabEasy,
        "medium" to R.id.statsTabMedium,
        "hard" to R.id.statsTabHard,
        "very_hard" to R.id.statsTabVeryHard
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("linkyrun", MODE_PRIVATE)
        statsPrefs = getSharedPreferences("linkyrun_stats", MODE_PRIVATE)
        currentLang = prefs.getString("lang", "namu") ?: "namu"

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        screenIntro = findViewById(R.id.screenIntro)
        screenDiff = findViewById(R.id.screenDifficulty)
        screenRanking = findViewById(R.id.screenRanking)
        screenStats = findViewById(R.id.screenStats)
        lbWikiTabs = findViewById(R.id.lbWikiTabs)
        lbContent = findViewById(R.id.lbContent)
        lbLoading = findViewById(R.id.lbLoading)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        statsWikiTabs = findViewById(R.id.statsWikiTabs)
        statsContent = findViewById(R.id.statsContent)

        MobileAds.initialize(this)
        findViewById<AdView>(R.id.adBanner).loadAd(AdRequest.Builder().build())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    screenDiff.visibility == View.VISIBLE -> showScreen(screenIntro)
                    screenRanking.visibility == View.VISIBLE -> showScreen(screenIntro)
                    screenStats.visibility == View.VISIBLE -> showScreen(screenIntro)
                    else -> finish()
                }
            }
        })

        setupButtons()
        applyLang()
        loadDaily()
        showOnboardingIfNeeded()
        setupGuidePulse()

        // 다시하기로 돌아온 경우 난이도 선택 화면 바로 표시
        if (intent.getBooleanExtra("show_difficulty", false)) {
            showScreen(screenDiff)
        }

        // 딥링크: /go/CODE → 도전장 게임 시작
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val data = intent.data ?: return
        val path = data.path ?: return
        if (!path.startsWith("/go/")) return
        val code = path.removePrefix("/go/").takeIf { it.isNotEmpty() } ?: return
        loadingOverlay.visibility = View.VISIBLE
        thread {
            try {
                val req = okhttp3.Request.Builder()
                    .url("https://linkyrun.com/api/challenge/$code")
                    .build()
                val body = okhttp3.OkHttpClient().newCall(req).execute().body?.string() ?: return@thread
                val j = org.json.JSONObject(body)
                if (j.has("error")) return@thread
                val info = ApiClient.GameInfo(
                    start = j.getString("start"),
                    goal = j.getString("goal"),
                    difficulty = "custom",
                    wiki = j.optString("wiki", "namu")
                )
                runOnUiThread {
                    loadingOverlay.visibility = View.GONE
                    launchGame(info)
                }
            } catch (_: Exception) {
                runOnUiThread { loadingOverlay.visibility = View.GONE }
            }
        }
    }

    private var pulseAnimator: ObjectAnimator? = null

    private fun setupGuidePulse() {
        val btn = findViewById<Button>(R.id.btnGoStart)
        if (!statsPrefs.getBoolean("guide_done", false)) {
            pulseAnimator = ObjectAnimator.ofFloat(btn, "alpha", 1f, 0.4f).apply {
                duration = 800
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 가이드 완료 후 돌아오면 펄스 제거
        if (statsPrefs.getBoolean("guide_done", false) && pulseAnimator != null) {
            pulseAnimator?.cancel()
            findViewById<Button>(R.id.btnGoStart).alpha = 1f
            pulseAnimator = null
        }
    }

    private fun applyLang() {
        val cfg = LANGS[currentLang] ?: LANGS["namu"]!!
        findViewById<TextView>(R.id.tvLangLabel).text = cfg.label
        findViewById<TextView>(R.id.tvSubtitle).text = cfg.subtitle
        findViewById<Button>(R.id.btnGoStart).text = cfg.btnStart
        findViewById<Button>(R.id.btnStats).text = cfg.btnStats
        findViewById<Button>(R.id.btnRanking).text = cfg.btnRanking
        findViewById<TextView>(R.id.tvDailyBadge).text = "📅 ${cfg.dailyBadge}"
        findViewById<TextView>(R.id.tvHowToPlay).text = cfg.howToPlay
        findViewById<TextView>(R.id.tvRule1).text = cfg.rule1
        findViewById<TextView>(R.id.tvRule2).text = cfg.rule2
        findViewById<TextView>(R.id.tvRule3).text = cfg.rule3
        findViewById<TextView>(R.id.tvRule4).text = cfg.rule4
        // 난이도 화면
        findViewById<TextView>(R.id.tvDiffTitle).text = cfg.diffTitle
        findViewById<TextView>(R.id.tvSelectedWiki).text = cfg.label
        findViewById<TextView>(R.id.tvDiffEasyName).text = cfg.diffEasy
        findViewById<TextView>(R.id.tvDiffMediumName).text = cfg.diffMedium
        findViewById<TextView>(R.id.tvDiffHardName).text = cfg.diffHard
        findViewById<TextView>(R.id.tvDiffVeryHardName).text = cfg.diffVeryHard
        findViewById<TextView>(R.id.tvDiffEasyDesc).text = cfg.diffEasyDesc
        findViewById<TextView>(R.id.tvDiffMediumDesc).text = cfg.diffMediumDesc
        findViewById<TextView>(R.id.tvDiffHardDesc).text = cfg.diffHardDesc
        findViewById<TextView>(R.id.tvDiffVeryHardDesc).text = cfg.diffVeryHardDesc
        // 언어 카드
        findViewById<TextView>(R.id.tvLangSettingTitle).text = cfg.langSettingTitle
        findViewById<TextView>(R.id.tvLangSettingHint).text = cfg.langSettingHint
        // 난이도 화면 뒤로 버튼
        findViewById<Button>(R.id.btnDiffBack).text = cfg.btnBack
        // 랭킹 화면 타이틀
        val rankingTitle = when (currentLang) {
            "en" -> "🏆 Ranking"; "ja" -> "🏆 ランキング"; "de" -> "🏆 Rangliste"; "fr" -> "🏆 Classement"
            "es" -> "🏆 Clasificación"; "pt" -> "🏆 Ranking"; "it" -> "🏆 Classifica"; else -> "🏆 랭킹"
        }
        findViewById<TextView>(R.id.tvRankingTitle).text = rankingTitle
        // 랭킹 탭
        findViewById<Button>(R.id.lbTabDaily).text = cfg.rankTabDaily
        findViewById<Button>(R.id.lbTabEasy).text = cfg.rankTabEasy
        findViewById<Button>(R.id.lbTabMedium).text = cfg.rankTabMedium
        findViewById<Button>(R.id.lbTabHard).text = cfg.rankTabHard
        findViewById<Button>(R.id.lbTabVeryHard).text = cfg.rankTabVeryHard
        // 내기록 화면
        val statsLabel = when (currentLang) { "en" -> "My Stats 📊"; "ja" -> "記録 📊"; "de" -> "Meine Stats 📊"; "fr" -> "Mes stats 📊"; "es" -> "Mis stats 📊"; "pt" -> "Minhas stats 📊"; "it" -> "Le mie stats 📊"; else -> "내 기록 📊" }
        val allLabel = when (currentLang) { "en" -> "All"; "ja" -> "全て"; "de" -> "Alle"; "fr" -> "Tout"; "es" -> "Todo"; "pt" -> "Tudo"; "it" -> "Tutto"; else -> "전체" }
        val resetLabel = when (currentLang) { "en" -> "Reset"; "ja" -> "リセット"; "de" -> "Zurücksetzen"; "fr" -> "Réinitialiser"; "es" -> "Restablecer"; "pt" -> "Redefinir"; "it" -> "Ripristina"; else -> "초기화" }
        findViewById<TextView>(R.id.tvStatsTitle).text = statsLabel
        findViewById<Button>(R.id.statsTabAll).text = allLabel
        findViewById<Button>(R.id.statsTabDaily).text = cfg.rankTabDaily
        findViewById<Button>(R.id.statsTabEasy).text = cfg.rankTabEasy
        findViewById<Button>(R.id.statsTabMedium).text = cfg.rankTabMedium
        findViewById<Button>(R.id.statsTabHard).text = cfg.rankTabHard
        findViewById<Button>(R.id.statsTabVeryHard).text = cfg.rankTabVeryHard
        findViewById<Button>(R.id.btnStatsReset).text = resetLabel
    }

    private fun setupButtons() {
        // 언어 선택
        findViewById<View>(R.id.btnLang).setOnClickListener { showLangPicker() }

        // 인트로 화면
        findViewById<Button>(R.id.btnInfo).setOnClickListener { showOnboarding(showDontAsk = false) }
        findViewById<Button>(R.id.btnGoStart).setOnClickListener {
            if (!statsPrefs.getBoolean("guide_done", false)) {
                launchGuideGame()
            } else {
                showScreen(screenDiff)
            }
        }
        findViewById<Button>(R.id.btnStats).setOnClickListener { showStats() }
        findViewById<Button>(R.id.btnRanking).setOnClickListener { showRanking() }
        findViewById<View>(R.id.dailyCard).setOnClickListener { dailyInfo?.let { launchGame(it) } }
        findViewById<Button>(R.id.btnChallengeAccept).setOnClickListener { pendingChallenge?.let { launchGame(it) } }

        // 난이도 화면
        findViewById<Button>(R.id.btnDiffBack).setOnClickListener { showScreen(screenIntro) }
        val currentWiki = { LANGS[currentLang]?.wikiForDaily ?: "namu" }
        findViewById<LinearLayout>(R.id.diffEasy).setOnClickListener { launchRandom("easy", currentWiki()) }
        findViewById<LinearLayout>(R.id.diffMedium).setOnClickListener { launchRandom("medium", currentWiki()) }
        findViewById<LinearLayout>(R.id.diffHard).setOnClickListener { launchRandom("hard", currentWiki()) }
        findViewById<LinearLayout>(R.id.diffVeryHard).setOnClickListener { launchRandom("very_hard", currentWiki()) }

        // 랭킹 화면
        findViewById<Button>(R.id.btnRankingClose).setOnClickListener { showScreen(screenIntro) }
        DIFF_TAB_IDS.forEach { (diff, id) ->
            findViewById<Button>(id).setOnClickListener { switchLbDiff(diff) }
        }

        // 내기록 화면
        findViewById<Button>(R.id.btnStatsClose).setOnClickListener { showScreen(screenIntro) }
        STATS_DIFF_TAB_IDS.forEach { (diff, id) ->
            findViewById<Button>(id).setOnClickListener { switchStatsDiff(diff) }
        }
        findViewById<Button>(R.id.btnStatsReset).setOnClickListener {
            val (confirmTitle, confirmOk, confirmMsg) = when (currentLang) {
                "en" -> arrayOf("Reset Stats", "Reset", "Stats reset!")
                "ja" -> arrayOf("リセット", "リセット", "リセットしました")
                "de" -> arrayOf("Statistiken zurücksetzen", "Zurücksetzen", "Zurückgesetzt!")
                "fr" -> arrayOf("Réinitialiser", "Réinitialiser", "Réinitialisées !")
                "es" -> arrayOf("Restablecer", "Restablecer", "¡Restablecido!")
                "pt" -> arrayOf("Redefinir", "Redefinir", "Redefinido!")
                "it" -> arrayOf("Ripristina", "Ripristina", "Ripristinato!")
                else -> arrayOf("초기화", "초기화", "초기화됐습니다")
            }
            val cancelLabel = when (currentLang) { "en" -> "Cancel"; "ja" -> "キャンセル"; "de" -> "Abbrechen"; "fr" -> "Annuler"; "es" -> "Cancelar"; "pt" -> "Cancelar"; "it" -> "Annulla"; else -> "취소" }
            AlertDialog.Builder(this)
                .setTitle(confirmTitle)
                .setPositiveButton(confirmOk) { _, _ ->
                    statsPrefs.edit().clear().apply()
                    loadStatsContent()
                    Toast.makeText(this, confirmMsg, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(cancelLabel, null)
                .show()
        }
    }

    private fun showScreen(target: View) {
        screenIntro.visibility = View.GONE
        screenDiff.visibility = View.GONE
        screenRanking.visibility = View.GONE
        screenStats.visibility = View.GONE
        target.visibility = View.VISIBLE
    }

    private fun showLangPicker() {
        val langs = LANGS.values.toList()
        val labels = langs.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("언어 / Language")
            .setItems(labels) { _, i ->
                currentLang = langs[i].code
                prefs.edit().putString("lang", currentLang).apply()
                applyLang()
                loadDaily()
            }
            .show()
    }

    private fun loadDaily() {
        val wiki = LANGS[currentLang]?.wikiForDaily ?: "namu"
        findViewById<View>(R.id.dailyCard).visibility = View.GONE
        thread {
            val info = ApiClient.getDaily(wiki)
            runOnUiThread {
                if (info != null) {
                    dailyInfo = info
                    findViewById<TextView>(R.id.tvDailyStart).text = info.start
                    findViewById<TextView>(R.id.tvDailyGoal).text = info.goal
                    findViewById<TextView>(R.id.tvDailyNum).text = info.dayNum?.let { "Day $it" } ?: ""
                    findViewById<View>(R.id.dailyCard).visibility = View.VISIBLE
                }
            }
        }
    }

    private fun launchRandom(difficulty: String, wiki: String) {
        loadingOverlay.visibility = View.VISIBLE
        thread {
            val info = ApiClient.getRandomGame(difficulty, wiki)
            runOnUiThread {
                loadingOverlay.visibility = View.GONE
                if (info == null) {
                    Toast.makeText(this, "게임을 불러오지 못했습니다", Toast.LENGTH_SHORT).show()
                } else {
                    launchGame(info)
                }
            }
        }
    }

    private fun launchGuideGame() {
        val (start, goal, wiki) = when (currentLang) {
            "en" -> Triple("Cat", "Egypt", "en")
            "ja" -> Triple("ネコ", "エジプト", "ja")
            "de" -> Triple("Hauskatze", "Ägypten", "de")
            "fr" -> Triple("Chat", "Égypte", "fr")
            "es" -> Triple("Gato", "Egipto", "es")
            "pt" -> Triple("Gato", "Egito", "pt")
            "it" -> Triple("Gatto", "Egitto", "it")
            "ko" -> Triple("고양이", "이집트", "ko")
            else -> Triple("고양이", "이집트", "namu")
        }
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_START, start)
            putExtra(GameActivity.EXTRA_GOAL, goal)
            putExtra(GameActivity.EXTRA_DIFFICULTY, "guide")
            putExtra(GameActivity.EXTRA_WIKI, wiki)
            putExtra(GameActivity.EXTRA_IS_GUIDE, true)
        }
        startActivity(intent)
    }

    private fun launchGame(info: ApiClient.GameInfo) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_START, info.start)
            putExtra(GameActivity.EXTRA_GOAL, info.goal)
            putExtra(GameActivity.EXTRA_DIFFICULTY, info.difficulty)
            putExtra(GameActivity.EXTRA_WIKI, info.wiki)
            info.dayNum?.let { putExtra(GameActivity.EXTRA_DAY_NUM, it) }
        }
        startActivity(intent)
    }

    private fun showStats() {
        statsCurrentWiki = LANGS[currentLang]?.wikiForDaily ?: "namu"
        statsCurrentDiff = "all"
        buildStatsWikiTabs()
        updateStatsDiffTabColors()
        showScreen(screenStats)
        loadStatsContent()
    }

    private fun buildStatsWikiTabs() {
        statsWikiTabs.removeAllViews()
        val wikiEntries = listOf(
            "namu" to "🌲 나무", "ko" to "🇰🇷 위키",
            "en" to "🇺🇸 EN", "ja" to "🇯🇵 JA",
            "de" to "🇩🇪 DE", "fr" to "🇫🇷 FR",
            "es" to "🇪🇸 ES", "pt" to "🇵🇹 PT", "it" to "🇮🇹 IT"
        )
        val dp = resources.displayMetrics.density
        wikiEntries.forEach { (wiki, label) ->
            val tv = TextView(this).apply {
                text = label
                textSize = 13f
                setPadding((18 * dp).toInt(), 0, (18 * dp).toInt(), 0)
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (wiki == statsCurrentWiki) getColor(R.color.primary) else getColor(R.color.muted))
                setTypeface(null, if (wiki == statsCurrentWiki) Typeface.BOLD else Typeface.NORMAL)
                tag = wiki
                setOnClickListener { switchStatsWiki(wiki) }
            }
            statsWikiTabs.addView(tv)
        }
    }

    private fun switchStatsWiki(wiki: String) {
        statsCurrentWiki = wiki
        for (i in 0 until statsWikiTabs.childCount) {
            (statsWikiTabs.getChildAt(i) as? TextView)?.let { tv ->
                val active = tv.tag == wiki
                tv.setTextColor(if (active) getColor(R.color.primary) else getColor(R.color.muted))
                tv.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        loadStatsContent()
    }

    private fun switchStatsDiff(diff: String) {
        statsCurrentDiff = diff
        updateStatsDiffTabColors()
        loadStatsContent()
    }

    private fun updateStatsDiffTabColors() {
        STATS_DIFF_TAB_IDS.forEach { (diff, id) ->
            findViewById<Button>(id).setTextColor(
                if (diff == statsCurrentDiff) getColor(R.color.primary) else getColor(R.color.muted)
            )
        }
    }

    private fun loadStatsContent() {
        statsContent.removeAllViews()
        val s = when (currentLang) {
            "en" -> arrayOf("My Stats", "Total games", "Wins", "Current streak", "Best streak", "Best time", "Fewest hops", "OK", "Reset", "Stats reset!")
            "ja" -> arrayOf("記録", "合計", "勝利", "連勝中", "最高連勝", "ベストタイム", "最少クリック", "OK", "リセット", "リセットしました")
            "de" -> arrayOf("Meine Stats", "Spiele", "Siege", "Aktuelle Serie", "Beste Serie", "Bestzeit", "Wenigste Klicks", "OK", "Zurücksetzen", "Zurückgesetzt!")
            "fr" -> arrayOf("Mes stats", "Parties", "Victoires", "Série en cours", "Meilleure série", "Meilleur temps", "Moins de clics", "OK", "Réinitialiser", "Réinitialisées !")
            "es" -> arrayOf("Mis stats", "Partidas", "Victorias", "Racha actual", "Mejor racha", "Mejor tiempo", "Menos clics", "OK", "Restablecer", "¡Restablecido!")
            "pt" -> arrayOf("Minhas stats", "Total de jogos", "Vitórias", "Sequência atual", "Melhor sequência", "Melhor tempo", "Menos cliques", "OK", "Redefinir", "Redefinido!")
            "it" -> arrayOf("Le mie stats", "Partite totali", "Vittorie", "Serie attuale", "Miglior serie", "Miglior tempo", "Meno clic", "OK", "Ripristina", "Ripristinato!")
            else -> arrayOf("내 기록", "총 게임", "승리", "현재 연승", "최고 연승", "최고 기록", "최소 이동", "확인", "초기화", "초기화됐습니다")
        }
        val cfg = LANGS[currentLang] ?: LANGS["namu"]!!

        val total: Int; val wins: Int; val streak: Int
        val bestStreak: Int; val bestMs: Long; val bestHops: Int

        if (statsCurrentDiff == "all") {
            val diffs = listOf("daily", "easy", "medium", "hard", "very_hard")
            total = diffs.sumOf { statsPrefs.getInt("s_${statsCurrentWiki}_${it}_total", 0) }
            wins = diffs.sumOf { statsPrefs.getInt("s_${statsCurrentWiki}_${it}_wins", 0) }
            streak = diffs.maxOfOrNull { statsPrefs.getInt("s_${statsCurrentWiki}_${it}_streak", 0) } ?: 0
            bestStreak = diffs.maxOfOrNull { statsPrefs.getInt("s_${statsCurrentWiki}_${it}_bestStreak", 0) } ?: 0
            val msList = diffs.map { statsPrefs.getLong("s_${statsCurrentWiki}_${it}_bestMs", Long.MAX_VALUE) }
            bestMs = msList.filter { it != Long.MAX_VALUE }.minOrNull() ?: -1L
            val hopsList = diffs.map { statsPrefs.getInt("s_${statsCurrentWiki}_${it}_bestHops", Int.MAX_VALUE) }
            bestHops = hopsList.filter { it != Int.MAX_VALUE }.minOrNull() ?: -1
        } else {
            val prefix = "s_${statsCurrentWiki}_${statsCurrentDiff}_"
            total = statsPrefs.getInt("${prefix}total", 0)
            wins = statsPrefs.getInt("${prefix}wins", 0)
            streak = statsPrefs.getInt("${prefix}streak", 0)
            bestStreak = statsPrefs.getInt("${prefix}bestStreak", 0)
            bestMs = statsPrefs.getLong("${prefix}bestMs", -1L)
            bestHops = statsPrefs.getInt("${prefix}bestHops", -1)
        }

        val bestMsStr = if (bestMs > 0) fmtTime(bestMs) else "—"
        val bestHopsStr = if (bestHops > 0) "$bestHops ${cfg.hopsUnit}" else "—"

        val rows = listOf(
            s[1] to total.toString(),
            s[2] to wins.toString(),
            s[3] to streak.toString(),
            s[4] to bestStreak.toString(),
            s[5] to bestMsStr,
            s[6] to bestHopsStr
        )
        rows.forEach { (label, value) -> statsContent.addView(buildStatRow(label, value)) }
    }

    private fun buildStatRow(label: String, value: String): View {
        val dp = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            background = getDrawable(R.drawable.bg_diff_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt()) }
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 14f
                setTextColor(getColor(R.color.muted))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 15f
                setTextColor(getColor(R.color.text))
                setTypeface(null, Typeface.BOLD)
            })
        }
    }

    private fun showRanking() {
        lbCurrentWiki = LANGS[currentLang]?.wikiForDaily ?: "namu"
        lbCurrentDiff = "daily"
        buildWikiTabs()
        updateDiffTabColors()
        showScreen(screenRanking)
        loadRankingEntries()
    }

    private fun buildWikiTabs() {
        lbWikiTabs.removeAllViews()
        val wikiEntries = listOf(
            "namu" to "🌲 나무", "ko" to "🇰🇷 위키",
            "en" to "🇺🇸 EN", "ja" to "🇯🇵 JA",
            "de" to "🇩🇪 DE", "fr" to "🇫🇷 FR",
            "es" to "🇪🇸 ES", "pt" to "🇵🇹 PT", "it" to "🇮🇹 IT"
        )
        val dp = resources.displayMetrics.density
        wikiEntries.forEach { (wiki, label) ->
            val tv = TextView(this).apply {
                text = label
                textSize = 13f
                setPadding((18 * dp).toInt(), 0, (18 * dp).toInt(), 0)
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(if (wiki == lbCurrentWiki) getColor(R.color.primary) else getColor(R.color.muted))
                setTypeface(null, if (wiki == lbCurrentWiki) Typeface.BOLD else Typeface.NORMAL)
                tag = wiki
                setOnClickListener { switchLbWiki(wiki) }
            }
            lbWikiTabs.addView(tv)
        }
    }

    private fun switchLbWiki(wiki: String) {
        lbCurrentWiki = wiki
        for (i in 0 until lbWikiTabs.childCount) {
            (lbWikiTabs.getChildAt(i) as? TextView)?.let { tv ->
                val active = tv.tag == wiki
                tv.setTextColor(if (active) getColor(R.color.primary) else getColor(R.color.muted))
                tv.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        loadRankingEntries()
    }

    private fun switchLbDiff(diff: String) {
        lbCurrentDiff = diff
        updateDiffTabColors()
        loadRankingEntries()
    }

    private fun updateDiffTabColors() {
        DIFF_TAB_IDS.forEach { (diff, id) ->
            findViewById<Button>(id).setTextColor(
                if (diff == lbCurrentDiff) getColor(R.color.primary) else getColor(R.color.muted)
            )
        }
    }

    private fun loadRankingEntries() {
        lbContent.removeAllViews()
        lbLoading.visibility = View.VISIBLE
        val wiki = lbCurrentWiki; val diff = lbCurrentDiff
        thread {
            val entries = ApiClient.getRanking(wiki = wiki, difficulty = diff, limit = 20)
            runOnUiThread {
                lbLoading.visibility = View.GONE
                if (entries.isEmpty()) {
                    lbContent.addView(TextView(this).apply {
                        text = (LANGS[currentLang] ?: LANGS["namu"]!!).rankEmpty
                        textSize = 14f
                        setTextColor(getColor(R.color.muted))
                        gravity = Gravity.CENTER
                        setPadding(0, 80, 0, 80)
                    })
                    return@runOnUiThread
                }
                entries.forEach { entry -> lbContent.addView(buildRankEntry(entry)) }
            }
        }
    }

    private fun buildRankEntry(entry: ApiClient.RankEntry): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            background = getDrawable(R.drawable.bg_diff_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins((12 * dp).toInt(), (3 * dp).toInt(), (12 * dp).toInt(), (3 * dp).toInt()) }
        }
        // 메달/순위
        val medal = when (entry.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${entry.rank}" }
        row.addView(TextView(this).apply {
            text = medal
            textSize = if (entry.rank <= 3) 22f else 13f
            setTextColor(getColor(R.color.muted))
            gravity = Gravity.CENTER
            minWidth = (44 * dp).toInt()
        })
        // 중앙 컨텐츠
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding((10 * dp).toInt(), 0, 0, 0)
        }
        // 첫 줄: 닉네임 + 시간/홉
        center.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = entry.nickname
                textSize = 14f
                setTextColor(getColor(R.color.text))
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(this@MainActivity).apply {
                text = "⏱ ${fmtTime(entry.elapsedMs)}  🔗 ${entry.hops} ${(LANGS[currentLang] ?: LANGS["namu"]!!).hopsUnit}"
                textSize = 12f
                setTextColor(getColor(R.color.primary))
            })
        })
        // 둘째 줄: 시작 → (중간 생략) → 끝 — 시작·끝은 항상 표시
        val routeStart = if (entry.path.size >= 2) entry.path.first() else entry.start
        val routeEnd   = if (entry.path.size >= 2) entry.path.last()  else entry.goal
        val routeMid   = when {
            entry.path.size >= 4 -> " ··· "
            entry.path.size == 3 -> " → ${entry.path[1]} → "
            else -> " → "
        }
        if (routeStart.isNotEmpty() || routeEnd.isNotEmpty()) {
            center.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (3 * dp).toInt(), 0, 0)
                addView(TextView(this@MainActivity).apply {
                    text = routeStart
                    textSize = 12f
                    setTextColor(getColor(R.color.muted))
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(this@MainActivity).apply {
                    text = routeMid
                    textSize = 12f
                    setTextColor(getColor(R.color.muted))
                })
                addView(TextView(this@MainActivity).apply {
                    text = routeEnd
                    textSize = 12f
                    setTextColor(getColor(R.color.muted))
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            })
        }
        row.addView(center)
        // 랭킹 항목 클릭 → 같은 출발/목표로 도전
        row.setOnClickListener {
            val start = entry.start.ifEmpty { entry.path.firstOrNull() ?: return@setOnClickListener }
            val goal = entry.goal.ifEmpty { entry.path.lastOrNull() ?: return@setOnClickListener }
            val cfg = LANGS[currentLang] ?: LANGS["namu"]!!
            val record = "⏱ ${fmtTime(entry.elapsedMs)}  🔗 ${entry.hops} ${cfg.hopsUnit}  (${entry.nickname})"
            AlertDialog.Builder(this)
                .setTitle("도전하기")
                .setMessage("$start → $goal\n\n$record")
                .setPositiveButton("도전 시작 ▶") { _, _ ->
                    launchGame(ApiClient.GameInfo(start, goal, "custom", lbCurrentWiki))
                }
                .setNegativeButton("취소", null)
                .show()
        }
        return row
    }

    private fun showOnboardingIfNeeded() {
        if (prefs.getBoolean("onboarding_done", false)) return
        if (statsPrefs.getBoolean("guide_done", false)) return
        showOnboarding(showDontAsk = true)
    }

    private fun showOnboarding(showDontAsk: Boolean = true) {
        val cfg = LANGS[currentLang] ?: LANGS["namu"]!!
        val rules = "${cfg.rule1}\n\n${cfg.rule2}\n\n${cfg.rule3}\n\n${cfg.rule4}"
        val (title, extra, btnOk, btnSkip) = when (currentLang) {
            "en" -> arrayOf(
                "🔗 Welcome to Linky Run!",
                "Change the language to play with different Wikipedia editions!",
                "Let's go!", "Don't show again"
            )
            "ja" -> arrayOf(
                "🔗 Linky Run へようこそ！",
                "言語を変えて各国のウィキペディアで遊べます！",
                "はじめる！", "表示しない"
            )
            "de" -> arrayOf(
                "🔗 Willkommen bei Linky Run!",
                "Wechsle die Sprache für verschiedene Wikipedia-Versionen!",
                "Los geht's!", "Nicht mehr zeigen"
            )
            "fr" -> arrayOf(
                "🔗 Bienvenue sur Linky Run !",
                "Changez la langue pour jouer avec différentes Wikipédia !",
                "C'est parti !", "Ne plus afficher"
            )
            "es" -> arrayOf(
                "🔗 ¡Bienvenido a Linky Run!",
                "¡Cambia el idioma para jugar con diferentes ediciones de Wikipedia!",
                "¡Vamos!", "No mostrar de nuevo"
            )
            "pt" -> arrayOf(
                "🔗 Bem-vindo ao Linky Run!",
                "Mude o idioma para jogar com diferentes edições da Wikipédia!",
                "Vamos!", "Não mostrar novamente"
            )
            "it" -> arrayOf(
                "🔗 Benvenuto su Linky Run!",
                "Cambia la lingua per giocare con diverse edizioni di Wikipedia!",
                "Iniziamo!", "Non mostrare più"
            )
            else -> arrayOf(
                "🔗 Linky Run 에 오신 걸 환영해요!",
                "언어를 바꾸면 해당 나라의 위키피디아로 게임을 즐길 수 있어요.",
                "시작하기!", "다시 보지 않기"
            )
        }
        val example = when (currentLang) {
            "en" -> "e.g. Navigate from 'Cat' to 'Egypt' by clicking links only"
            "ja" -> "例：「ネコ」から「エジプト」までリンクだけをクリックして移動"
            "de" -> "z.B. Navigiere von 'Hauskatze' zu 'Ägypten' nur durch Klicken auf Links"
            "fr" -> "ex : Naviguez de 'Chat' à 'Égypte' en cliquant uniquement sur des liens"
            "es" -> "ej: Navega de 'Gato' a 'Egipto' haciendo clic solo en enlaces"
            "pt" -> "ex: Navegue de 'Gato' a 'Egito' clicando apenas em links"
            "it" -> "es: Naviga da 'Gatto' a 'Egitto' cliccando solo sui link"
            else -> "예: '고양이' 페이지에서 '이집트' 페이지까지 링크만 클릭해서 이동"
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("📖 ${cfg.howToPlay}\n\n$rules\n\n$extra\n\n$example")
            .setPositiveButton(btnOk, null)  // OK: 다시 보지 않기 없이 그냥 닫기
            .setCancelable(false)
        if (showDontAsk) {
            builder.setNegativeButton(btnSkip) { _, _ ->
                prefs.edit().putBoolean("onboarding_done", true).apply()
            }
        }
        builder.show()
    }

    private fun fmtTime(ms: Long): String {
        val s = ms / 1000; val m = s / 60; val ss = s % 60
        return if (m > 0) "%02d:%02d".format(m, ss) else "${ss}초"
    }

}
