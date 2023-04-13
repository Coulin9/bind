package com.zhaoxinyu.bind.ui.watchTogether

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.media.MediaFormat
import android.os.*
import android.os.Build.VERSION
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.video.VideoFrameMetadataListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.render.SimpleRenderer
import com.kuaishou.akdanmaku.render.TypedDanmakuRenderer
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.WatchTestFragmentBinding
import com.zhaoxinyu.bind.logic.`watch-together`.FrameQueue
import com.zhaoxinyu.bind.logic.`watch-together`.MediaGrabber
import com.zhaoxinyu.bind.logic.`watch-together`.MediaInfo
import com.zhaoxinyu.bind.logic.`watch-together`.MediaPusher
import com.zhaoxinyu.bind.logic.entities.RealTimeMSG
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.ui.HomeFragment
import com.zhaoxinyu.bind.utils.LogUtil
import com.zhaoxinyu.bind.utils.Utils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.bytedeco.ffmpeg.global.avcodec
import kotlin.concurrent.thread
import kotlin.random.Random

class WatchTestFragment:Fragment() {
    companion object{
        const val UPDATE_PROGRESS_BAR=1
        const val PLAY_BACK_PROGRESS="timeStamp"
        const val REALTIME_MSG="realTimeMSG"
        val CHAT_RECORD="chatRecord"+"${MainActivity.loggedUser?.id}"
    }

    /**
     * viewBinding对象
     */
    private val binding:WatchTestFragmentBinding get() = _binding!!
    private var _binding:WatchTestFragmentBinding?=null

    /**
     * 弹幕渲染器
     */
    private val renderer by lazy {
        TypedDanmakuRenderer(
            SimpleRenderer()
        )
    }

    /**
     * 弹幕播放器
     */
    private lateinit var danmakuPlayer:DanmakuPlayer

    /**
     * 窗口控制器
     */
    private lateinit var windowController:WindowInsetsControllerCompat



    /**
     * NetSyncPlayer实例
     */
    private var syncPlayer:NetSyncVideoPlayer?=null

    /**
     * MediaGrabber实例
     */
    private var mediaGrabber:MediaGrabber?=null

    /**
     * MediaPusher实例
     */
    private var mediaPusher:MediaPusher?=null

    /**
     * 本地视频地址
     */
    private var videoPath=""

    /**
     * 房间状态检测线程
     */
    private var roomStateCheckTask:RoomStateCheckTask?=null

    /**
     * 用于roomStateCheckTask和视频播放进行同步的标志位
     */
    private var taskStartOnResume=true

    /**
     * 获取播放控制命令的线程
     */
    private var commandFetchTask:CommandFetchTask?=null

    /**
     * 获取实时消息的线程
     */
    private var messageFetchTask:MessageFetchTask?=null

    /**
     * 表示进度条是否被手动拖动的标志位
     */
    private var seekbarHolding=false

    /**
     * true表示播放按钮，false表示暂停按钮
     */
    private var isPlayBtn=true

    /**
     * 视频控件是否显示
     */
    private var showController=false

    /**
     * 展示实时通讯消息的RecyclerView的Adapter
     */
    private var adapter:RealTimeMSGAdapter?=null

    /**
     * 选择本地视频的结果处理回调
     */
    private val activityResultLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        if(it!=null&&it.resultCode== Activity.RESULT_OK){
            val url=it.data?.data!!
            //设定要查询的数据
            val projection= arrayOf<String>(MediaStore.Video.Media.DATA,MediaStore.Video.Media.SIZE)
            //使用contentProvider进行查询
            val cursor=MainActivity.context?.contentResolver?.query(url,projection,null,null,null)
            cursor?.moveToFirst()
            //保存视频文件的路径和大小
            val path=cursor?.getString(0)
            val size=cursor?.getString(1)
            LogUtil.d("selectVideo","path:${path},size:${size}")

            videoPath=path!!

            //开启放映室
            startStreaming(videoPath,0L)


        }
    }

    /**
     * 文件读取及相册访问权限申请的回调处理
     */
    private val permissionResultLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){res->
        if (res){
            launchActivityForRes()
        }else{
            Toast.makeText(requireContext(),"你拒绝了文件读写权限，app将无法读取本地视频",Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 子线程更新UI的Handler
     */
    private val handler=object :Handler(Looper.getMainLooper()){
        override fun handleMessage(msg: Message) {
            when(msg.what){
                UPDATE_PROGRESS_BAR->{
                    if(!seekbarHolding){
                        val stamp=msg.data.getLong(PLAY_BACK_PROGRESS)
                        val totalSec=stamp/1000000
                        val min=totalSec/60
                        val sec=totalSec%60
                        binding.nowProgressText.text="${min}:${sec}"
                        binding.progressBar.progress=totalSec.toInt()
                    }
                }
            }
        }
    }


    /**
     * 房间状态检测线程类
     */
    inner class RoomStateCheckTask:Thread(){
        private var isRunning=false

        override fun run() {
            while (isRunning&&MainActivity.loggedUser!=null){
                Network.watchTogetherService.checkVideoOn(MainActivity.loggedUser!!)
                    .onErrorReturn { e->
                        val res=Result(false,e.message!!,null)
                        LogUtil.e("[watchTogetherService]:checkVideoOn",e.message!!)
                        return@onErrorReturn res
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { r->
                        if(r.success){
                            //如果房间状态是开启但syncPlayer未初始化，表明需要拉取视频流播放了。
                            if((r.ojb as Boolean)&&syncPlayer!=null&&syncPlayer?.isClosed!!){
                                sync(false)
                                binding.selectVideoBtn.isEnabled=false
                                binding.stateChangeBtn.isEnabled=true
                                changeStateChangeBtn(false)
                            }
                            //如果房间状态是关闭且syncPlayer实例初始化了，那么就需要停止视频播放.
                            if(!(r.ojb as Boolean)&&syncPlayer!=null&&!syncPlayer?.isClosed!!){
                                closeStreaming()
                                binding.syncBtn.isEnabled=true
                                binding.selectVideoBtn.isEnabled=true
                                binding.totalLengthText.text="0:0"
                                binding.nowProgressText.text="0:0"
                                binding.progressBar.progress=0
                                videoPath=""
                                //视频播放停止时不能进行暂停以及继续
                                binding.stateChangeBtn.isEnabled=false
                                changeStateChangeBtn(true)
                            }

                            //如果房间处于开启状态，那么就需要开始接收控制命令
                            if((r.ojb as Boolean)&&commandFetchTask==null){
                                commandFetchTask=CommandFetchTask()
                                commandFetchTask?.startFetch()
                            }
                            //如果房间处于关闭状态，那么就停止接收命令
                            if(!(r.ojb as Boolean)&&commandFetchTask!=null){
                                commandFetchTask?.endFetch()
                                commandFetchTask=null
                            }
                        }else{
                            endCheck()
                        }

                        LogUtil.d("[watchTogetherService]:checkVideoOn",r.msg)
                    }
                sleep(700)
            }
            isRunning=false
        }

        fun startCheck(){
            if(!isRunning){
                isRunning=true
                this.start()
            }
        }

        fun endCheck(){
            if(isRunning){
                isRunning=false
            }
        }
    }

    /**
     * 不断从服务器获取播放控制命令的线程类
     */
    inner class CommandFetchTask():Thread(){
        val isRunning get() = _isRunning
        private var _isRunning=false

        override fun run() {
            //暂停时的时间记录
            var timeRecord=0L
            while (isRunning&&MainActivity.loggedUser!=null){
                //循环获取控制命令
                Network.watchTogetherService.getProgress(MainActivity.loggedUser!!)
                    .onErrorReturn { e->
                        val res=Result(false,e.message!!,null)
                        LogUtil.e("[watchTogetherService]:getProgress",e.message!!)
                        return@onErrorReturn res
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { r->
                        if(r.success){
                            LogUtil.e("[watchTogetherService]:getProgress","${r.msg}-${r.ojb}")
                            val command=(r.ojb as Double).toLong()
                            if(command<0){
                                when(command){
                                    NetSyncVideoPlayer.ORDER_PAUSE->{
                                        //暂停时，记录下当前播放位置
                                        if(mediaGrabber!=null){
                                            //如果是主用户
                                            //记录当前播放位置
                                            timeRecord=syncPlayer?.realTimeStamp!!
                                        }
                                        //停止播放与推流
                                        mediaGrabber?.stop()
                                        mediaPusher?.stop()
                                        syncPlayer?.release()
                                        if(mediaGrabber==null) syncPlayer?.sourcePath=null

                                        changeStateChangeBtn(true)
                                    }
                                    NetSyncVideoPlayer.ORDER_START->{
                                        if(mediaGrabber!=null&&!mediaGrabber?.isRunning!!){
                                            //对于主用户，重新开始推流播放
                                            startStreaming(videoPath,timeRecord)
                                        }else if(mediaGrabber==null){
                                            var tryB=true
                                            //对于从用户，进行同步
                                            while (tryB){
                                                try{
                                                    sync(true)
                                                    tryB=false
                                                }catch (e:Exception){
                                                    tryB=true
                                                    e.printStackTrace()
                                                    syncPlayer?.release()
                                                    sleep(100)
                                                    LogUtil.e("ProgressSyncError",e.message!!)
                                                }
                                            }
                                        }
                                        changeStateChangeBtn(false)
                                    }
                                    NetSyncVideoPlayer.ORDER_NONE->{

                                    }
                                }
                            }else{
                                /**
                                 * 进度同步的逻辑
                                 */
                                if(mediaGrabber!=null){
                                    //如果是主用户的话
                                    //停止播放与推流
                                    mediaGrabber?.stop()
                                    mediaPusher?.stop()
                                    syncPlayer?.release()
                                    //从新的位置开始播放
                                    startStreaming(videoPath,command*1000000)
                                }else{
                                    //如果是从用户的话
                                    syncPlayer?.release()
                                    var tryB=true
                                    //对于从用户，进行同步
                                    while (tryB){
                                        try{
                                            LogUtil.e("ProgressSyncError",syncPlayer?.sourcePath!!)
                                            sync(true,command*1000000)
                                            tryB=false
                                        }catch (e:Exception){
                                            tryB=true
                                            e.printStackTrace()
                                            syncPlayer?.release()
                                            sleep(100)
                                            LogUtil.e("ProgressSyncError",e.message!!)
                                        }
                                    }
                                }
                                changeStateChangeBtn(false)
                            }
                        }
                    }
                sleep(555)
            }
            _isRunning=false
        }

        fun startFetch(){
            if(!isRunning){
                _isRunning=true
                this.start()
            }
        }

        fun endFetch(){
            if(isRunning){
                _isRunning=false
            }
        }
    }

    /**
     * 不断地从服务器获取新消息的线程类
     */
    inner class MessageFetchTask:Thread(){
        val isRunning get() = _isRunning
        private var _isRunning=false
        override fun run() {
            while (isRunning&&MainActivity.loggedUser!=null){
                Network.watchTogetherService.getNewMessage(MainActivity.loggedUser!!)
                    .onErrorReturn { e->
                        val res=Result(false,e.message!!,null)
                        LogUtil.e("[watchTogetherService]:getNewMessage",e.message!!)
                        return@onErrorReturn res
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { r->
                        /**
                         * 接收到消息后的动作
                         * 目前由于只有弹幕所以就先暂时显示在弹幕上
                         */
                        LogUtil.e("[watchTogetherService]:getNewMessage",r.msg)
                        if(r.success){
                            //发送弹幕
                            val msg=r.ojb as String
                            sendDanmaku(danmakuPlayer,msg)

                            //在消息列表显示
                            adapter?.msgList?.add(RealTimeMSG(RealTimeMSG.TYPE_LEFT,msg))
                            adapter?.notifyItemInserted(adapter?.itemCount!!-1)
                            binding.msgRecyclerView.scrollToPosition(adapter?.itemCount!!-1)
                        }

                    }
                sleep(1117)
            }
            _isRunning=false

        }

        fun startFetch(){
            if(!isRunning){
                _isRunning=true
                this.start()
            }
        }

        fun endFetch(){
            if(isRunning){
                _isRunning=false
            }
        }
    }


    /**
     * 初始化弹幕播放器
     */
    private fun initAkdanmaku(){
        danmakuPlayer = DanmakuPlayer(renderer)
        danmakuPlayer.bindView(binding.danmakuView)
        danmakuPlayer.start()
        //发送弹幕
        binding.sendDanmaBtn.setOnClickListener {
            val msg=binding.danmaInputText.editText?.text?.toString()!!
            if(msg!=null&&msg!=""){
                val danmaku=DanmakuItemData(
                    Random.nextLong(),
                    danmakuPlayer.getCurrentTimeMs()+500,
                    msg,
                    DanmakuItemData.DANMAKU_MODE_ROLLING,
                    24,
                    Color.WHITE,
                    9,
                    DanmakuItemData.DANMAKU_STYLE_NONE,
                    9
                )
                danmakuPlayer.send(danmaku)

                //将发送的弹幕插入到消息列表中
                adapter?.msgList?.add(RealTimeMSG(RealTimeMSG.TYPE_RIGHT,binding.danmaInputText.editText?.text?.toString()!!))
                adapter?.notifyItemInserted(adapter?.itemCount!!-1)
                binding.msgRecyclerView.scrollToPosition(adapter?.itemCount!!-1)


                binding.danmaInputText.editText?.text?.clear()
                //向服务器发送通讯消息
                Network.watchTogetherService.sendMessage(MainActivity.loggedUser!!,msg)
                    .onErrorReturn { e->
                        val res=Result(false,e.message!!,null)
                        LogUtil.e("[watchTogetherService]:sendMessage",e.message!!)
                        return@onErrorReturn res
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { r->
                        LogUtil.e("[watchTogetherService]:sendMessage",r.msg)
                    }
            }else{
                Toast.makeText(requireContext(),"你不能发送空的消息！",Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 发送一条弹幕
     */
    private fun sendDanmaku(player:DanmakuPlayer,msg:String){
        val danmaku=DanmakuItemData(
            Random.nextLong(),
            danmakuPlayer.getCurrentTimeMs()+500,
            msg,
            DanmakuItemData.DANMAKU_MODE_ROLLING,
            24,
            Color.WHITE,
            9,
            DanmakuItemData.DANMAKU_STYLE_NONE,
            9
        )
        player.send(danmaku)
    }

    /**
     * 更改暂停播放按钮的状态
     * @param isPlay true更改为播放按钮，false更改为暂停按钮
     */
    private fun changeStateChangeBtn(isPlay:Boolean){
        isPlayBtn=isPlay
        if(isPlay){
            binding.stateChangeBtnIcon.setImageResource(R.drawable.play_arrow_fill1_wght400_grad0_opsz24)
        }else{
            binding.stateChangeBtnIcon.setImageResource(R.drawable.pause_fill1_wght400_grad0_opsz24)
        }
    }
    /**
     * 初始化ExoPlayer
     */
    /*private fun initExoPlayer(path:String){
        exoPlayer?.release()
        exoPlayer=null

        exoPlayer=ExoPlayer.Builder(requireContext()).build()
        val mediaItem=MediaItem.fromUri(Uri.parse(path))
        exoPlayer?.addMediaItem(mediaItem)
        //exoPlayer?.setWakeMode(ExoPlayer.WAKE_MODE_NETWORK)
        //binding.playerView.player=exoPlayer
        exoPlayer?.prepare()

        //获取视频宽高并依据视频的宽和高设置surfaceView的宽高.
        val videoWidth=exoPlayer?.videoFormat?.width
        val videoHeight=exoPlayer?.videoFormat?.height
    }*/

    /**
     * 选择本地视频推流并播放
     */
    fun startStreaming(path: String,startPoint:Long){
        try {
            //第一步：推流
            pushVideo(path,MainActivity.HWASetting,startPoint)
            //记录播放起始点
            syncPlayer?.realTimeStamp=startPoint
            mediaPusher?.updatePlayTimeStamp(startPoint)

            //记录一下视频的总时长
            val length=mediaGrabber?.mediaInfo?.lengthInTime
            //记录一帧持续的时间
            syncPlayer?.frameTime=((1000*1000000)/(mediaGrabber?.mediaInfo?.frameRate!!*1000)).toLong()
            //第二步，初始化播放器
            syncPlayer?.apply {
                //sourcePath="http://cdn.hklive.tv/xxxx/81/index.m3u8"
                //sourcePath=path
                sourcePath=Network.WATCH_STREAM_URL
                surView=binding.videoSurf
                mInfo=mediaPusher?.mediaInfo
                /**
                 * 用于同步的时间偏移量。
                 * 由于观看的是直播流，所以实际上不论当前播放的是文件实际的哪个位置，播放器的
                 * timeStamp都是从0开始的，因此需要借助timeOffset去找到实际在文件中的播放
                 * 位置
                 */
                var lastTime=System.currentTimeMillis()
                //设置帧监听
                var lastPST=0L
                frameListener=object :VideoFrameMetadataListener{
                    override fun onVideoFrameAboutToBeRendered(
                        presentationTimeUs: Long,
                        releaseTimeNs: Long,
                        format: Format,
                        mediaFormat: MediaFormat?
                    ) {
                        //更新用于推流同步的播放进度
                        mediaPusher?.updatePlayTimeStamp(syncPlayer?.realTimeStamp!!)
                        LogUtil.e("updatePusherTimeStamp",syncPlayer?.realTimeStamp?.toString()!!)

                        val nowTime=System.currentTimeMillis()
                        if(nowTime-lastTime>=500){
                            //更新服务器记录的播放进度，每500ms更新一次
                            val o=Network.watchTogetherService.updatePlayingTimeStamp(MainActivity.loggedUser!!,syncPlayer?.realTimeStamp!!)
                                .onErrorReturn { e->
                                    val res=Result(false,e.message!!,null)
                                    LogUtil.e("[watchTogetherService]:updatePlayingTimeStamp",e.message!!)
                                    return@onErrorReturn res
                                }
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe { r->
                                    LogUtil.d("[watchTogetherService]:updatePlayingTimeStamp",r.msg)
                                }
                            lastTime=nowTime
                        }

                        //更新UI显示的播放进度
                        val msg=Message()
                        msg.what= UPDATE_PROGRESS_BAR
                        msg.data.putLong(PLAY_BACK_PROGRESS,syncPlayer?.realTimeStamp!!)
                        handler.sendMessage(msg)

                        //更新实时播放进度
                        syncPlayer?.realTimeStamp=syncPlayer?.realTimeStamp!!+(presentationTimeUs-lastPST)
                        lastPST=presentationTimeUs

                        //播放第一帧的时候开启RoomStateCheckTask
                        if(!taskStartOnResume){
                            taskStartOnResume=true
                            if(roomStateCheckTask==null) roomStateCheckTask=RoomStateCheckTask()
                            roomStateCheckTask?.startCheck()
                        }
                        LogUtil.e("SyncPlayer",syncPlayer?.realTimeStamp?.toString()!!)
                        //LogUtil.e("WatchTestFragment","Video is Playing!")
                    }
                }

                //获取视频总时长
                lengthInTime=length?:0L

                //设置显示的视频总时长
                val totalSec=lengthInTime/1000000
                val minutes=totalSec/60
                val sec=totalSec%60
                binding.totalLengthText.text="${minutes}:${sec}"
                binding.progressBar.max=totalSec.toInt()

                //作为主用户，不需要等待同步
                onPrepared=null
                initPlayer()
            }

            //告诉服务器放映室已开启
            val o=Network.watchTogetherService.startStreaming(
                hashMapOf("user" to Gson().toJson(MainActivity.loggedUser),"mInfo" to Gson().toJson(mediaPusher?.mediaInfo)))
                .onErrorReturn { e->
                    val res=Result(false,e.message!!,null)
                    LogUtil.e("[watchTogetherService]:startStreaming",e.message!!)
                    return@onErrorReturn res
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { r->
                    LogUtil.d("[watchTogetherService]:startStreaming",r.msg)
                    if(!r.success){
                        closeStreaming()
                        Toast.makeText(requireContext(),"开播失败！请检查网络连接",Toast.LENGTH_SHORT).show()
                    }else{
                        syncPlayer?.isClosed=false
                        //开播成功，让按钮灰掉
                        //让主用户无法进行同步操作
                        binding.syncBtn.isEnabled=false
                        //在手动停止播放前无法再选择新的本地文件进行推流播放
                        binding.selectVideoBtn.isEnabled=false

                        binding.stateChangeBtn.isEnabled=true
                        changeStateChangeBtn(false)
                    }
                }
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    /**
     * 结束推流
     * 向服务器发送结束指令
     */
    fun stopStreaming(){
        Network.watchTogetherService.closeStreaming(MainActivity.loggedUser!!)
            .onErrorReturn { e->
                val res=Result(false,e.message!!,null)
                LogUtil.e("[watchTogetherService]:closeStreaming",e.message!!)
                return@onErrorReturn res
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { r->
                LogUtil.d("[watchTogetherService]:closeStreaming",r.msg)
            }
    }
    /**
     * 结束推流实际上需要做的操作
     */
    private fun closeStreaming(){
        //需要关闭播放器，关闭Grabber和Pusher
        mediaGrabber?.stop()
        mediaPusher?.stop()
        syncPlayer?.release()
        syncPlayer?.sourcePath=null
        syncPlayer?.isClosed=true
    }

    /**
     * 进度同步
     */
    private var requireStartTime=0L
    private var requireEndTime=0L
    fun sync(jumpSyncWait:Boolean,syncFrom:Long=-1L){
        var syncWait=true
        var playingTimeStamp=0L
        //标记请求开始时间
        requireStartTime=System.currentTimeMillis()
        Observable.concat(listOf(
            Network.watchTogetherService.getMediaInfo(MainActivity.loggedUser!!),
            Network.watchTogetherService.getPlayingTimeStamp(MainActivity.loggedUser!!),
            Network.watchTogetherService.getCacheTime(MainActivity.loggedUser!!)))
            .onErrorReturn { e->
                val res=Result(false,e.message!!,null)
                LogUtil.e("[watchTogetherService]:getPlayingTimeStampOrGetMediaInfo",e.message!!)
                return@onErrorReturn res
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {r->
                //如果是获取媒体信息返回的结果
                if(r.success&&r.msg=="Get MediaInfo Success!"){
                    /**
                     * 获得了媒体信息，就可以设置视频总时长和每一帧的时长还有媒体信息了
                     */
                    LogUtil.e("[watchTogetherService]:getMediaInfo",r.ojb?.toString()!!)
                    val mInfo= Gson().fromJson((r.ojb as String),MediaInfo::class.java)
                    syncPlayer?.lengthInTime=mInfo.lengthInTime
                    val totalSec=syncPlayer?.lengthInTime!!/1000000
                    val minutes=totalSec/60
                    val sec=totalSec%60
                    binding.totalLengthText.text="${minutes}:${sec}"
                    binding.progressBar.max=totalSec.toInt()
                    syncPlayer?.frameTime=((1000*1000000)/(mInfo?.frameRate!!*1000)).toLong()
                    syncPlayer?.mInfo=mInfo
                }
                //如果是获取PlayingTimeStamp返回的结果。
                if(r.success&&r.msg=="Get Playing Timestamp Success!"){
                    LogUtil.e("[watchTogetherService]:getPlayingTimeStamp",r.ojb?.toString()!!)
                    //开播小于1s，不进行同步等待
                    val timeStamp=(r.ojb as Double).toLong()
                    playingTimeStamp=if(syncFrom==-1L) timeStamp else syncFrom
                    if(timeStamp<600*1000){
                        syncWait=false
                    }
                }
                //如果获取到了cacheTime
                if(r.success&&r.msg=="Get Cache Time Success!"){
                    LogUtil.e("[watchTogetherService]:getCacheTime",r.toString())
                    //拉取时为了同步，需要计算同步等待时间
                    syncPlayer?.setCacheTime((r.ojb as Double).toLong())
                    //标记请求结束时间
                    requireEndTime=System.currentTimeMillis()

                    //计算请求时间
                    val requireTime=requireEndTime-requireStartTime

                    //同步时，如果syncPlayer的地址等为空，那么就需要初始化新的syncPlayer
                    if(syncPlayer?.sourcePath==null){
                        syncPlayer?.apply {
                            //sourcePath="http://cdn.hklive.tv/xxxx/81/index.m3u8"
                            //sourcePath=path
                            /**
                             * 如果按下同步按钮时播放器还没有初始化，那么认为当前用户为从用户。
                             * 实际上凡是调用了sync()函数的都算是从用户。
                             * 帧监听不需要同步上传播放进度
                             */
                            sourcePath=Network.WATCH_STREAM_URL
                            surView=binding.videoSurf
                        }
                    }
                    //依据syncWait标识位来决定是否进行同步等待
                    if(syncWait&&!jumpSyncWait){
                        //实现准备回调接口，在准备完成时进行等待同步
                        syncPlayer?.onPrepared=object :NetSyncVideoPlayer.OnPreparedCallback{
                            override fun onPrepared() {
                                val loadTime=syncPlayer?.loadTime
                                /**
                                 * 计算同步等待时间：
                                 */
                                val waitTime=syncPlayer?.getCacheTime()!!-loadTime!!-syncPlayer?.getLiveOffset()!!-requireTime

                                //依据同步等待时间更新实时播放进度
                                //val rtstamp=playBackTimeStamp+syncPlayer?.getCacheTime()!!+loadTime+requireTime-syncPlayer?.getLiveOffset()!!
                                val rtstamp=playingTimeStamp+syncPlayer?.getCacheTime()!!-requireTime*2
                                if(rtstamp>=0) syncPlayer?.realTimeStamp=rtstamp

                                //设置用于更新UI实时播放进度的frameListener
                                var lastPTS=0L
                                syncPlayer?.setPlayTimeFrameListener(object :VideoFrameMetadataListener{
                                    override fun onVideoFrameAboutToBeRendered(
                                        presentationTimeUs: Long,
                                        releaseTimeNs: Long,
                                        format: Format,
                                        mediaFormat: MediaFormat?
                                    ) {
                                        //更新UI显示的播放进度
                                        val msg=Message()
                                        msg.what= UPDATE_PROGRESS_BAR
                                        msg.data.putLong(PLAY_BACK_PROGRESS,syncPlayer?.realTimeStamp!!)
                                        handler.sendMessage(msg)

                                        //更新实时播放进度，但需要注意的是，最开始几帧由于还不知道媒体信息（因为获取媒体信息的网络请求还没有返回），也就不知道一帧持续的时间。
                                        syncPlayer?.realTimeStamp=syncPlayer?.realTimeStamp!!+(presentationTimeUs-lastPTS)
                                        lastPTS=presentationTimeUs

                                        LogUtil.e("SyncPlayer",syncPlayer?.realTimeStamp?.toString()!!)
                                        //LogUtil.e("WatchTestFragment","Video is Playing!")
                                    }
                                })

                                //LogUtil.e("[SyncPlayer]:waitTime",waitTime.toString())
                                LogUtil.e("[SyncPlayer]:cacheTime",syncPlayer?.getCacheTime()!!.toString())
                                LogUtil.e("[SyncPlayer]:requireTime",requireTime.toString())
                                if(waitTime>=0){
                                    syncPlayer?.pauseWithoutSync()
                                    Thread.sleep(waitTime/1000)
                                    syncPlayer?.startWithoutSync()
                                }else{
                                    //syncPlayer?.destroy()
                                    LogUtil.e("[SyncPlayer]:Sync","Loading overtime!")
                                }

                                /**
                                 * 获取并显示视频总时长
                                 */
                                //syncPlayer?.getLengthFromServer(binding.totalLengthText,binding.progressBar)
                            }
                        }
                    }else syncPlayer?.onPrepared=object :NetSyncVideoPlayer.OnPreparedCallback{
                        override fun onPrepared() {
                            /**
                             * 获取并显示视频总时长
                             */
                            //syncPlayer?.getLengthFromServer(binding.totalLengthText,binding.progressBar)

                            val loadTime=syncPlayer?.loadTime


                            //依据同步等待时间更新实时播放进度
                            //val rtstamp=playBackTimeStamp+syncPlayer?.getCacheTime()!!+loadTime+requireTime-syncPlayer?.getLiveOffset()!!
                            val rtstamp=playingTimeStamp+loadTime!!+requireTime
                            if(rtstamp>=0) syncPlayer?.realTimeStamp=rtstamp

                            //设置用于更新UI实时播放进度的frameListener
                            var lastPTS=0L
                            syncPlayer?.setPlayTimeFrameListener(object :VideoFrameMetadataListener{
                                override fun onVideoFrameAboutToBeRendered(
                                    presentationTimeUs: Long,
                                    releaseTimeNs: Long,
                                    format: Format,
                                    mediaFormat: MediaFormat?
                                ) {
                                    //更新UI显示的播放进度
                                    val msg=Message()
                                    msg.what= UPDATE_PROGRESS_BAR
                                    msg.data.putLong(PLAY_BACK_PROGRESS,syncPlayer?.realTimeStamp!!)
                                    handler.sendMessage(msg)

                                    syncPlayer?.realTimeStamp=syncPlayer?.realTimeStamp!!+(presentationTimeUs-lastPTS)
                                    lastPTS=presentationTimeUs

                                    LogUtil.e("SyncPlayer",syncPlayer?.realTimeStamp?.toString()!!)
                                    //LogUtil.e("WatchTestFragment","Video is Playing!")
                                }
                            })
                        }
                    }
                    try {
                        syncPlayer?.initPlayer()
                        syncPlayer?.startWithoutSync()
                        syncPlayer?.isClosed=false
                    }catch (e:Exception){
                        e.printStackTrace()
                    }
                }
            }
    }
    /*private fun syncWithCacheTime(syncWait:Boolean,playBackTimeStamp:Long){
        if(r.success){
        }
    }*/

    /**
     * 启动系统相册并选择视频
     */
    private fun launchActivityForRes(){
        //防止返回后RoomStateCheckTask自动开启造成Bug
        taskStartOnResume=false
        //启动系统相册选择视频
        val intent=Intent()
        intent.apply {
            action=Intent.ACTION_PICK
            type="video/*"
        }
        activityResultLauncher.launch(intent)
    }

    /**
     * 初始化mediaGrabber与mediaPusher以及开始推流
     */
    private fun pushVideo(path:String?,hwa:Boolean,startPoint: Long){
        if(path!=null){
            val cacheQueue=FrameQueue()
            MediaGrabber.HWA=hwa
            MediaPusher.HWA=hwa
            mediaGrabber=MediaGrabber(path,cacheQueue)
            val mInfo=mediaGrabber?.mediaInfo?.copy()
            mInfo?.apply {
                format="flv"
                imageInfo.videoCodec= avcodec.AV_CODEC_ID_H264
                audioInfo.audioCodec= avcodec.AV_CODEC_ID_AAC
                audioInfo.sampleRate=44100
                //imageInfo.width=1280
                //imageInfo.height=720
                //imageInfo.videoBiteRate=1000
                if(imageInfo.videoBiteRate==0){
                    imageInfo.videoBiteRate=8500*1024
                }
            }
            LogUtil.d("MediaInfo",mInfo.toString())
            mediaPusher=MediaPusher(Network.STREAM_URL,cacheQueue,mInfo!!)
            try{
                mediaGrabber?.start()
                mediaGrabber?.setTimeStamp(startPoint)
                mediaPusher?.start()
            }catch (e:Exception){
                LogUtil.e("PushError",e.message!!)
                Toast.makeText(requireContext(),"播放失败！",Toast.LENGTH_SHORT).show()
            }
        }
        //val codec=avcodec.avcodec_find_decoder_by_name("h264_mediacodec")
        /*val grabber=FFmpegFrameGrabber(path)
        grabber.videoCodecName="h264_mediacodec"
        grabber.setOption("fflags", "genpts")
        grabber.setVideoOption("threads", "0")
        try{
            grabber.start()
        }catch (e:Exception){
            e.printStackTrace()
        }
        while(true){
            println("Grabbing:${grabber.grab().timestamp}")
        }*/
    }

    /**
     * 进入全屏模式
     */
    private fun enterFullScreen(){
        //隐藏状态栏
        windowController.hide(WindowInsetsCompat.Type.statusBars())
        //隐藏导航栏
        windowController.hide(WindowInsetsCompat.Type.navigationBars())
        windowController.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * 退出全屏模式
     */
    private fun existFullScreen(){
        //显示状态栏
        windowController.show(WindowInsetsCompat.Type.statusBars())
        //显示导航栏
        windowController.show(WindowInsetsCompat.Type.navigationBars())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {

        //LogUtil.d("ConfigChanged",newConfig.orientation.toString())
        //横竖屏切换时就改变布局来让视频全屏。
        if(newConfig.orientation==Configuration.ORIENTATION_PORTRAIT){
            //如果是竖屏，依据屏幕宽度重新设置videoContainer的大小,固定视频预览窗比例16:9

            existFullScreen()

            binding.fullScreenBtnIcon.setImageResource(R.drawable.fullscreen_fill1_wght400_grad0_opsz24)

            val width=Resources.getSystem().displayMetrics.widthPixels
            val height=(width/16)*9
            val params=binding.videoContainer.layoutParams
            params.width=width
            params.height=height
            binding.videoContainer.layoutParams=params

            //调整视频画幅大小
            syncPlayer?.sizeMatch(width,height)

            //展示底部导航栏
            HomeFragment.buttonNavBar?.visibility=View.VISIBLE
        }else{

            enterFullScreen()

            binding.fullScreenBtnIcon.setImageResource(R.drawable.close_fullscreen_fill1_wght400_grad0_opsz24)

            val width=requireActivity().window.decorView.height
            val height=requireActivity().window.decorView.width
            val params=binding.videoContainer.layoutParams
            params.width=width
            params.height=height
            binding.videoContainer.layoutParams=params

            //调整视频画幅大小
            syncPlayer?.sizeMatch(width,height)
            
            //隐藏底部导航栏
            HomeFragment.buttonNavBar?.visibility=View.GONE
        }
        super.onConfigurationChanged(newConfig)
    }

    /**
     * 界面重建时保存实时消息
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(REALTIME_MSG,Gson().toJson(adapter?.msgList))
        super.onSaveInstanceState(outState)
    }

    /**
     * onCreate回调
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //初始化syncPlayer
        syncPlayer=NetSyncVideoPlayer(requireContext())

        //初始化实时消息获取线程
        messageFetchTask=MessageFetchTask()
        messageFetchTask?.startFetch()

        LogUtil.d("WatchTestFragment","onCreate")
        //初始化MediaPlayer
        //initMediaPlayer("http://cdn.hklive.tv/xxxx/81/index.m3u8")
        //initMediaPlayer(Network.WATCH_STREAM_URL)
    }

    /**
     * onCreateView回调
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        //初始化viewBinding对象
        _binding= WatchTestFragmentBinding.inflate(inflater,container,false)

        //初始化窗口控制器
        windowController=WindowCompat.getInsetsController(requireActivity().window,binding.root)


        //获取横竖屏状态
        var orientation=requireContext().resources.configuration.orientation

       if(orientation==Configuration.ORIENTATION_PORTRAIT){
           //如果是竖屏，依据屏幕宽度重新设置videoContainer的大小,固定视频预览窗比例16:9
           val width=Resources.getSystem().displayMetrics.widthPixels
           val height=(width/16)*9
           val params=binding.videoContainer.layoutParams
           params.width=width
           params.height=height
           binding.videoContainer.layoutParams=params

           //展示底部导航栏
           HomeFragment.buttonNavBar?.visibility=View.VISIBLE
       }else{
           //如果是横屏，全屏并且播放窗口直接填满整个屏幕
           //让应用全屏

           enterFullScreen()

           val width=requireActivity().window.decorView.height
           val height=requireActivity().window.decorView.width
           val params=binding.videoContainer.layoutParams
           params.width=width
           params.height=height
           binding.videoContainer.layoutParams=params

           //并且隐藏底部导航栏
           HomeFragment.buttonNavBar?.visibility=View.GONE
       }


        //surfaceHolder初始化
        //surHolder=binding.videoSurf.holder

        //初始化视频进行播放的视图
        syncPlayer?.surView=binding.videoSurf
        syncPlayer?.videoContainer=binding.videoContainer

        //初始化AkDanmaku弹幕
        initAkdanmaku()

        //初始化RecyclerView
        val msgs=Gson().fromJson<List<RealTimeMSG>>(savedInstanceState?.getString(REALTIME_MSG),
            Utils.getType(List::class.java,RealTimeMSG::class.java)
        )
        //获取保存在本地的聊天记录
        val records=Gson().fromJson<List<RealTimeMSG>>(MainActivity.sharedPreference.getString(
            CHAT_RECORD,null),Utils.getType(List::class.java,RealTimeMSG::class.java))
        adapter=RealTimeMSGAdapter(mutableListOf<RealTimeMSG>(),requireContext())

        if(records!=null){
            adapter?.msgList?.addAll(records)
        }else if(msgs!=null){
            adapter?.msgList?.addAll(msgs)
        }
        binding.msgRecyclerView.adapter=adapter
        binding.msgRecyclerView.layoutManager=LinearLayoutManager(requireContext())



        //视频选择按钮的监听回调
        binding.selectVideoBtn.setOnClickListener {

            //先检查一下是否有权限
            var permissionCode:String?=null
            //安卓13及以上申请视频读取权限，否则申请存储读取权限
            if(VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
                //val perm=ContextCompat.checkSelfPermission(requireContext(),Manifest.permission.READ_MEDIA_VIDEO)
                permissionCode=Manifest.permission.READ_MEDIA_VIDEO
            }else{
                permissionCode=Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if(
                ContextCompat.checkSelfPermission(requireContext(),permissionCode)!=PackageManager.PERMISSION_GRANTED
            ){
                //没有权限就手动请求权限
                permissionResultLauncher.launch(permissionCode)
            }else{
                //有权限的话就直接打开系统相册
                launchActivityForRes()
            }
        }

        //设置同步按钮的监听回调
        binding.syncBtn.setOnClickListener {
            sync(false)
        }

        //设置关闭按钮的监听回调
        binding.stopStreamingBtn.setOnClickListener {
            stopStreaming()
        }

        //进度条拖动回调
        var finalProgress=0L
        binding.progressBar.setOnSeekBarChangeListener(object :OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if(fromUser){
                    finalProgress=progress.toLong()
                    val min=progress/60
                    val sec=progress%60
                    binding.nowProgressText.text="${min}:${sec}"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seekbarHolding=true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Network.watchTogetherService.updateProgress(MainActivity.loggedUser!!, finalProgress)
                    .onErrorReturn { e->
                        val res=Result(false,e.message!!,null)
                        LogUtil.e("[watchTogetherService]:updateProgress",e.message!!)
                        return@onErrorReturn res
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { r ->
                        LogUtil.d("[watchTogetherService]:updateProgress",r.msg)
                    }
                //if(mediaGrabber==null) syncPlayer?.release()
                seekbarHolding=false
            }
        })

        //播放暂停按钮回调
        binding.stateChangeBtn.setOnClickListener {
            if(isPlayBtn){
                syncPlayer?.start()
            }else{
                syncPlayer?.pause()
            }
        }

        //全屏/退出全屏按钮监听回调
        binding.fullScreenBtn.setOnClickListener {
            val activity=requireActivity()
            orientation=requireContext().resources.configuration.orientation
            if(orientation==Configuration.ORIENTATION_PORTRAIT){
               //手动横屏
                activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                binding.fullScreenBtnIcon.setImageResource(R.drawable.close_fullscreen_fill1_wght400_grad0_opsz24)
            }else if(orientation==Configuration.ORIENTATION_LANDSCAPE){
                //手动竖屏
                activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                binding.fullScreenBtnIcon.setImageResource(R.drawable.fullscreen_fill1_wght400_grad0_opsz24)
            }
            thread {
                //一定时间后切换回由重力感应决定
                Thread.sleep(2000)
                activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            LogUtil.d("orientationChanged",orientation.toString())
        }

        //controllerView触摸回调
        binding.controllerView.setOnClickListener {
            showController=!showController
            if(showController){
                binding.controllerBlock.visibility=View.VISIBLE
                binding.syncAndCloseBlock.visibility=View.VISIBLE
            }else{
                binding.controllerBlock.visibility=View.GONE
                binding.syncAndCloseBlock.visibility=View.GONE
            }
        }

        LogUtil.d("WatchTestFragment","onCreateView")
        return binding.root
    }


    override fun onPause() {
        danmakuPlayer.pause()

        roomStateCheckTask?.endCheck()
        roomStateCheckTask=null

        //保存一下聊天记录
        MainActivity.sharedPreference.edit().apply {
            putString(CHAT_RECORD,Gson().toJson(adapter?.msgList))
            apply()
        }

        LogUtil.d("WatchTestFragment","onPause")
        super.onPause()
    }

    override fun onResume() {
        danmakuPlayer.start()

        if(taskStartOnResume){
            roomStateCheckTask=RoomStateCheckTask()
            roomStateCheckTask?.startCheck()
        }
        //当换了头像以后，所有聊天记录的消息的头像都要换
        adapter?.notifyItemRangeChanged(0,adapter?.itemCount!!)

        LogUtil.d("WatchTestFragment","onResume")
        super.onResume()
    }

    override fun onStop() {
        danmakuPlayer.stop()
        LogUtil.d("WatchTestFragment","onStop")
        super.onStop()
    }


    override fun onDestroyView() {
        _binding=null
        super.onDestroyView()
    }

    override fun onDestroy() {
        danmakuPlayer.release()
        syncPlayer?.release()
        syncPlayer=null
        if(roomStateCheckTask!=null&&roomStateCheckTask?.isAlive!!){
            roomStateCheckTask?.endCheck()
            roomStateCheckTask=null
        }
        if(commandFetchTask!=null&&commandFetchTask?.isRunning!!){
            commandFetchTask?.endFetch()
            commandFetchTask=null
        }
        if(messageFetchTask!=null&&messageFetchTask?.isRunning!!){
            messageFetchTask?.endFetch()
            messageFetchTask=null
        }
        LogUtil.d("WatchTestFragment","onDestroy")
        super.onDestroy()
    }
}