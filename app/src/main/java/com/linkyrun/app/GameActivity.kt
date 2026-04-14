package com.linkyrun.app

import android.annotation.SuppressLint
import android.content.*
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.concurrent.thread

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_START = "start"
        const val EXTRA_GOAL = "goal"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_WIKI = "wiki"
        const val EXTRA_DAY_NUM = "day_num"
        const val EXTRA_IS_GUIDE = "is_guide"

        private val NAMU_PREFIXES = listOf(
            "https://namu.wiki/w/",
            "http://namu.wiki/w/",
            "https://m.namu.wiki/w/",
            "http://m.namu.wiki/w/"
        )
        private val WIKI_HOSTS = mapOf(
            "en" to Pair("en.wikipedia.org", "/wiki/"),
            "ko" to Pair("ko.wikipedia.org", "/wiki/"),
            "ja" to Pair("ja.wikipedia.org", "/wiki/"),
            "de" to Pair("de.wikipedia.org", "/wiki/"),
            "fr" to Pair("fr.wikipedia.org", "/wiki/"),
            "es" to Pair("es.wikipedia.org", "/wiki/"),
            "pt" to Pair("pt.wikipedia.org", "/wiki/"),
            "it" to Pair("it.wikipedia.org", "/wiki/")
        )

        // namu.wiki에 주입할 JS: 링크 클릭 인터셉트 + 헤더 숨김
        private val NAMU_INJECT_JS = """
            (function(){
                if (window.__linkyInjected) return;
                window.__linkyInjected = true;

                // namu.wiki 자체 헤더/검색 숨김
                var style = document.createElement('style');
                style.textContent = 'header{display:none!important}body,#app>div{padding-top:0!important;margin-top:0!important}[class*=search]{display:none!important}[id*=search]{display:none!important}input[type=search]{display:none!important}';
                (document.head||document.documentElement).appendChild(style);

                function hideNamuHeader(){
                    try{
                        document.querySelectorAll('header').forEach(function(el){
                            el.style.setProperty('display','none','important');
                        });
                        var body=document.body;
                        if(body){Array.from(body.children).forEach(function(el){
                            var s=window.getComputedStyle(el);
                            if(s.position==='fixed'||s.position==='sticky'){
                                el.style.setProperty('display','none','important');
                            }
                        });}
                    }catch(e){}
                }
                hideNamuHeader();
                setTimeout(hideNamuHeader,300);
                setTimeout(hideNamuHeader,1000);
                new MutationObserver(function(){hideNamuHeader();}).observe(
                    document.documentElement,{childList:true,subtree:true});

                // 링크 클릭 인터셉트
                document.addEventListener('click', function(e){
                    var a = e.target.closest('a');
                    if (!a || !a.href) return;
                    var href = a.href;
                    if (/namu\.wiki\/w\//.test(href)) {
                        e.preventDefault();
                        e.stopPropagation();
                        Android.onLinkClick(href);
                    }
                }, true);
            })();
        """.trimIndent()

        private val WIKI_INJECT_JS = """
            (function(){
                if (window.__linkyInjected) return;
                window.__linkyInjected = true;

                // 위키피디아 검색 UI 숨김
                var style = document.createElement('style');
                style.textContent = '#searchInput,#searchButton,.search-toggle,#p-search,.minerva-search-form,.header-search,input[name=search],.search-box,#searchform,.search-container{display:none!important}';
                (document.head||document.documentElement).appendChild(style);

                document.addEventListener('click', function(e){
                    var a = e.target.closest('a');
                    if (!a || !a.href) return;
                    var href = a.href;
                    if (/wikipedia\.org\/wiki\//.test(href) && !/Special:/.test(href) && !/Wikipedia:/.test(href) && !/Help:/.test(href)) {
                        e.preventDefault();
                        e.stopPropagation();
                        Android.onLinkClick(href);
                    }
                }, true);
            })();
        """.trimIndent()
    }

    private lateinit var webView: WebView
    private lateinit var tvTimer: TextView
    private lateinit var tvHops: TextView
    private lateinit var tvGoal: TextView
    private lateinit var tvGoalLabel: TextView
    private lateinit var btnGiveUp: Button
    private lateinit var btnReadPage: Button
    private lateinit var victoryOverlay: FrameLayout
    private lateinit var tvVictoryTitle: TextView
    private lateinit var tvVictoryTimeLabel: TextView
    private lateinit var tvVictoryHopsLabel: TextView
    private lateinit var tvVictoryPathLabel: TextView
    private lateinit var tvVictoryTime: TextView
    private lateinit var tvVictoryHops: TextView
    private lateinit var tvVictoryPath: TextView
    private lateinit var tvRankTitle: TextView
    private lateinit var btnSubmitRank: Button
    private lateinit var etNickname: EditText
    private lateinit var tvRankResult: TextView
    private lateinit var btnPlayAgain: Button
    private lateinit var btnGoHome: Button
    private lateinit var btnShare: Button
    private lateinit var btnChallenge: Button
    private lateinit var rankSection: View
    private lateinit var hud: View
    private lateinit var pathPanel: View
    private lateinit var pathContent: LinearLayout
    private lateinit var hudCenter: View
    private lateinit var pageLoadingOverlay: View

    private var interstitialAd: InterstitialAd? = null
    private var lastProgrammaticUrl: String? = null
    private var pendingVictory = false
    private var gameCompleted = false
    private var isGuide = false
    private lateinit var gameState: GameState
    private lateinit var statsPrefs: SharedPreferences

    private val gameLang: String by lazy {
        getSharedPreferences("linkyrun", MODE_PRIVATE).getString("lang", "namu") ?: "namu"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (gameState.active) {
                updateTimer()
                handler.postDelayed(this, 50)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_game)

        statsPrefs = getSharedPreferences("linkyrun_stats", MODE_PRIVATE)

        val start = intent.getStringExtra(EXTRA_START) ?: run { finish(); return }
        val goal = intent.getStringExtra(EXTRA_GOAL) ?: run { finish(); return }
        val difficulty = intent.getStringExtra(EXTRA_DIFFICULTY) ?: "easy"
        val wiki = intent.getStringExtra(EXTRA_WIKI) ?: "namu"
        val dayNum = if (intent.hasExtra(EXTRA_DAY_NUM)) intent.getIntExtra(EXTRA_DAY_NUM, 0) else null
        isGuide = intent.getBooleanExtra(EXTRA_IS_GUIDE, false)

        gameState = GameState(
            start = start, goal = goal,
            difficulty = difficulty, wiki = wiki, dayNum = dayNum
        ).also { it.path.add(start) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    victoryOverlay.visibility == View.VISIBLE -> finish()
                    gameCompleted -> finish()
                    pathPanel.visibility == View.VISIBLE -> pathPanel.visibility = View.GONE
                    else -> showGiveUpDialog()
                }
            }
        })

        bindViews()
        applyGameLang()
        setupInsets()
        setupWebView()
        setupButtons()
        syncHUD()
        loadInterstitialAd()

        webView.loadUrl(buildWikiUrl(start, wiki))
        showStartPopup()

        if (isGuide) {
            showGuideHint()
        }
    }

    private fun buildWikiUrl(title: String, wiki: String): String {
        val enc = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        return when (wiki) {
            "namu" -> "https://namu.wiki/w/$enc"
            else -> {
                val host = WIKI_HOSTS[wiki]?.first ?: "en.wikipedia.org"
                "https://$host/wiki/$enc"
            }
        }
    }

    private fun bindViews() {
        webView              = findViewById(R.id.webView)
        tvTimer              = findViewById(R.id.tvTimer)
        tvHops               = findViewById(R.id.tvHops)
        tvGoal               = findViewById(R.id.tvGoal)
        tvGoalLabel          = findViewById(R.id.tvGoalLabel)
        btnGiveUp            = findViewById(R.id.btnGiveUp)
        btnReadPage          = findViewById(R.id.btnReadPage)
        victoryOverlay       = findViewById(R.id.victoryOverlay)
        tvVictoryTitle       = findViewById(R.id.tvVictoryTitle)
        tvVictoryTimeLabel   = findViewById(R.id.tvVictoryTimeLabel)
        tvVictoryHopsLabel   = findViewById(R.id.tvVictoryHopsLabel)
        tvVictoryPathLabel   = findViewById(R.id.tvVictoryPathLabel)
        tvVictoryTime        = findViewById(R.id.tvVictoryTime)
        tvVictoryHops        = findViewById(R.id.tvVictoryHops)
        tvVictoryPath        = findViewById(R.id.tvVictoryPath)
        tvRankTitle          = findViewById(R.id.tvRankTitle)
        btnSubmitRank        = findViewById(R.id.btnSubmitRank)
        etNickname           = findViewById(R.id.etNickname)
        tvRankResult         = findViewById(R.id.tvRankResult)
        btnPlayAgain         = findViewById(R.id.btnPlayAgain)
        btnGoHome            = findViewById(R.id.btnGoHome)
        btnShare             = findViewById(R.id.btnShare)
        btnChallenge         = findViewById(R.id.btnChallenge)
        rankSection          = findViewById(R.id.rankSection)
        hud                  = findViewById(R.id.hud)
        pathPanel            = findViewById(R.id.pathPanel)
        pathContent          = findViewById(R.id.pathContent)
        hudCenter            = findViewById(R.id.hudCenter)
        pageLoadingOverlay   = findViewById(R.id.pageLoadingOverlay)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(hud) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top + 8, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(victoryOverlay) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }
        hud.post {
            val lp = webView.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = hud.height
            webView.requestLayout()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        // ★ JavascriptInterface: JS → Kotlin 링크 콜백
        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun onLinkClick(url: String) {
                runOnUiThread { handleNavigation(url) }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                // 게임 시작 후 이동 시에만 로딩 표시 (초기 페이지 로드 제외)
                if (gameState.hops > 0 || pendingVictory) {
                    pageLoadingOverlay.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageLoadingOverlay.visibility = View.GONE
                // 뒤로가기는 OnBackPressedCallback에서 처리하므로 clearHistory 불필요

                // ★ 페이지 로드 완료마다 JS 재주입 (SPA 네비게이션 대응)
                val js = if (gameState.wiki == "namu") NAMU_INJECT_JS else WIKI_INJECT_JS
                view.evaluateJavascript(js, null)

                // 목적지 도달 후 페이지 로드 완료 → 빅토리 오버레이 표시
                if (pendingVictory) {
                    pendingVictory = false
                    showVictory()
                    return
                }

                // 리다이렉트 후 최종 URL로 목표 확인
                val title = extractTitle(url, gameState.wiki)
                if (title != null && gameState.active && gameState.hops > 0 && isGoal(title, gameState.goal)) {
                    handler.removeCallbacks(timerRunnable)
                    gameState.elapsed = System.currentTimeMillis() - gameState.startTime
                    gameState.active = false
                    updatePersonalStats()
                    showVictory()
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                // 검색 URL 차단
                if (url.contains("/search") || url.contains("Special:Search") || url.contains("action=search") || url.contains("search=")) {
                    return true
                }
                if (gameState.wiki == "namu") return false  // namu: JS injection이 처리
                // 우리가 직접 loadUrl한 URL이면 hop 중복 카운트 없이 허용
                if (url == lastProgrammaticUrl) {
                    lastProgrammaticUrl = null
                    return false
                }
                // JS 미주입 상태에서 사용자가 위키 링크 클릭한 경우 (fallback)
                val wikiInfo = WIKI_HOSTS[gameState.wiki] ?: return true
                return if (url.contains(wikiInfo.first) && url.contains(wikiInfo.second) &&
                    !url.contains("Special:") && !url.contains("Wikipedia:") && !url.contains("Help:")) {
                    handleNavigation(url)
                } else {
                    true  // 외부 링크 차단
                }
            }
        }
    }

    private fun handleNavigation(url: String): Boolean {
        if (!gameState.active) return true
        val title = extractTitle(url, gameState.wiki) ?: return false

        gameState.hops++
        gameState.path.add(title)
        syncHUD()

        if (isGoal(title, gameState.goal)) {
            // 타이머 정지 + 기록 저장 후, 목적 페이지로 이동하고 onPageFinished에서 빅토리 표시
            handler.removeCallbacks(timerRunnable)
            gameState.elapsed = System.currentTimeMillis() - gameState.startTime
            gameState.active = false
            updatePersonalStats()
            pendingVictory = true
            if (gameState.wiki != "namu") lastProgrammaticUrl = url
            webView.loadUrl(url)
            return true
        }

        // JS가 preventDefault했으므로 직접 이동 (namu + Wikipedia 공통)
        if (gameState.wiki != "namu") lastProgrammaticUrl = url
        webView.loadUrl(url)
        return true
    }

    private fun extractTitle(url: String, wiki: String): String? {
        return when (wiki) {
            "namu" -> NAMU_PREFIXES.firstNotNullOfOrNull { prefix ->
                if (url.startsWith(prefix)) {
                    val raw = url.removePrefix(prefix).split("?")[0].split("#")[0]
                    try { URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
                } else null
            }
            else -> {
                val info = WIKI_HOSTS[wiki] ?: return null
                if (url.contains(info.first) && url.contains(info.second)) {
                    val idx = url.indexOf(info.second)
                    if (idx >= 0) {
                        val raw = url.substring(idx + info.second.length).split("?")[0].split("#")[0]
                        try { URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
                    } else null
                } else null
            }
        }
    }

    private fun isGoal(title: String, goal: String): Boolean {
        val nt = normalize(title); val ng = normalize(goal)
        // Layer 1: 직접 일치
        if (nt == ng) return true
        // Layer 2: 괄호 제거 후 비교 (동음이의어 구분자)
        val st = stripParen(nt); val sg = stripParen(ng)
        if (st.isNotEmpty() && sg.isNotEmpty() && st == sg) return true
        // Layer 3: 유사도 매칭 (0.92 이상, 양쪽 모두 6자 이상 — 서버와 동일)
        if (nt.length >= 6 && ng.length >= 6 && similarity(nt, ng) >= 0.92) return true
        return false
    }

    private fun normalize(s: String) = s.replace("_", " ").trim().lowercase()
    private fun stripParen(s: String) = s.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()
    private fun similarity(a: String, b: String): Double {
        val m = a.length; val n = b.length
        if (m == 0 || n == 0) return 0.0
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) for (j in 1..n)
            dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1] + 1 else maxOf(dp[i-1][j], dp[i][j-1])
        return 2.0 * dp[m][n] / (m + n)
    }

    private fun syncHUD() {
        runOnUiThread {
            tvHops.text = gameState.hops.toString()
            tvGoal.text = gameState.goal
            if (pathPanel.visibility == View.VISIBLE) renderPathPanel()
        }
    }

    private fun updateTimer() {
        val ms = System.currentTimeMillis() - gameState.startTime
        tvTimer.text = fmtTime(ms)
    }

    private fun fmtTime(ms: Long): String {
        val s = ms / 1000; val m = s / 60; val ss = s % 60; val cs = (ms % 1000) / 10
        return if (m > 0) "%02d:%02d".format(m, ss) else "%02d.%02d".format(ss, cs)
    }

    // ── 게임 화면 다국어 문자열 (중앙 관리) ──────────────────────
    private data class GameStrings(
        val goalLabel: String, val giveUp: String, val victoryTitle: String,
        val timeLabel: String, val hopsLabel: String, val pathLabel: String,
        val rankTitle: String, val nickHint: String, val submit: String,
        val playAgain: String, val home: String, val readPage: String,
        val hopsUnit: String, val close: String,
        val popupTitle: String, val popupGoalLabel: String, val popupRulesLabel: String,
        val popupRules: String, val popupStart: String,
        // 포기 다이얼로그
        val giveUpQ: String, val giveUpYes: String, val giveUpGoal: String, val giveUpKeep: String,
        // 제출/결과
        val nickError: String, val submitting: String, val rankOk: String, val rankFail: String,
        // 도전장/가이드
        val challengeCopied: String, val guideToast: String,
        val guideHint: (String) -> String
    )

    private val GAME_STRINGS = mapOf(
        "en" to GameStrings("GOAL ▾","Quit","🎉  Arrived!","TIME","HOPS","PATH","Submit Score","Enter nickname","Submit","Play Again","Home","📖 Read","hops","Close",
            "Ready?","Goal:","Rules","1. Click internal links only to navigate\n2. Reach the goal in fewest hops!\n3. No back button · No search · No external links","Start!",
            "Give up?","Give Up","Go to Goal Page","Keep Playing","Enter nickname","Submitting…","Submitted!","Submission failed","Challenge copied!","First clear! Now play freely 🎉",{g->"Find and tap the '$g' link! 👉"}),
        "ja" to GameStrings("ゴール ▾","やめる","🎉  ゴール！","タイム","クリック","ルート","記録登録","ニックネームを入力","登録","もう一回","ホーム","📖 読む","クリック","閉じる",
            "準備はいいですか？","ゴール:","ルール","1. 内部リンクのみクリック\n2. 最少クリックでゴール！\n3. 戻るボタン・検索・外部リンク禁止","スタート！",
            "ギブアップしますか？","ギブアップ","ゴールページへ","続ける","ニックネーム入力","登録中…","登録完了！","登録失敗","コピーしました！","初クリア！自由にプレイしよう 🎉",{g->"「$g」リンクを探してタップ！ 👉"}),
        "de" to GameStrings("ZIEL ▾","Aufgeben","🎉  Geschafft!","ZEIT","KLICKS","ROUTE","Rangliste","Name eingeben","Eintragen","Nochmal","Start","📖 Lesen","Klicks","Schließen",
            "Bereit?","Zielseite:","Regeln","1. Nur interne Links anklicken\n2. Ziel in wenigsten Klicks!\n3. Kein Zurück · Keine Suche · Keine externen Links","Start!",
            "Aufgeben?","Aufgeben","Zur Zielseite","Weiterspielen","Name eingeben","Wird gesendet…","Eingetragen!","Fehler","Kopiert!","Erster Sieg! Jetzt frei spielen 🎉",{g->"Finde und tippe auf '$g'! 👉"}),
        "fr" to GameStrings("BUT ▾","Abandonner","🎉  Arrivé !","TEMPS","CLICS","CHEMIN","Classement","Entrer un pseudo","Soumettre","Rejouer","Accueil","📖 Lire","clics","Fermer",
            "Prêt(e) ?","Page cible :","Règles","1. Cliquer uniquement les liens internes\n2. Atteindre le but en moins de clics !\n3. Pas de retour · Pas de recherche · Pas de liens externes","C'est parti !",
            "Abandonner ?","Abandonner","Aller à la cible","Continuer","Entrer un pseudo","Envoi…","Envoyé !","Échec","Copié !","Premier succès ! Jouez librement 🎉",{g->"Trouvez et appuyez sur '$g' ! 👉"}),
        "es" to GameStrings("META ▾","Rendirse","🎉  ¡Llegaste!","TIEMPO","CLICS","RUTA","Enviar puntuación","Ingresa apodo","Enviar","Otra vez","Inicio","📖 Leer","clics","Cerrar",
            "¿Listo?","Página objetivo:","Reglas","1. Solo haz clic en enlaces internos\n2. ¡Llega a la meta en menos clics!\n3. Sin retroceso · Sin búsqueda · Sin enlaces externos","¡Empezar!",
            "¿Rendirse?","Rendirse","Ir a la página objetivo","Seguir jugando","Ingresa apodo","Enviando…","¡Enviado!","Error","¡Copiado!","¡Primera victoria! Juega libremente 🎉",{g->"¡Busca y toca '$g'! 👉"}),
        "pt" to GameStrings("META ▾","Desistir","🎉  Chegou!","TEMPO","CLIQUES","ROTA","Enviar pontuação","Digite apelido","Enviar","Jogar de novo","Início","📖 Ler","cliques","Fechar",
            "Pronto?","Página alvo:","Regras","1. Clique apenas em links internos\n2. Chegue ao destino com menos cliques!\n3. Sem voltar · Sem busca · Sem links externos","Começar!",
            "Desistir?","Desistir","Ir para a página alvo","Continuar jogando","Digite apelido","Enviando…","Enviado!","Erro","Copiado!","Primeira vitória! Jogue livremente 🎉",{g->"Encontre e toque '$g'! 👉"}),
        "it" to GameStrings("META ▾","Arrendersi","🎉  Arrivato!","TEMPO","CLIC","PERCORSO","Invia punteggio","Inserisci nickname","Invia","Rigioca","Home","📖 Leggi","clic","Chiudi",
            "Pronto?","Pagina obiettivo:","Regole","1. Clicca solo sui link interni\n2. Raggiungi l'obiettivo con meno clic!\n3. Niente indietro · Niente ricerca · Niente link esterni","Inizia!",
            "Arrendersi?","Arrendersi","Vai alla pagina obiettivo","Continua a giocare","Inserisci nickname","Invio…","Inviato!","Errore","Copiato!","Prima vittoria! Gioca liberamente 🎉",{g->"Trova e tocca '$g'! 👉"}),
    )
    private val DEFAULT_STRINGS = GameStrings("목표 ▾","포기","🎉  도착!","시간","이동","경로","랭킹 등록","닉네임 입력","랭킹 등록","다시 하기","홈으로","📖 읽기","홉","닫기",
        "준비됐나요?","목적 페이지:","게임 방법","1. 내부 링크만 클릭해서 목표 페이지까지 이동\n2. 최단 시간 · 최소 클릭으로 도달하면 승리!\n3. 뒤로 가기 · 검색 · 외부 링크 사용 금지","시작!",
        "게임을 포기할까요?","포기","목적 페이지로 이동","계속하기","닉네임 입력","등록 중…","등록 완료!","등록 실패","도전장이 복사됐어요!","첫 클리어! 이제 자유롭게 플레이하세요 🎉",{g->"'$g' 링크를 찾아서 눌러보세요! 👉"})

    private fun gs(): GameStrings = GAME_STRINGS[gameLang] ?: DEFAULT_STRINGS

    private fun applyGameLang() {
        val s = gs()
        tvGoalLabel.text = s.goalLabel
        btnGiveUp.text = s.giveUp
        tvVictoryTitle.text = s.victoryTitle
        tvVictoryTimeLabel.text = s.timeLabel
        tvVictoryHopsLabel.text = s.hopsLabel
        tvVictoryPathLabel.text = s.pathLabel
        tvRankTitle.text = s.rankTitle
        etNickname.hint = s.nickHint
        btnSubmitRank.text = s.submit
        btnPlayAgain.text = s.playAgain
        btnGoHome.text = s.home
        btnReadPage.text = s.readPage
    }

    private fun getHopsUnit(): String = gs().hopsUnit
    private fun getCloseText(): String = gs().close
    private fun getReadPageText(): String = gs().readPage

    private fun showStartPopup() {
        val s = gs()
        AlertDialog.Builder(this)
            .setTitle(s.popupTitle)
            .setMessage("${s.popupGoalLabel}  ${gameState.goal}\n\n${s.popupRulesLabel}\n${s.popupRules}")
            .setPositiveButton(s.popupStart) { _, _ ->
                gameState.startTime = System.currentTimeMillis()
                handler.post(timerRunnable)
            }
            .setCancelable(false)
            .show()
    }

    private fun showVictory() {
        if (victoryOverlay.visibility == View.VISIBLE) return
        // pendingVictory 경로: active=false, elapsed 이미 설정됨
        // 리다이렉트 직접 감지 경로: active=true인 경우 여기서 처리
        if (gameState.active) {
            handler.removeCallbacks(timerRunnable)
            gameState.elapsed = System.currentTimeMillis() - gameState.startTime
            gameState.active = false
            updatePersonalStats()
        }

        tvVictoryTime.text = fmtTime(gameState.elapsed)
        tvVictoryHops.text = "${gameState.hops} ${getHopsUnit()}"
        tvTimer.text = fmtTime(gameState.elapsed)
        btnReadPage.text = getReadPageText()

        tvVictoryPath.text = gameState.path.joinToString(" → ")

        if (gameState.difficulty == "custom" || isGuide) {
            rankSection.visibility = View.GONE
        }

        if (isGuide) {
            guideBanner?.visibility = View.GONE
            statsPrefs.edit().putBoolean("guide_done", true).apply()
            Toast.makeText(this, gs().guideToast, Toast.LENGTH_LONG).show()
        }

        // HUD 아래 영역에서 중앙 정렬
        hud.post {
            val lp = victoryOverlay.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = hud.height
            victoryOverlay.requestLayout()
        }

        victoryOverlay.visibility = View.VISIBLE
    }

    private fun updatePersonalStats() {
        if (isGuide) return  // 가이드 게임 기록은 저장하지 않음
        // 전체 통계
        val wins = statsPrefs.getInt("wins", 0) + 1
        val total = statsPrefs.getInt("totalGames", 0) + 1
        val streak = statsPrefs.getInt("streak", 0) + 1
        val bestStreak = maxOf(statsPrefs.getInt("bestStreak", 0), streak)
        val prevBestTime = statsPrefs.getLong("bestTime", Long.MAX_VALUE)
        val bestTime = if (gameState.elapsed < prevBestTime) gameState.elapsed else prevBestTime
        val prevBestHops = statsPrefs.getInt("bestHops", Int.MAX_VALUE)
        val bestHops = if (gameState.hops < prevBestHops) gameState.hops else prevBestHops

        // 위키+난이도별 통계
        val prefix = "s_${gameState.wiki}_${gameState.difficulty}_"
        val cTotal = statsPrefs.getInt("${prefix}total", 0) + 1
        val cWins = statsPrefs.getInt("${prefix}wins", 0) + 1
        val cStreak = statsPrefs.getInt("${prefix}streak", 0) + 1
        val cBestStreak = maxOf(statsPrefs.getInt("${prefix}bestStreak", 0), cStreak)
        val cPrevBestMs = statsPrefs.getLong("${prefix}bestMs", Long.MAX_VALUE)
        val cBestMs = if (gameState.elapsed < cPrevBestMs) gameState.elapsed else cPrevBestMs
        val cPrevBestHops = statsPrefs.getInt("${prefix}bestHops", Int.MAX_VALUE)
        val cBestHops = if (gameState.hops < cPrevBestHops) gameState.hops else cPrevBestHops

        statsPrefs.edit()
            .putInt("wins", wins).putInt("totalGames", total)
            .putInt("streak", streak).putInt("bestStreak", bestStreak)
            .putLong("bestTime", bestTime).putInt("bestHops", bestHops)
            .putInt("${prefix}total", cTotal).putInt("${prefix}wins", cWins)
            .putInt("${prefix}streak", cStreak).putInt("${prefix}bestStreak", cBestStreak)
            .putLong("${prefix}bestMs", cBestMs).putInt("${prefix}bestHops", cBestHops)
            .apply()
    }

    private fun setupButtons() {
        hudCenter.setOnClickListener { togglePathPanel() }
        btnGiveUp.setOnClickListener {
            if (gameCompleted) finish() else showGiveUpDialog()
        }

        btnReadPage.setOnClickListener {
            victoryOverlay.visibility = View.GONE
            gameCompleted = true
            btnGiveUp.text = getCloseText()
        }

        // 키패드가 올라오면 화면을 밀어올려서 닉네임 입력부 노출
        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            if (keypadHeight > screenHeight * 0.15) {
                victoryOverlay.translationY = -keypadHeight.toFloat() * 0.5f
            } else {
                victoryOverlay.translationY = 0f
            }
        }

        btnSubmitRank.setOnClickListener {
            val nick = etNickname.text.toString().trim()
            if (nick.isEmpty()) { etNickname.error = gs().nickError; return@setOnClickListener }
            btnSubmitRank.isEnabled = false; btnSubmitRank.text = gs().submitting
            val gs = gameState
            thread {
                val (ok, rank) = ApiClient.submitRanking(
                    nick, gs.start, gs.goal, gs.elapsed, gs.hops, gs.path, gs.difficulty, gs.wiki, gs.dayNum)
                runOnUiThread {
                    // 키보드 숨기기
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(etNickname.windowToken, 0)
                    etNickname.visibility = View.GONE; btnSubmitRank.visibility = View.GONE
                    tvRankResult.visibility = View.VISIBLE
                    val s = gs()
                    tvRankResult.text = when { ok && rank != null -> "🏆 #$rank!"; ok -> s.rankOk; else -> s.rankFail }
                }
            }
        }

        // 다시하기: 전면 광고 → 같은 위키의 난이도 선택 화면으로
        btnPlayAgain.setOnClickListener {
            showInterstitialThen {
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("show_difficulty", true)
                    putExtra("wiki", gameState.wiki)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
                finish()
            }
        }
        // 홈으로: 전면 광고 → 앱 첫 화면으로
        btnGoHome.setOnClickListener { showInterstitialThen { finish() } }
        btnShare.setOnClickListener { shareResult() }
        btnChallenge.setOnClickListener { sendChallenge() }
    }

    private fun togglePathPanel() {
        if (pathPanel.visibility == View.VISIBLE) {
            pathPanel.visibility = View.GONE
        } else {
            renderPathPanel()
            (pathPanel.layoutParams as FrameLayout.LayoutParams).topMargin = hud.height
            pathPanel.requestLayout()
            pathPanel.visibility = View.VISIBLE
        }
    }

    private fun renderPathPanel() {
        pathContent.removeAllViews()
        gameState.path.forEachIndexed { i, page ->
            if (i > 0) {
                pathContent.addView(TextView(this).apply {
                    text = "→"; textSize = 12f
                    setTextColor(getColor(R.color.border)); setPadding(8, 0, 8, 0)
                })
            }
            pathContent.addView(TextView(this).apply {
                text = page; textSize = 13f
                setTextColor(if (i == gameState.path.size - 1) getColor(R.color.primary) else getColor(R.color.muted))
                if (i == gameState.path.size - 1) setTypeface(null, android.graphics.Typeface.BOLD)
            })
        }
    }

    private fun showGiveUpDialog() {
        val s = gs()
        AlertDialog.Builder(this)
            .setTitle(s.giveUpQ)
            .setMessage("${gameState.start} → ${gameState.goal}")
            .setPositiveButton(s.giveUpYes) { _, _ -> recordGiveUp(); finish() }
            .setNeutralButton(s.giveUpGoal) { _, _ ->
                recordGiveUp()
                gameState.active = false
                handler.removeCallbacks(timerRunnable)
                gameCompleted = true
                btnGiveUp.text = getCloseText()
                webView.loadUrl(buildWikiUrl(gameState.goal, gameState.wiki))
            }
            .setNegativeButton(s.giveUpKeep, null)
            .show()
    }

    private fun recordGiveUp() {
        if (isGuide) return  // 가이드 게임 기록은 저장하지 않음
        val prefix = "s_${gameState.wiki}_${gameState.difficulty}_"
        statsPrefs.edit()
            .putInt("totalGames", statsPrefs.getInt("totalGames", 0) + 1)
            .putInt("streak", 0)
            .putInt("${prefix}total", statsPrefs.getInt("${prefix}total", 0) + 1)
            .putInt("${prefix}streak", 0)
            .apply()
    }

    private fun shareResult() {
        val text = "Linky Run\n${gameState.start} → ${gameState.goal}\n⏱ ${fmtTime(gameState.elapsed)}  🔗 ${gameState.hops}${getHopsUnit()}\nhttps://linkyrun.com"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "공유"))
    }

    private fun sendChallenge() {
        val gs = gameState
        thread {
            val url = ApiClient.createChallenge(gs.start, gs.goal, gs.wiki, gs.hops, gs.elapsed)
                ?: "https://linkyrun.com/?start=${URLEncoder.encode(gs.start,"UTF-8")}&goal=${URLEncoder.encode(gs.goal,"UTF-8")}&wiki=${URLEncoder.encode(gs.wiki,"UTF-8")}"
            val text = "Linky Run 도전장!\n${gs.start} → ${gs.goal}\n내 기록: ⏱ ${fmtTime(gs.elapsed)}  🔗 ${gs.hops}${getHopsUnit()}\n도전: $url"
            runOnUiThread {
                try {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("linkyrun", text))
                    Toast.makeText(this, gs().challengeCopied, Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "도전장 보내기"))
                }
            }
        }
    }

    private var guideBanner: TextView? = null

    private fun showGuideHint() {
        val hintText = gs().guideHint(gameState.goal)
        val dp = resources.displayMetrics.density
        guideBanner = TextView(this).apply {
            text = hintText
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xDD3B82F6.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.BOTTOM }
        }
        (findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout).addView(guideBanner)
    }

    private fun loadInterstitialAd() {
        InterstitialAd.load(this, "ca-app-pub-4723026681968956/9463382111",
            AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(err: LoadAdError) { interstitialAd = null }
            })
    }

    private fun showInterstitialThen(action: () -> Unit) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { interstitialAd = null; action() }
                override fun onAdFailedToShowFullScreenContent(err: com.google.android.gms.ads.AdError) { interstitialAd = null; action() }
            }
            ad.show(this)
        } else {
            action()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        webView.destroy()
    }
}
