package com.my.playerall
// Player Activity
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.my.playerall.databinding.ActivityPlayerBinding
import com.my.playerall.databinding.BoosterBinding
import com.my.playerall.databinding.MoreFeaturesBinding
import com.my.playerall.databinding.SpeedDialogBinding
import com.github.vkay94.dtpv.youtube.YouTubeOverlay
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.TimeBar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.DecimalFormat
import kotlin.math.abs
class PlayerActivity : AppCompatActivity(), AudioManager.OnAudioFocusChangeListener, GestureDetector.OnGestureListener {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var playPauseBtn: ImageButton
    private lateinit var fullScreenBtn: ImageButton
    private lateinit var orientationBtn: ImageButton
    private lateinit var videoTitle: TextView
    private lateinit var gestureDetectorCompat: GestureDetectorCompat
    private var minSwipeY: Float = 0f
    private var minSwipeX: Float = 0f
    companion object{
        private var audioManager: AudioManager? = null
        private lateinit var player: ExoPlayer
        lateinit var playerList: ArrayList<Video>
        var position: Int = -1
        private var repeat: Boolean = false
        private var isFullscreen: Boolean = false
        private var isLocked: Boolean = false
        @SuppressLint("StaticFieldLeak")
        private lateinit var trackSelector: DefaultTrackSelector
        private lateinit var loudnessEnhancer: LoudnessEnhancer
        private var speed: Float = 1.0f
        var pipStatus: Int = 0
        var nowPlayingId: String = ""
        private var brightness: Int = 0
        private var volume: Int = 0
        private var isSpeedChecked: Boolean = false
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setTheme(R.style.playerActivityTheme)
        setContentView(binding.root)

        videoTitle = findViewById(R.id.videoTitle)
        playPauseBtn = findViewById(R.id.playPauseBtn)
        fullScreenBtn = findViewById(R.id.fullScreenBtn)
        orientationBtn = findViewById(R.id.orientationBtn)
        gestureDetectorCompat = GestureDetectorCompat(this, this)

        //for immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        try {
            //for handling video file intent (Improved Version)
            if(intent.data?.scheme.contentEquals("content")){
                playerList = ArrayList()
                position = 0
                val cursor = contentResolver.query(intent.data!!, arrayOf(MediaStore.Video.Media.DATA), null, null,
                    null)
                cursor?.let {
                    it.moveToFirst()
                    try {
                        val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
                        val file = File(path)
                        val video = Video(id = "", title = file.name, duration = 0L, artUri = Uri.fromFile(file), path = path, size = "", folderName = "")
                        playerList.add(video)
                        cursor.close()
                    }catch (e: Exception){
                        val tempPath = getPathFromURI(context = this, uri = intent.data!!)
                        val tempFile = File(tempPath)
                        val video = Video(id = "", title = tempFile.name, duration = 0L, artUri = Uri.fromFile(tempFile), path = tempPath, size = "", folderName = "")
                        playerList.add(video)
                        cursor.close()
                    }
                }
                createPlayer()
                initializeBinding()
            }
            else{
                initializeLayout()
                initializeBinding()
            }
        }catch (e: Exception){Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show()}
    }
    @SuppressLint("PrivateResource")
    private fun initializeLayout(){
        when(intent.getStringExtra("class")){
            "AllVideos" -> {
                playerList = ArrayList()
                playerList.addAll(MainActivity.videoList)
                createPlayer()
            }
            "FolderActivity" -> {
                playerList = ArrayList()
                playerList.addAll(FoldersActivity.currentFolderVideos)
                createPlayer()
            }
            "SearchedVideos" ->{
                playerList = ArrayList()
                playerList.addAll(MainActivity.searchList)
                createPlayer()
            }
            "NowPlaying" ->{
                speed = 1.0f
                videoTitle.text = playerList[position].title
                videoTitle.isSelected = true
                doubleTapEnable()
                playVideo()
                playInFullscreen(enable = isFullscreen)
                seekBarFeature()
            }
        }
        if(repeat) {
            findViewById<ImageButton>(R.id.repeatBtn)
                .setImageResource(com.github.vkay94.dtpv.R.drawable.exo_controls_repeat_all)
        }
        else {
            findViewById<ImageButton>(R.id.repeatBtn)
                .setImageResource(com.github.vkay94.dtpv.R.drawable.exo_controls_repeat_all)
        }
    }
    @SuppressLint("SetTextI18n", "SourceLockedOrientationActivity", "PrivateResource")
    private fun initializeBinding(){

        findViewById<ImageButton>(R.id.orientationBtn).setOnClickListener {
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                orientationBtn.setColorFilter(ContextCompat.getColor(this, R.color.green), PorterDuff.Mode.SRC_IN)
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                orientationBtn.setColorFilter(ContextCompat.getColor(this, R.color.white), PorterDuff.Mode.SRC_IN)
            }
        }
        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            finish()
        }
        playPauseBtn.setOnClickListener {
            if(player.isPlaying) pauseVideo()
            else playVideo()
        }
        findViewById<ImageButton>(R.id.nextBtn).setOnClickListener { nextPrevVideo() }
        findViewById<ImageButton>(R.id.prevBtn).setOnClickListener { nextPrevVideo(isNext = false) }

        findViewById<ImageButton>(R.id.repeatBtn).setOnClickListener {
            if(repeat){
                repeat = false
                player.repeatMode = Player.REPEAT_MODE_OFF
                findViewById<ImageButton>(R.id.repeatBtn).setImageResource(com.github.vkay94.dtpv.R.drawable.exo_controls_repeat_all)
                findViewById<ImageButton>(R.id.repeatBtn).setColorFilter(ContextCompat.getColor(this, R.color.white), PorterDuff.Mode.SRC_IN)
            }
            else{
                repeat = true
                player.repeatMode = Player.REPEAT_MODE_ONE
                findViewById<ImageButton>(R.id.repeatBtn).setImageResource(com.github.vkay94.dtpv.R.drawable.exo_controls_repeat_all)
                findViewById<ImageButton>(R.id.repeatBtn).setColorFilter(ContextCompat.getColor(this, R.color.green), PorterDuff.Mode.SRC_IN)
            }
        }
        fullScreenBtn.setOnClickListener {
            if(isFullscreen){
                isFullscreen = false
                playInFullscreen(enable = false)
            }else{
                isFullscreen = true
                playInFullscreen(enable = true)
            }
        }
        binding.lockButton.setOnClickListener {
            if(!isLocked){
                //for hiding
                isLocked = true
                binding.playerView.hideController()
                binding.playerView.useController = false
                binding.lockButton.setImageResource(R.drawable.close_lock_icon)

                binding.lockButton.setColorFilter(ContextCompat.getColor(this, R.color.green), PorterDuff.Mode.SRC_IN)
            }
            else{
                //for showing
                isLocked = false
                binding.playerView.useController = true
                binding.playerView.showController()
                binding.lockButton.setImageResource(R.drawable.lock_open_icon)

                binding.lockButton.setColorFilter(ContextCompat.getColor(this, R.color.white), PorterDuff.Mode.SRC_IN)
            }
        }
        //for auto hiding & showing lock button
        binding.playerView.setControllerVisibilityListener {
            when{
                isLocked -> binding.lockButton.visibility = View.VISIBLE
                binding.playerView.isControllerVisible -> binding.lockButton.visibility = View.VISIBLE
                else -> binding.lockButton.visibility = View.INVISIBLE
            }
        }
        findViewById<ImageButton>(R.id.moreFeaturesBtn).setOnClickListener {
            // findViewById<ImageButton>(R.id.moreFeaturesBtn).setColorFilter(ContextCompat.getColor(this, R.color.green), PorterDuff.Mode.SRC_IN)
            pauseVideo()
            val customDialog = LayoutInflater.from(this).inflate(R.layout.more_features, binding.root, false)
            val bindingMF = MoreFeaturesBinding.bind(customDialog)
            val dialog = MaterialAlertDialogBuilder(this).setView(customDialog)
                .setOnCancelListener { playVideo() }
                // black
                .setBackground(ColorDrawable(0x80000000.toInt()))
                .create()
            dialog.show()
            bindingMF.audioBooster.setOnClickListener {
                dialog.dismiss()
                playVideo()
                val customDialogB = LayoutInflater.from(this).inflate(R.layout.booster, binding.root, false)
                val bindingB = BoosterBinding.bind(customDialogB)
                val dialogB = MaterialAlertDialogBuilder(this).setView(customDialogB)
                    .setOnCancelListener { playVideo() }
                    .setPositiveButton("OK"){self, _ ->
                        loudnessEnhancer.setTargetGain(bindingB.verticalBar.progress * 100)
                        playVideo()
                        self.dismiss()
                    }
                        // white color
                    .setBackground(ColorDrawable(0x80000000.toInt()))
                    .create()
                dialogB.show()
                bindingB.verticalBar.progress = loudnessEnhancer.targetGain.toInt()/100
                bindingB.progressText.text = "Audio Boost\n\n${loudnessEnhancer.targetGain.toInt()/10} %"
                bindingB.verticalBar.setOnProgressChangeListener {
                    bindingB.progressText.text = "Audio Boost\n\n${it*10} %"
                }
            }
            bindingMF.speedBtn.setOnClickListener {
                dialog.dismiss()
                playVideo()
                val customDialogS = LayoutInflater.from(this).inflate(R.layout.speed_dialog, binding.root, false)
                val bindingS = SpeedDialogBinding.bind(customDialogS)
                val dialogS = MaterialAlertDialogBuilder(this).setView(customDialogS)
                    .setCancelable(false)
                    .setPositiveButton("OK"){self, _ ->
                        self.dismiss()
                    }
                        // white color
                    .setBackground(ColorDrawable(0x80000000.toInt()))
                    .create()
                dialogS.show()
                bindingS.speedText.text = "${DecimalFormat("#.##").format(speed)} X"
                bindingS.minusBtn.setOnClickListener {
                    changeSpeed(isIncrement = false)
                    bindingS.speedText.text = "${DecimalFormat("#.##").format(speed)} X"
                }
                bindingS.plusBtn.setOnClickListener {
                    changeSpeed(isIncrement = true)
                    bindingS.speedText.text = "${DecimalFormat("#.##").format(speed)} X"
                }

                bindingS.speedCheckBox.isChecked = isSpeedChecked
                bindingS.speedCheckBox.setOnClickListener {
                    it as CheckBox
                    isSpeedChecked = it.isChecked
                }
            }
            bindingMF.pipModeBtn.setOnClickListener {
                val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), packageName)==
                            AppOpsManager.MODE_ALLOWED
                } else { false }

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    if (status) {
                        this.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                        dialog.dismiss()
                        binding.playerView.hideController()
                        playVideo()
                        pipStatus = 0
                    }
                    else{
                        val intent = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS",
                            Uri.parse("package:$packageName"))
                        startActivity(intent)
                    }
                }else{
                    Toast.makeText(this, "Feature Not Supported!!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    playVideo()
                }
            }
        }
    }
    private fun createPlayer(){
        try { player.release() }catch (_: Exception){}
        if(!isSpeedChecked) speed = 1.0f
        trackSelector = DefaultTrackSelector(this)
        videoTitle.text = playerList[position].title
        videoTitle.isSelected = true
        player = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        doubleTapEnable()
        val mediaItem = MediaItem.fromUri(playerList[position].artUri)
        player.setMediaItem(mediaItem)
        player.setPlaybackSpeed(speed)
        player.prepare()
        playVideo()
        player.addListener(object : Player.Listener{
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if(playbackState == Player.STATE_ENDED) nextPrevVideo()
            }
        })
        playInFullscreen(enable = isFullscreen)
        loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
        loudnessEnhancer.enabled = true
        nowPlayingId = playerList[position].id
        seekBarFeature()
    }
    private fun playVideo(){
        playPauseBtn.setImageResource(R.drawable.pause_icon)
        player.play()
    }
    private fun pauseVideo(){
        playPauseBtn.setImageResource(R.drawable.play_icon)
        player.pause()
    }
    private fun nextPrevVideo(isNext: Boolean = true){
        if(isNext) setPosition()
        else setPosition(isIncrement = false)
        createPlayer()
    }
    private fun setPosition(isIncrement: Boolean = true){
        if(!repeat){
            if(isIncrement){
                if(playerList.size -1 == position)
                    position = 0
                else ++position
            }else{
                if(position  == 0)
                    position = playerList.size - 1
                else --position
            }
        }
    }
    private fun playInFullscreen(enable: Boolean){
        if(enable){
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            fullScreenBtn.setImageResource(R.drawable.fullscreen_exit_icon)
            findViewById<ImageButton>(R.id.fullScreenBtn).setColorFilter(ContextCompat.getColor(this, R.color.green), PorterDuff.Mode.SRC_IN)
        }else{
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            fullScreenBtn.setImageResource(R.drawable.fullscreen_icon)
            findViewById<ImageButton>(R.id.fullScreenBtn).setColorFilter(ContextCompat.getColor(this, R.color.white), PorterDuff.Mode.SRC_IN)
        }
    }
    private fun changeSpeed(isIncrement: Boolean){
        if(isIncrement){
            if(speed <= 4.9f){
                speed += 0.10f //speed = speed + 0.10f
            }
        }
        else{
            if(speed > 0.10f){
                speed -= 0.10f
            }
        }
        player.setPlaybackSpeed(speed)
    }
    @Deprecated("Deprecated in Java")
    @SuppressLint("MissingSuperCall")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        if(pipStatus != 0){
            finish()
            val intent = Intent(this, PlayerActivity::class.java)
            when(pipStatus){
                1 -> intent.putExtra("class","FolderActivity")
                2 -> intent.putExtra("class","SearchedVideos")
                3 -> intent.putExtra("class","AllVideos")
            }
            startActivity(intent)
        }
        if(!isInPictureInPictureMode) pauseVideo()
    }
    override fun onDestroy() {
        super.onDestroy()
        player.pause()
        audioManager?.abandonAudioFocus(this)
    }
    override fun onAudioFocusChange(focusChange: Int) {
        if(focusChange <= 0) pauseVideo()
    }
    override fun onResume() {
        super.onResume()
        if(audioManager == null) audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager!!.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if(brightness != 0) setScreenBrightness(brightness)
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun doubleTapEnable(){
        binding.playerView.player = player
        binding.ytOverlay.performListener(object: YouTubeOverlay.PerformListener{
            override fun onAnimationEnd() {
                binding.ytOverlay.visibility = View.GONE
            }
            override fun onAnimationStart() {
                binding.ytOverlay.visibility = View.VISIBLE
            }
        })
        binding.ytOverlay.player(player)
        binding.playerView.setOnTouchListener { _, motionEvent ->
            binding.playerView.isDoubleTapEnabled = false
            if(!isLocked){
                binding.playerView.isDoubleTapEnabled = true
                gestureDetectorCompat.onTouchEvent(motionEvent)
                if(motionEvent.action == MotionEvent.ACTION_UP) {
                    binding.brightnessIcon.visibility = View.GONE
                    binding.volumeIcon.visibility = View.GONE
                    //for immersive mode
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    WindowInsetsControllerCompat(window, binding.root).let { controller ->
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            }
            return@setOnTouchListener false
        }
    }
    private fun seekBarFeature(){
        findViewById<DefaultTimeBar>(com.github.vkay94.dtpv.R.id.exo_progress).addListener(object: TimeBar.OnScrubListener{
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                pauseVideo()
            }
            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                player.seekTo(position)
            }
            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                playVideo()
            }
        })
    }
    override fun onDown(p0: MotionEvent): Boolean {
        minSwipeY = 0f
        return false
    }
    override fun onShowPress(p0: MotionEvent) = Unit
    override fun onSingleTapUp(p0: MotionEvent): Boolean = false
    override fun onLongPress(p0: MotionEvent) = Unit
    override fun onFling(e1: MotionEvent?, p0: MotionEvent, p2: Float, p3: Float): Boolean = false
    override fun onScroll(
        e1: MotionEvent?,
        event: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        minSwipeY += distanceY
        minSwipeX += distanceX
        val sWidth = Resources.getSystem().displayMetrics.widthPixels
        val sHeight = Resources.getSystem().displayMetrics.heightPixels
        // 10 px border is not working code
        val border = 10 * Resources.getSystem().displayMetrics.density.toInt()
        if (event.x < border || event.y < border || event.x > sWidth - border || event.y > sHeight - border)
            return false
        // minSwipeY for slowly increasing brightness & volume on swipe
        // minSwipeX for detecting horizontal swipes
        if (abs(minSwipeX) > 100) {
            if (abs(distanceX) > abs(distanceY)) {
                // Horizontal swipe
                if (distanceX > 0) {
                    // Swipe right for forward (adjust the time as needed)
                    player.seekTo(player.currentPosition - 5000)
                } else {
                    // Swipe left for rewind (adjust the time as needed)
                    player.seekTo(player.currentPosition + 5000)
                }
                minSwipeX = 0f
            } else {
                // Vertical swipe
                if (event.x < sWidth / 2) {
                    // brightness
                    playPauseBtn.visibility = View.GONE
                    binding.brightnessIcon.visibility = View.VISIBLE
                    binding.volumeIcon.visibility = View.GONE

                    val increase = distanceY > 0
                    val newValue = if (increase) brightness + 1 else brightness - 1
                    if (newValue in 0..15) brightness = newValue
                    binding.brightnessIcon.text = brightness.toString()
                    setScreenBrightness(brightness)
                } else {
                    // volume
                    binding.brightnessIcon.visibility = View.GONE
                    binding.volumeIcon.visibility = View.VISIBLE
                    playPauseBtn.visibility = View.GONE

                    val maxVolume = audioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val increase = distanceY > 0
                    val newValue = if (increase) volume + 1 else volume - 1
                    if (newValue in 0..maxVolume) volume = newValue
                    binding.volumeIcon.text = volume.toString()
                    audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                }
                minSwipeY = 0f
            }
            playPauseBtn.visibility = View.VISIBLE
        }
        return true
    }
    private fun setScreenBrightness(value: Int){
        val d = 1.0f/15
        val lp = this.window.attributes
        lp.screenBrightness = d * value
        this.window.attributes = lp
    }
    //used to get path of video selected by user (if column data fails to get path)
    private fun getPathFromURI(context: Context , uri : Uri): String {
        var filePath = ""
        // ExternalStorageProvider
        val docId = DocumentsContract.getDocumentId(uri)
        val split = docId.split(':')
        val type = split[0]

        return if ("primary".equals(type, ignoreCase = true)) {
            "${Environment.getExternalStorageDirectory()}/${split[1]}"
        } else {
            //getExternalMediaDirs() added in API 21
            val external = context.externalMediaDirs
            if (external.size > 1) {
                filePath = external[1].absolutePath
                filePath = filePath.substring(0, filePath.indexOf("Android")) + split[1]
            }
            filePath
        }
    }
}