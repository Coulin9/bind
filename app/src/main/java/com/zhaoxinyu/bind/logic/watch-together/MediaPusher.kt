package com.zhaoxinyu.bind.logic.`watch-together`

import android.widget.Toast
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.utils.LogUtil
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.AndroidFrameConverter
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.FFmpegLogCallback
import kotlin.random.Random

class MediaPusher(
    /**
     * 目标推流地址或保存地址
     */
    val targetURL:String,
    /**
     * 取帧的队列
     */
    var frameQueue: FrameQueue?=null,
    /**
     * 推流或保存的目标媒体格式及信息
     */
    var mediaInfo: MediaInfo=MediaInfo()) {
    /**
     * 录制器对象，只有在调用start方法时才初始化，因为一开始mediaInfo可能
     * 是不确定的。
     */
    private var recorder:FFmpegFrameRecorder?=null

    /**
     * 启停标志位
     */
    val isRunning get() = _isRunning
    private var _isRunning=false

    /**
     * 暂停标识位
     */
    val isPause get() = _isPause
    private var _isPause=true

    /**
     * 工作线程
     */
    val task get() = _task
    private var _task=MediaPusherThread()

    /**
     * 帧队列的同步锁
     */
    private val lock get() = frameQueue?.lock!!

    /**
     * 播放进度,推流进度同步的对象
     */
    private var playTimeStamp=0L

    /**
     * 播放进度的同步锁
     */
    private val playLock=Object()


    companion object{
        var HWA=false
        /**
         * 播放进度与推送进度之间的差值
         */
        const val cacheTime=7000000L
    }

    /**
     * 工作线程类
     */
    inner class MediaPusherThread:Thread(){
        override fun run() {
            while (isRunning){
                try{
                    pushMedia()
                }catch (e:Exception){
                    LogUtil.e("PushError",e.message!!)
                }
                sleep((1000/mediaInfo.frameRate.toLong())/16)
            }
            recorder?.stop()
        }
    }

    /**
     * 更新播放进度
     */
    fun updatePlayTimeStamp(t:Long)= synchronized(playLock){
        playTimeStamp=t
        playLock.notify()
    }
    /**
     * 推送媒体的任务
     */
    //帧计数
    private var frameCount=0
    private fun pushMedia()= synchronized(lock){
        //首先检查是否需要暂停，暂停的条件是帧队列为空或者isPause为true
        if(frameQueue?.isEmpty()!!||isPause){
            println("Pusher被阻塞")
            lock.wait()
        }
        //从帧队列取一帧
        val frame=frameQueue?.poll()
        if(frame!=null){
            //帧不为空，且当前帧时间与播放进度差值小于8s，推送或录制
            /*while(frame.timestamp-playTimeStamp>=cacheTime&&isRunning){
                //循环推送同一帧，保持和服务器的连接
                recorder?.record(frame)
                println("Pusher由于同步被阻塞，当前播放进度：${playTimeStamp},当前推流进度：${frame.timestamp}")
                Thread.sleep(1000 / 24)
            }*/
            synchronized(playLock){
                while(frame.timestamp-playTimeStamp>= cacheTime&&isRunning){
                    println("Pusher由于同步被阻塞，当前播放进度：${playTimeStamp},当前推流进度：${frame.timestamp}")
                    playLock.wait()
                }
            }
            recorder?.record(frame)
            if(frameCount>=10){
                Network.watchTogetherService.setCacheTime(MainActivity.loggedUser!!,frame.timestamp-playTimeStamp)
                    .onErrorReturn {e->
                        val res= Result(false,e.message!!,null)
                        LogUtil.e("[watchTogetherService]:setCacheTime",e.message!!)
                        return@onErrorReturn res
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { r->
                        LogUtil.e("[watchTogetherService]:setCacheTime",r.msg)
                    }
                frameCount=0
            }else{
                frameCount++
            }
            println("正常推送：${frame.timestamp}/////${playTimeStamp}")
            //一定要关闭资源，否则会导致内存溢出。
            frame.close()
            lock.notify()
        }
    }

    /**
     * 开始推送
     */
    fun start(){
        avutil.av_log_set_level(avutil.AV_LOG_INFO)
        FFmpegLogCallback.set()

        //首先检查frameQueue不能为空以及是否有线程还在运行
        if(frameQueue==null) return
        if(task.isAlive) return
        //初始化recorder
        recorder= FFmpegFrameRecorder(targetURL,mediaInfo.audioInfo.audioChannels)
        recorder?.apply {
            imageWidth=mediaInfo.imageInfo.width
            imageHeight=mediaInfo.imageInfo.height
            videoCodec=mediaInfo.imageInfo.videoCodec
            format=mediaInfo.format
            videoBitrate=mediaInfo.imageInfo.videoBiteRate*4
            frameRate=mediaInfo.frameRate
            audioCodec=mediaInfo.audioInfo.audioCodec
            sampleRate=mediaInfo.audioInfo.sampleRate
            audioBitrate=mediaInfo.audioInfo.audioBiteRate
        }
        if(HWA){
            recorder?.videoCodecName="h264_mediacodec"
        }
        //recorder?.pixelFormat
        try {
            recorder?.start()
        }catch (e:Exception){
            e.printStackTrace()
        }
        //创建并启动任务线程
        _task=MediaPusherThread()
        _isRunning=true
        _isPause=false
        task.start()
    }

    /**
     * 暂停推流或录制
     */
    fun pause(){
        //只有当任务正在运行的时候才可以暂停
        if(task.isAlive){
            _isPause=true
        }
    }

    /**
     * 恢复推流或录制
     */
    fun resume(){
        //当帧队列缓存为空时不能恢复
        if(task.isAlive&&!frameQueue?.isEmpty()!!){
            synchronized(lock){
                _isPause=false
                lock.notify()
            }
        }
    }

    /**
     * 停止推流或录制
     */
    fun stop()= synchronized(lock){
        _isRunning=false
        lock.notify()
        synchronized(playLock){
            playLock.notify()
        }
    }
}