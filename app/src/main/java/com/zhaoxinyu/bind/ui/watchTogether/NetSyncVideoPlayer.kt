package com.zhaoxinyu.bind.ui.watchTogether

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.google.android.exoplayer2.C.WAKE_MODE_NETWORK
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.Listener
import com.google.android.exoplayer2.video.VideoFrameMetadataListener
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.logic.`watch-together`.MediaInfo
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.utils.LogUtil
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * 可以进行进度同步的视频播放器
 */
class NetSyncVideoPlayer(
    /**
     * 上下文
     */
    val context: Context
) {
    companion object{
        /**
         * 用于进行同步的指令码
         */
        const val ORDER_PAUSE=-1L
        const val ORDER_NONE=-2L
        const val ORDER_START=-3L
    }
    /**
     * 视频进行展示的surfaceView
     */
    var surView:SurfaceView?=null

    /**
     * 视频显示的容器视图
     */
    var videoContainer:ViewGroup?=null

    /**
     * 进行媒体播放的exoPlayer
     */
    private var player:ExoPlayer?=null

    /**
     * 视频源地址
     */
    var sourcePath:String?=null

    /**
     * 视频总时长
     */
    var lengthInTime=0L

    /**
     * 视频的媒体信息
     */
    var mInfo:MediaInfo?=null

    /**
     * 设置离开当前页面时播放是否暂停
     */
    var leavePause=true

    /**
     * 与服务器进行通讯的service对象
     */
    private val service=Network.watchTogetherService

    /**
     * 当前登陆的用户
     */
    private val user get()=MainActivity.loggedUser

    /**
     * 播放是否终止
     */
    var isClosed=true

    /**
     * 帧回调
     */
    var frameListener:VideoFrameMetadataListener?=null

    /**
     * 准备完成回调
     */
    var onPrepared:OnPreparedCallback?=null

    /**
     * 加载时间，定义为播放器从开始准备到准备完成，播出第一帧所花的时间
     * 每次初始化一次播放器就会计算一次加载时间
     */
    val loadTime get() = _loadTime
    private var _loadTime=0L

    /**
     * 实时播放进度
     */
    var realTimeStamp=0L

    /**
     * 一帧持续的时间
     */
    var frameTime=0L

    /**
     * 推流缓冲时间,它决定了一次同步操作后为了达成同步应该等待的时间
     */
    private var cacheTime=0L

    /**
     * onPrepared回调接口
     */
    interface OnPreparedCallback{
        fun onPrepared()
    }


    constructor(surfaceView:SurfaceView,context: Context):this(context){
        surView=surfaceView
    }

    constructor(path:String,context: Context):this(context){
        sourcePath=path
    }

    constructor(surfaceView:SurfaceView,path:String,context: Context):this(context){
        surView=surfaceView
        sourcePath=path
    }

    /**
     * 开始推送直播流
     */
    /*private fun pushVideo(){
        if(sourcePath!=null){
            val cacheQueue= FrameQueue()
            MediaGrabber.HWA=hwa
            MediaPusher.HWA=hwa
            grabber=MediaGrabber(sourcePath!!,cacheQueue)
            val mInfo=grabber?.mediaInfo?.copy()
            mInfo?.apply {
                format="flv"
                imageInfo.videoCodec= avcodec.AV_CODEC_ID_H264
                audioInfo.audioCodec= avcodec.AV_CODEC_ID_AAC
                audioInfo.sampleRate=44100
                if(imageInfo.videoBiteRate==0){
                    imageInfo.videoBiteRate=8500*1024
                }
            }
            LogUtil.d("[SyncPlayer]:MediaInfo",mInfo.toString())
            pusher=MediaPusher(Network.STREAM_URL,cacheQueue,mInfo!!)
            grabber?.start()
            pusher?.start()
        }else{
            LogUtil.e("[SyncPlayer]:pushVideo","Null sourcePath!")
        }
    }*/

    /**
     * 依据视频信息和用于承载视频画面的布局的大小来调整视频画幅的大小。
     * @param cWidth videoContainer的宽度
     * @param cHeight videoContainer的高度
     */
    fun sizeMatch(cWidth:Int,cHeight:Int){
        //用于重新设置surfaceView大小
        var acWidth=0
        var acHeight=0
        //依据视频宽高比例重新设置surfaceView的宽高比例
        val params=surView?.layoutParams
        //获取视频的宽高
        val vWidth=mInfo?.imageInfo?.width
        val vHeight=mInfo?.imageInfo?.height
        if(vWidth!=null&&vHeight!=null){
            //当surfaceView的宽高和视频容器的宽高不相等时，需要拉伸视频。
            val vRate=vWidth.toDouble()/vHeight.toDouble()
            val cRate=cWidth.toDouble()/cHeight.toDouble()
            if(vRate>cRate){
                //如果视频的宽高比大于容器的宽高比，那么以容器的宽为依据来设定
                //surfaceView的宽和高
                acWidth=cWidth
                acHeight=(cWidth/vRate).toInt()
            }else{
                //否则以容器的高为依据来设置surfaceView的宽高比
                acHeight=cHeight
                acWidth=(cHeight*vRate).toInt()
            }
            params?.width=acWidth
            params?.height=acHeight
            surView?.layoutParams=params
        }
    }

    /**
     * 初始化播放器.
     * 主要做的工作是创建exoPlayer对象，准备数据源，设置播放的surfaceView以及
     * 设置surfaceView的生命周期回调
     * 不论播放器当前处于何种状态，调用该方法都将重置播放器
     */
    fun initPlayer(){
        try{
            //释放已有的播放器
            player?.stop()
            player?.release()
            player=null
            _loadTime=0L
            //onPrepared=null


            //用于计算加载时间
            var startTime=0L
            var endTime=0L

            //创建新的播放器
            player=ExoPlayer.Builder(context).build()
            player?.apply {
                val mediaItem=MediaItem.fromUri(Uri.parse(sourcePath))
                setMediaItem(mediaItem)
                //设置唤醒保持
                setWakeMode(WAKE_MODE_NETWORK)
                //设置视频输出的SurfaceView
                setVideoSurfaceView(surView)
                //设置外部帧监听
                if(frameListener!=null) setVideoFrameMetadataListener(frameListener!!)
                //设置准备完成后的监听
                addListener(object :Player.Listener{
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if(playbackState==Player.STATE_READY){
                            //画幅对齐
                            sizeMatch(videoContainer?.layoutParams?.width!!,videoContainer?.layoutParams?.height!!)
                            //计算加载时间
                            if(endTime==0L){
                                endTime=System.currentTimeMillis()
                                _loadTime=endTime-startTime
                            }
                            onPrepared?.onPrepared()
                            //准备完成回调
                            LogUtil.e("[SyncPlayer]","onPrepared")
                        }
                    }
                })
                startTime=System.currentTimeMillis()
                prepare()
                startWithoutSync()
            }

            //设置surfaceView的生命周期回调
            /*surView?.holder?.addCallback(object :SurfaceHolder.Callback{
                override fun surfaceCreated(holder: SurfaceHolder) {
                    if(leavePause||!player?.isPlaying!!){
                        //start()
                    }
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    //surfaceView被销毁后可能暂停播放。
                    if(leavePause){
                        //pause()
                    }
                }
            })*/

        }catch (e:Exception){
            e.printStackTrace()
            LogUtil.e("[SyncPlayer]:initError",e.message!!)
        }
    }

    /**
     * 销毁播放器并释放资源,播放器将回到initPlayer()调用之前的状态
     */
    fun release(){
        player?.stop()
        player?.release()
        player=null
        lengthInTime=0L
        realTimeStamp=0L
        frameListener=null
        onPrepared=null
        /*//通知服务器已停止播放
        service.closeStreaming(user!!)
            .onErrorReturn { e->
                return@onErrorReturn Result(false,e.message!!,null)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {r->
                LogUtil.e("[SyncPlayer]:closeStreaming",r.msg)
            }*/
    }



    /**
     * 开始播放，播放器从停止状态进入播放状态
     */
    fun start(){
        //将操作同步到服务器
        service.updateProgress(user!!, ORDER_START)
            .onErrorReturn { e->
                return@onErrorReturn Result(false,e.message!!,null)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {r->
                LogUtil.e("[SyncPlayer]:updateProgress","${r.msg}-${r.ojb}")
            }
        /*if(player!=null&&!player?.isPlaying!!){

        }*/
    }

    /**
     * 暂停播放
     */
    fun pause(){
        if(player!=null&&player?.isPlaying!!){
            //将操作同步到服务器
            service.updateProgress(user!!, ORDER_PAUSE)
                .onErrorReturn { e->
                    return@onErrorReturn Result(false,e.message!!,null)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {r->
                    LogUtil.e("[SyncPlayer]:updateProgress","${r.msg}-${r.ojb}")
                }
        }
    }

    /**
     * 不带同步的开始播放
     */
    fun startWithoutSync(){
        if(player!=null&&!player?.isPlaying!!){
            player?.play()
        }
    }

    /**
     * 不带同步的暂停播放
     */
    fun pauseWithoutSync(){
        if(player!=null&&player?.isPlaying!!){
            player?.pause()
        }
    }

    /**
     * 获取直播缓冲延迟
     */
    fun getLiveOffset()=3000*1000L


    /**
     * 获取播放器当前状态
     */
    fun getState()=player?.playbackState

    /**
     * 设置cacheTime
     */
    fun setCacheTime(v:Long)= synchronized(this){
        cacheTime=v
    }

    /**
     * 获取cacheTime
     */
    fun getCacheTime():Long= synchronized(this){
        return@synchronized cacheTime
    }

    /**
     * 从服务器获取视频总时长并将它显示到一个TextView中以及设置seekBar的最大值
     * @param text 展示视频总时长的textView
     */
    /*fun getLengthFromServer(text:TextView,bar:SeekBar?){
        if(lengthInTime==0L){
            //如果没有设置视频总时长，那么从用户就向服务器获取。
            Network.watchTogetherService.getMediaInfo(MainActivity.loggedUser!!)
                .onErrorReturn {e->
                    val res=Result(false,e.message!!,null)
                    LogUtil.e("[watchTogetherService]:getMediaInfo",e.message!!)
                    return@onErrorReturn res
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe{r->
                    if(r.success){
                        LogUtil.e("[watchTogetherService]:getMediaInfo",r.ojb?.toString()!!)
                        val mInfo= Gson().fromJson((r.ojb as String),MediaInfo::class.java)
                        lengthInTime=mInfo.lengthInTime
                        val totalSec=lengthInTime/1000000
                        val minutes=totalSec/60
                        val sec=totalSec%60
                        text.text="${minutes}:${sec}"
                        bar?.max=totalSec.toInt()
                        frameTime=((1000*1000000)/(mInfo?.frameRate!!*1000)).toLong()
                    }
                }
        }
    }*/

    /**
     * 在播放后设置frameListener
     */
    fun setPlayTimeFrameListener(listener:VideoFrameMetadataListener){
        player?.setVideoFrameMetadataListener(listener)
    }

    /**
     * 将实时播放进度更新到一个TextView中
     * @param text 展示播放进度的textView
     */
    /*fun showRealTimeStamp(owner: LifecycleOwner,text: TextView){
        realTimeStamp.observe(owner){v->
            val totalSec=v/1000000
            val min=totalSec/60
            val sec=totalSec%60
            text.text="${min}:${sec}"
        }
    }*/

}