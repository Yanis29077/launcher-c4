package com.launcherc4.home

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val COLOR_BG = Color.parseColor("#0E1013")
private val COLOR_CARD = Color.parseColor("#1A1F26")
private val COLOR_MUTED = Color.parseColor("#9AA1AB")
private val COLOR_ACCENT = Color.parseColor("#3B82F6")
private val COLOR_ORANGE = Color.parseColor("#F59E0B")

class MainActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private val handler = Handler(Looper.getMainLooper())
    private var unit = 1f
    private var ticks = 0

    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var alertText: TextView
    private lateinit var carView: CarView
    private lateinit var musicArt: ImageView
    private lateinit var musicTitle: TextView
    private lateinit var musicArtist: TextView
    private lateinit var playButton: ImageView
    private lateinit var dock: LinearLayout
    private lateinit var drawer: FrameLayout
    private lateinit var drawerGrid: GridLayout

    private var controller: MediaController? = null
    private var demoDoorOpen = false

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
    private val dateFormat = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)

    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            if (ticks % 2 == 0) refreshMusic()
            if (ticks % 900 == 0) fetchWeather()
            ticks++
            handler.postDelayed(this, 1000)
        }
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        unit = minOf(dm.widthPixels, dm.heightPixels) / 100f
        setContentView(buildUi())

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    override fun onResume() {
        super.onResume()
        fillDock()
        handler.removeCallbacks(ticker)
        ticks = 0
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        drawer.visibility = View.GONE
    }

    override fun onBackPressed() {
        if (drawer.visibility == View.VISIBLE) {
            drawer.visibility = View.GONE
        }
        // Sur un écran d'accueil, "retour" ne doit rien faire d'autre.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        fetchWeather()
    }

    // ------------------------------------------------------------------
    // Construction de l'interface
    // ------------------------------------------------------------------

    private fun u(v: Float): Int = (v * unit).toInt()

    private fun rounded(color: Int, radius: Float): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(color)
        d.cornerRadius = radius * unit
        return d
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.setTextColor(color)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit)
        if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
        return tv
    }

    private fun buildUi(): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(COLOR_BG)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(u(2f), u(2f), u(2f), u(2f))
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        content.addView(top, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // ----- Partie gauche : voiture + heure + météo -----
        val left = FrameLayout(this)
        top.addView(left, LinearLayout.LayoutParams(0, MATCH, 1.9f))

        carView = CarView(this)
        carView.setPadding(0, u(10f), 0, u(5f))
        carView.setOnLongClickListener {
            demoDoorOpen = !demoDoorOpen
            setDoorOpen(demoDoorOpen, true)
            true
        }
        left.addView(carView, FrameLayout.LayoutParams(MATCH, MATCH))

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        timeText = text("--:--", 10f, Color.WHITE, true)
        dateText = text("", 3.2f, COLOR_MUTED, false)
        weatherText = text("Météo…", 3.2f, COLOR_MUTED, false)
        info.addView(timeText)
        info.addView(dateText)
        info.addView(weatherText)
        val infoLp = FrameLayout.LayoutParams(WRAP, WRAP)
        infoLp.gravity = Gravity.TOP or Gravity.START
        infoLp.setMargins(u(1.5f), u(1f), 0, 0)
        left.addView(info, infoLp)

        val gear = ImageView(this)
        gear.setImageResource(android.R.drawable.ic_menu_preferences)
        gear.setColorFilter(COLOR_MUTED)
        gear.setPadding(u(1f), u(1f), u(1f), u(1f))
        gear.setOnClickListener { safeStart(Intent(Settings.ACTION_SETTINGS)) }
        val gearLp = FrameLayout.LayoutParams(u(7f), u(7f))
        gearLp.gravity = Gravity.TOP or Gravity.END
        gearLp.setMargins(0, u(1f), u(1f), 0)
        left.addView(gear, gearLp)

        alertText = text("", 3f, COLOR_ORANGE, false)
        alertText.visibility = View.GONE
        val alertLp = FrameLayout.LayoutParams(WRAP, WRAP)
        alertLp.gravity = Gravity.BOTTOM or Gravity.START
        alertLp.setMargins(u(1.5f), 0, 0, u(1f))
        left.addView(alertText, alertLp)

        // ----- Partie droite : musique + carte -----
        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        val rightLp = LinearLayout.LayoutParams(0, MATCH, 1f)
        rightLp.setMargins(u(1.5f), 0, 0, 0)
        top.addView(right, rightLp)

        val music = LinearLayout(this)
        music.orientation = LinearLayout.VERTICAL
        music.gravity = Gravity.CENTER_VERTICAL
        music.background = rounded(COLOR_CARD, 3f)
        music.setPadding(u(1.5f), u(1.5f), u(1.5f), u(1.5f))
        right.addView(music, LinearLayout.LayoutParams(MATCH, 0, 1.4f))

        val musicRow = LinearLayout(this)
        musicRow.orientation = LinearLayout.HORIZONTAL
        musicRow.gravity = Gravity.CENTER_VERTICAL
        musicRow.setOnClickListener { onMusicCardClick() }

        musicArt = ImageView(this)
        musicArt.scaleType = ImageView.ScaleType.CENTER_CROP
        musicArt.background = rounded(Color.parseColor("#2B323C"), 1.5f)
        musicRow.addView(musicArt, LinearLayout.LayoutParams(u(11f), u(11f)))

        val musicCol = LinearLayout(this)
        musicCol.orientation = LinearLayout.VERTICAL
        musicTitle = text("Musique", 3.2f, Color.WHITE, true)
        musicTitle.maxLines = 1
        musicTitle.ellipsize = TextUtils.TruncateAt.END
        musicArtist = text("", 2.6f, COLOR_MUTED, false)
        musicArtist.maxLines = 2
        musicArtist.ellipsize = TextUtils.TruncateAt.END
        musicCol.addView(musicTitle)
        musicCol.addView(musicArtist)
        val colLp = LinearLayout.LayoutParams(0, WRAP, 1f)
        colLp.setMargins(u(1.5f), 0, 0, 0)
        musicRow.addView(musicCol, colLp)

        music.addView(musicRow, LinearLayout.LayoutParams(MATCH, WRAP))

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER
        val prev = controlButton(android.R.drawable.ic_media_previous) { controller?.transportControls?.skipToPrevious() }
        playButton = controlButton(android.R.drawable.ic_media_play) { togglePlay() }
        val next = controlButton(android.R.drawable.ic_media_next) { controller?.transportControls?.skipToNext() }
        controls.addView(prev, LinearLayout.LayoutParams(u(10f), u(10f)))
        controls.addView(playButton, LinearLayout.LayoutParams(u(10f), u(10f)))
        controls.addView(next, LinearLayout.LayoutParams(u(10f), u(10f)))
        val controlsLp = LinearLayout.LayoutParams(MATCH, WRAP)
        controlsLp.setMargins(0, u(1.5f), 0, 0)
        music.addView(controls, controlsLp)

        val mapCard = LinearLayout(this)
        mapCard.orientation = LinearLayout.HORIZONTAL
        mapCard.gravity = Gravity.CENTER_VERTICAL
        mapCard.background = rounded(COLOR_CARD, 3f)
        mapCard.setPadding(u(1.5f), u(1.5f), u(1.5f), u(1.5f))
        mapCard.setOnClickListener { openNavigation() }
        val mapIcon = ImageView(this)
        mapIcon.setImageResource(android.R.drawable.ic_menu_mapmode)
        mapIcon.setColorFilter(Color.WHITE)
        mapCard.addView(mapIcon, LinearLayout.LayoutParams(u(8f), u(8f)))
        val mapCol = LinearLayout(this)
        mapCol.orientation = LinearLayout.VERTICAL
        mapCol.addView(text("Carte", 3.4f, Color.WHITE, true))
        mapCol.addView(text("Ouvrir la navigation", 2.6f, COLOR_MUTED, false))
        val mapColLp = LinearLayout.LayoutParams(WRAP, WRAP)
        mapColLp.setMargins(u(1.5f), 0, 0, 0)
        mapCard.addView(mapCol, mapColLp)
        val mapLp = LinearLayout.LayoutParams(MATCH, 0, 1f)
        mapLp.setMargins(0, u(1.5f), 0, 0)
        right.addView(mapCard, mapLp)

        // ----- Rangée de raccourcis en bas -----
        dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        val dockLp = LinearLayout.LayoutParams(MATCH, u(17f))
        dockLp.setMargins(0, u(1.5f), 0, 0)
        content.addView(dock, dockLp)

        // ----- Liste de toutes les applis (cachée au départ) -----
        drawer = FrameLayout(this)
        drawer.setBackgroundColor(Color.parseColor("#F2101216"))
        drawer.visibility = View.GONE
        drawer.isClickable = true

        val drawerInner = LinearLayout(this)
        drawerInner.orientation = LinearLayout.VERTICAL
        drawerInner.setPadding(u(2f), u(2f), u(2f), u(2f))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = text("Toutes les applis", 4f, Color.WHITE, true)
        header.addView(title, LinearLayout.LayoutParams(0, WRAP, 1f))
        val close = text("Fermer", 3.4f, Color.WHITE, false)
        close.background = rounded(COLOR_CARD, 3f)
        close.setPadding(u(3f), u(1.5f), u(3f), u(1.5f))
        close.setOnClickListener { drawer.visibility = View.GONE }
        header.addView(close, LinearLayout.LayoutParams(WRAP, WRAP))
        drawerInner.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        val scroll = ScrollView(this)
        drawerGrid = GridLayout(this)
        drawerGrid.columnCount = 7
        scroll.addView(drawerGrid)
        val scrollLp = LinearLayout.LayoutParams(MATCH, 0, 1f)
        scrollLp.setMargins(0, u(1.5f), 0, 0)
        drawerInner.addView(scroll, scrollLp)

        drawer.addView(drawerInner, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(drawer, FrameLayout.LayoutParams(MATCH, MATCH))

        return root
    }

    private fun controlButton(res: Int, action: () -> Unit): ImageView {
        val b = ImageView(this)
        b.setImageResource(res)
        b.setColorFilter(Color.WHITE)
        b.scaleType = ImageView.ScaleType.FIT_CENTER
        b.setPadding(u(1.2f), u(1.2f), u(1.2f), u(1.2f))
        b.setOnClickListener { action() }
        return b
    }

    private fun makeTile(label: String, icon: Drawable?, dim: Boolean, tint: Int? = null, onClick: () -> Unit): View {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.gravity = Gravity.CENTER
        tile.background = rounded(COLOR_CARD, 3f)

        val img = ImageView(this)
        img.setImageDrawable(icon)
        if (tint != null) img.setColorFilter(tint)
        if (dim) img.alpha = 0.35f
        tile.addView(img, LinearLayout.LayoutParams(u(8f), u(8f)))

        val tv = text(label, 2.6f, COLOR_MUTED, false)
        tv.gravity = Gravity.CENTER
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
        val tvLp = LinearLayout.LayoutParams(WRAP, WRAP)
        tvLp.topMargin = u(0.8f)
        tile.addView(tv, tvLp)

        tile.setOnClickListener { onClick() }

        val lp = LinearLayout.LayoutParams(0, MATCH, 1f)
        lp.setMargins(u(0.5f), 0, u(0.5f), 0)
        tile.layoutParams = lp
        return tile
    }

    // ------------------------------------------------------------------
    // Raccourcis et liste des applis
    // ------------------------------------------------------------------

    private fun fillDock() {
        dock.removeAllViews()
        dock.addView(carPlayTile())
        dock.addView(appTile("Waze", "com.waze"))
        dock.addView(appTile("Maps", "com.google.android.apps.maps"))
        dock.addView(appTile("Spotify", "com.spotify.music"))
        dock.addView(appTile("YouTube", "com.google.android.youtube"))
        dock.addView(appTile("Uber", "com.ubercab.driver"))
        dock.addView(appTile("Snap", "com.snapchat.android"))
        dock.addView(appTile("WhatsApp", "com.whatsapp"))
        dock.addView(allAppsTile())
    }

    private fun appTile(label: String, pkg: String): View {
        var icon: Drawable? = null
        try {
            icon = packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            icon = null
        }
        val installed = icon != null
        val shown: Drawable? = icon ?: getDrawable(android.R.drawable.sym_def_app_icon)
        return makeTile(label, shown, !installed) { launchPackage(pkg, label) }
    }

    private fun carPlayTile(): View {
        val ri = findCarPlayApp()
        val icon: Drawable? = ri?.loadIcon(packageManager) ?: getDrawable(android.R.drawable.sym_def_app_icon)
        return makeTile("CarPlay", icon, ri == null) {
            if (ri != null) {
                launchResolved(ri)
            } else {
                toast("Appli CarPlay introuvable. Cherche-la dans Apps.")
                openDrawer()
            }
        }
    }

    private fun allAppsTile(): View {
        val icon: Drawable? = getDrawable(android.R.drawable.ic_menu_more)
        return makeTile("Apps", icon, false, COLOR_MUTED) { openDrawer() }
    }

    private fun launchableApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val list = packageManager.queryIntentActivities(intent, 0)
        return list
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun findCarPlayApp(): ResolveInfo? {
        val words = listOf("carplay", "zlink", "z-link", "autolink", "auto link", "carlink")
        return launchableApps().firstOrNull { ri ->
            val name = ri.loadLabel(packageManager).toString().lowercase()
            words.any { name.contains(it) }
        }
    }

    private fun openDrawer() {
        drawerGrid.removeAllViews()
        for (ri in launchableApps()) {
            drawerGrid.addView(drawerTile(ri))
        }
        drawer.visibility = View.VISIBLE
    }

    private fun drawerTile(ri: ResolveInfo): View {
        val label = ri.loadLabel(packageManager).toString()
        val tile = makeTile(label, ri.loadIcon(packageManager), false) {
            drawer.visibility = View.GONE
            launchResolved(ri)
        }
        val lp = GridLayout.LayoutParams()
        lp.width = u(16f)
        lp.height = u(17f)
        lp.setMargins(u(0.6f), u(0.6f), u(0.6f), u(0.6f))
        tile.layoutParams = lp
        return tile
    }

    private fun launchResolved(ri: ResolveInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(intent)
    }

    private fun launchPackage(pkg: String, name: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            safeStart(intent)
        } else {
            toast("$name n'est pas installé")
            try {
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(market)
            } catch (e: Exception) {
                // pas de Play Store sur cet autoradio : on ne fait rien de plus
            }
        }
    }

    private fun openNavigation() {
        val pkg = listOf("com.waze", "com.google.android.apps.maps")
            .firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }
        if (pkg == null) {
            toast("Installe Waze ou Google Maps")
        } else {
            launchPackage(pkg, "Navigation")
        }
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Impossible d'ouvrir cette appli")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------------------------
    // Heure, date, porte
    // ------------------------------------------------------------------

    private fun updateClock() {
        val now = Date()
        timeText.text = timeFormat.format(now)
        dateText.text = dateFormat.format(now).replaceFirstChar { it.uppercase() }
    }

    private fun setDoorOpen(open: Boolean, demo: Boolean) {
        carView.frontLeftOpen = open
        if (open) {
            alertText.text = if (demo) "Porte avant gauche ouverte (test)" else "Porte avant gauche ouverte"
            alertText.visibility = View.VISIBLE
        } else {
            alertText.visibility = View.GONE
        }
    }

    // ------------------------------------------------------------------
    // Musique
    // ------------------------------------------------------------------

    private fun listenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun findController(): MediaController? {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(ComponentName(this, MusicListener::class.java))
            val playing = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            return playing ?: sessions.firstOrNull()
        } catch (e: Exception) {
            return null
        }
    }

    private fun refreshMusic() {
        if (!listenerEnabled()) {
            controller = null
            musicTitle.text = "Activer la musique"
            musicArtist.text = "Touche ici puis autorise Launcher C4"
            musicArt.setImageDrawable(null)
            playButton.setImageResource(android.R.drawable.ic_media_play)
            return
        }

        val c = findController()
        controller = c
        if (c == null) {
            musicTitle.text = "Aucune musique"
            musicArtist.text = "Lance Spotify"
            musicArt.setImageDrawable(null)
            playButton.setImageResource(android.R.drawable.ic_media_play)
            return
        }

        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        musicTitle.text = title ?: "Lecture en cours"
        musicArtist.text = artist ?: ""

        val bmp: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        musicArt.setImageBitmap(bmp)

        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        playButton.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    private fun togglePlay() {
        val c = controller
        if (c == null) {
            onMusicCardClick()
            return
        }
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    private fun onMusicCardClick() {
        if (!listenerEnabled()) {
            safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return
        }
        val c = controller
        if (c == null) {
            launchPackage("com.spotify.music", "Spotify")
            return
        }
        try {
            c.sessionActivity?.send()
        } catch (e: Exception) {
            // rien à faire
        }
    }

    // ------------------------------------------------------------------
    // Météo
    // ------------------------------------------------------------------

    private fun currentCoords(): DoubleArray {
        // Position par défaut (région de Brest) si le GPS ne donne rien.
        var lat = 48.39
        var lon = -4.49
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                var best: Location? = null
                for (p in lm.getProviders(true)) {
                    try {
                        val l = lm.getLastKnownLocation(p)
                        if (l != null && (best == null || l.time > best.time)) {
                            best = l
                        }
                    } catch (e: Exception) {
                        // ce fournisseur de position n'est pas autorisé
                    }
                }
                if (best != null) {
                    lat = best.latitude
                    lon = best.longitude
                }
            }
        } catch (e: Exception) {
            // on garde la position par défaut
        }
        return doubleArrayOf(lat, lon)
    }

    private fun fetchWeather() {
        Thread {
            try {
                val coords = currentCoords()
                val lat = coords[0]
                val lon = coords[1]
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code&timezone=auto"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val cur = JSONObject(body).getJSONObject("current")
                val t = Math.round(cur.getDouble("temperature_2m")).toInt()
                val code = cur.getInt("weather_code")
                runOnUiThread { weatherText.text = "$t °C · ${weatherLabel(code)}" }
            } catch (e: Exception) {
                runOnUiThread { weatherText.text = "Météo indisponible" }
            }
        }.start()
    }

    private fun weatherLabel(code: Int): String {
        return when (code) {
            0 -> "Ciel dégagé"
            1, 2 -> "Peu nuageux"
            3 -> "Couvert"
            45, 48 -> "Brouillard"
            in 51..57 -> "Bruine"
            in 61..67 -> "Pluie"
            in 71..77 -> "Neige"
            in 80..82 -> "Averses"
            85, 86 -> "Averses de neige"
            in 95..99 -> "Orage"
            else -> "Météo"
        }
    }
}
