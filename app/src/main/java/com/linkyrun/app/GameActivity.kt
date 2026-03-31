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
            "fr" to Pair("fr.wikipedia.org", "/wiki/")
        )

        // namu.wiki에 주입할 JS: 링크 클릭 인터셉트 + 헤더 숨김
        private val NAMU_INJECT_JS = """
            (function(){
                if (window.__linkyInjected) return;
                window.__linkyInjected = true;

                // namu.wiki 자체 헤더 숨김
                var style = document.createElement('style');
                style.textContent = 'header{display:none!important}body,#app>div{padding-top:0!important;margin-top:0!important}';
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
    private lateinit var btnGiveUp: Button
    private lateinit var victoryOverlay: FrameLayout
    private lateinit var tvVictoryTime: TextView
    private lateinit var tvVictoryHops: TextView
    private lateinit var tvVictoryPath: TextView
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

    private var lastProgrammaticUrl: String? = null
    private lateinit var gameState: GameState
    private lateinit var statsPrefs: SharedPreferences

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

        gameState = GameState(
            start = start, goal = goal,
            difficulty = difficulty, wiki = wiki, dayNum = dayNum
        ).also { it.path.add(start) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    victoryOverlay.visibility == View.VISIBLE -> finish()
                    pathPanel.visibility == View.VISIBLE -> pathPanel.visibility = View.GONE
                    webView.canGoBack() -> webView.goBack()
                    else -> showGiveUpDialog()
                }
            }
        })

        bindViews()
        setupInsets()
        setupWebView()   // JS injection 포함
        setupButtons()
        syncHUD()

        webView.loadUrl(buildWikiUrl(start, wiki))
        handler.post(timerRunnable)
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
        webView       = findViewById(R.id.webView)
        tvTimer       = findViewById(R.id.tvTimer)
        tvHops        = findViewById(R.id.tvHops)
        tvGoal        = findViewById(R.id.tvGoal)
        btnGiveUp     = findViewById(R.id.btnGiveUp)
        victoryOverlay = findViewById(R.id.victoryOverlay)
        tvVictoryTime = findViewById(R.id.tvVictoryTime)
        tvVictoryHops = findViewById(R.id.tvVictoryHops)
        tvVictoryPath = findViewById(R.id.tvVictoryPath)
        btnSubmitRank = findViewById(R.id.btnSubmitRank)
        etNickname    = findViewById(R.id.etNickname)
        tvRankResult  = findViewById(R.id.tvRankResult)
        btnPlayAgain  = findViewById(R.id.btnPlayAgain)
        btnGoHome     = findViewById(R.id.btnGoHome)
        btnShare      = findViewById(R.id.btnShare)
        btnChallenge  = findViewById(R.id.btnChallenge)
        rankSection   = findViewById(R.id.rankSection)
        hud           = findViewById(R.id.hud)
        pathPanel     = findViewById(R.id.pathPanel)
        pathContent   = findViewById(R.id.pathContent)
        hudCenter     = findViewById(R.id.hudCenter)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(hud) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top + 8, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(victoryOverlay) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val sv = (v as FrameLayout).getChildAt(0)
            sv?.setPadding(0, bars.top, 0, bars.bottom)
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

            override fun onPageFinished(view: WebView, url: String) {
                // ★ 페이지 로드 완료마다 JS 재주입 (SPA 네비게이션 대응)
                val js = if (gameState.wiki == "namu") NAMU_INJECT_JS else WIKI_INJECT_JS
                view.evaluateJavascript(js, null)

                // 리다이렉트 후 최종 URL로 목표 확인
                val title = extractTitle(url, gameState.wiki)
                if (title != null && gameState.active && gameState.hops > 0 && isGoal(title, gameState.goal)) {
                    showVictory()
                }
            }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (gameState.wiki == "namu") return false  // namu: JS injection이 처리
                val url = request.url.toString()
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
            showVictory()
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
        if (nt == ng) return true
        val st = stripParen(nt); val sg = stripParen(ng)
        if (st.isNotEmpty() && sg.isNotEmpty() && st == sg) return true
        if (nt.length >= 4 && ng.length >= 4 && similarity(nt, ng) >= 0.88) return true
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

    private fun showVictory() {
        if (!gameState.active) return
        handler.removeCallbacks(timerRunnable)
        gameState.elapsed = System.currentTimeMillis() - gameState.startTime
        gameState.active = false
        updatePersonalStats()

        tvVictoryTime.text = fmtTime(gameState.elapsed)
        tvVictoryHops.text = "${gameState.hops}홉"
        tvTimer.text = fmtTime(gameState.elapsed)

        tvVictoryPath.text = gameState.path.joinToString(" → ")

        if (gameState.difficulty == "daily" || gameState.difficulty == "custom") {
            rankSection.visibility = View.GONE
        }

        // HUD 아래에서 시작하도록 ScrollView 상단 패딩 조정
        val sv = victoryOverlay.getChildAt(0)
        hud.post { sv?.setPadding(0, hud.height, 0, 0) }

        victoryOverlay.visibility = View.VISIBLE
    }

    private fun updatePersonalStats() {
        val wins = statsPrefs.getInt("wins", 0) + 1
        val total = statsPrefs.getInt("totalGames", 0) + 1
        val streak = statsPrefs.getInt("streak", 0) + 1
        val bestStreak = maxOf(statsPrefs.getInt("bestStreak", 0), streak)
        val prevBestTime = statsPrefs.getLong("bestTime", Long.MAX_VALUE)
        val bestTime = if (gameState.elapsed < prevBestTime) gameState.elapsed else prevBestTime
        val prevBestHops = statsPrefs.getInt("bestHops", Int.MAX_VALUE)
        val bestHops = if (gameState.hops < prevBestHops) gameState.hops else prevBestHops
        statsPrefs.edit()
            .putInt("wins", wins).putInt("totalGames", total)
            .putInt("streak", streak).putInt("bestStreak", bestStreak)
            .putLong("bestTime", bestTime).putInt("bestHops", bestHops)
            .apply()
    }

    private fun setupButtons() {
        hudCenter.setOnClickListener { togglePathPanel() }
        btnGiveUp.setOnClickListener { showGiveUpDialog() }

        btnSubmitRank.setOnClickListener {
            val nick = etNickname.text.toString().trim()
            if (nick.isEmpty()) { etNickname.error = "닉네임 입력"; return@setOnClickListener }
            btnSubmitRank.isEnabled = false; btnSubmitRank.text = "등록 중…"
            val gs = gameState
            thread {
                val (ok, rank) = ApiClient.submitRanking(
                    nick, gs.start, gs.goal, gs.elapsed, gs.hops, gs.path, gs.difficulty, gs.wiki)
                runOnUiThread {
                    etNickname.visibility = View.GONE; btnSubmitRank.visibility = View.GONE
                    tvRankResult.visibility = View.VISIBLE
                    tvRankResult.text = when { ok && rank != null -> "🏆 ${rank}위로 등록됐어요!"; ok -> "등록 완료!"; else -> "등록 실패" }
                }
            }
        }

        // 다시하기: 같은 위키의 난이도 선택 화면으로
        btnPlayAgain.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("show_difficulty", true)
                putExtra("wiki", gameState.wiki)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
        }
        // 홈으로: 앱 첫 화면으로
        btnGoHome.setOnClickListener { finish() }
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
        AlertDialog.Builder(this)
            .setTitle("게임을 포기할까요?")
            .setMessage("${gameState.start} → ${gameState.goal}")
            .setPositiveButton("포기") { _, _ -> recordGiveUp(); finish() }
            .setNeutralButton("목적 페이지로 이동") { _, _ ->
                recordGiveUp()
                gameState.active = false
                handler.removeCallbacks(timerRunnable)
                webView.loadUrl(buildWikiUrl(gameState.goal, gameState.wiki))
            }
            .setNegativeButton("계속하기", null)
            .show()
    }

    private fun recordGiveUp() {
        statsPrefs.edit().putInt("totalGames", statsPrefs.getInt("totalGames", 0) + 1).putInt("streak", 0).apply()
    }

    private fun shareResult() {
        val text = "Linky Run\n${gameState.start} → ${gameState.goal}\n⏱ ${fmtTime(gameState.elapsed)}  🔗 ${gameState.hops}홉\nhttps://linkyrun.com"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "공유"))
    }

    private fun sendChallenge() {
        val gs = gameState
        thread {
            val url = ApiClient.createChallenge(gs.start, gs.goal, gs.wiki, gs.hops, gs.elapsed)
                ?: "https://linkyrun.com/?start=${gs.start}&goal=${gs.goal}&wiki=${gs.wiki}"
            val text = "Linky Run 도전장!\n${gs.start} → ${gs.goal}\n내 기록: ⏱ ${fmtTime(gs.elapsed)}  🔗 ${gs.hops}홉\n도전: $url"
            runOnUiThread {
                try {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("linkyrun", text))
                    Toast.makeText(this, "도전장이 복사됐어요!", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "도전장 보내기"))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        webView.destroy()
    }
}
