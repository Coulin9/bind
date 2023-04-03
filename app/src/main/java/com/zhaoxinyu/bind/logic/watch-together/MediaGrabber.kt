package com.zhaoxinyu.bind.logic.`watch-together`

import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import org.bytedeco.javacv.Frame
import kotlin.concurrent.thread
import kotlin.random.Random

class MediaGrabber(
    /**
     * 媒体源的地址
     */
    private val fileURL:String,
    /**
     * 帧队列
     */
    var frameQueue:FrameQueue?=null) {

    /**
     * 媒体源的基本信息
     */
    val mediaInfo get() = _mediaInfo!!
    private var _mediaInfo:MediaInfo?=null

    /**
     * 帧抓取器
     */
    private lateinit var grabber: FFmpegFrameGrabber


    /**
     * 启停标识位
     */
    val isRunning get() = _isRuning
    private var _isRuning:Boolean=false

    /**
     * 启停标识位
     */
    val isPause get() = _isPause
    private var _isPause:Boolean=false

    /**
     * 工作线程
     */
    private var task=MediaGrabThread()

    /**
     * 同步锁对象
     */
    private val lock get() = frameQueue?.lock!!

    /**
     * 帧
     */
    private var frame:Frame?=null

    /**
     * 暂存帧的数组
     */
    //private val frameList= mutableListOf<Frame>()


    companion object{
        /**
         * 暂存帧数组的大小
         */
        const val FRAME_ARRAY_SIZE=128

        var HWA=false
    }
    /**
     * 初始化基本信息
     */
    init{

        avutil.av_log_set_level(avutil.AV_LOG_INFO)
        FFmpegLogCallback.set()

        //初始化grabber以及媒体信息
        grabber= FFmpegFrameGrabber(fileURL)
        //设置h264硬件加速
        try {
            grabber.start()
            //println("VideoCodecName:${grabber.videoCodecName}")
            if(HWA){
                when(grabber.videoCodecName){
                    "h264"->{
                            //mediacodec是安卓平台的硬件加速api
                            grabber.videoCodecName="h264_mediacodec"
                            //grabber.setOption("fflags", "genpts")
                            //grabber.setVideoOption("threads", "0")
                    }
                    "h265"->{
                            grabber.videoCodecName="h265_mediacodec"
                            //grabber.setOption("fflags", "genpts")
                            //grabber.setVideoOption("threads", "0")
                    }
                    "hevc"->{
                            grabber.videoCodecName="hevc_mediacodec"
                            //grabber.setOption("fflags", "genpts")
                            //grabber.setVideoOption("threads", "0")
                    }
                }
                grabber.stop()
                grabber.start()
            }
            //获取音频与视频的基本信息
            val imageInfo=ImageInfo(grabber.videoCodec,grabber.imageWidth,grabber.imageHeight,grabber.videoFrameRate
                ,grabber.videoBitrate)
            val audioInfo=AudioInfo(grabber.audioCodec,grabber.sampleFormat,grabber.audioChannels,grabber.sampleRate
                ,grabber.audioBitrate)
            _mediaInfo= MediaInfo(grabber.lengthInTime,grabber.format,grabber.frameRate,imageInfo, audioInfo)
        }catch (e:Exception){
            e.printStackTrace()
        }

    }

    /**
     * 任务线程
     */
    private inner class MediaGrabThread:Thread(){
        override fun run() {
            //frameQueue?.clear()
            while (isRunning){
                grabMedia()
                sleep((1000/mediaInfo.frameRate.toLong())/16)
            }
            //抓取结束，释放资源。
            grabber.stop()
        }
    }

    /**
     * 媒体抓取任务
     */
    private fun grabMedia()= synchronized(lock){
        //到达播放结束或者帧队列满都需要将当前线程阻塞。
        //生产者和消费者要如何互相通知帧率队列已满或者已空呢？
        //生产者正常生产时就不断向消费者发送通知，解除消费者的阻塞状态
        //消费者正常消费时，也不断向生产者发送通知（notify），解除生产者的阻塞状态。
        //为什么播放结束是阻塞而不是直接退出呢？
        //因为用户此时还有可能拖动进度条。
        //判断是否阻塞
        frame=grabber.grab()
        if(frameQueue?.isFull()!!&&frame!=null){
            _isPause=true
            println("Grabber被阻塞")
            lock.wait()
        }
        //没有阻塞，正常生产
        if(_isPause){
            _isPause=false
        }
        if(frame!=null){
            frameQueue?.offer(frame?.clone())
            println("正常抓取：${frame?.timestamp}")
            //通知消费者正常消费
            lock.notify()
        }else{
            println("Grabber被阻塞")
            lock.wait()
        }
    }

    /**
     *  启动并开始抓取媒体资源
     */
    fun start(){
        //判断帧队列是否为null
        if(frameQueue==null) return
        //判断线程是否已经停止
        if(task.isAlive) return

        task=MediaGrabThread()
        //println("启动线程：${task.toString()}")
        _isRuning=true
        _isPause=false
        task.start()
    }

    /**
     * 退出任务线程
     */
    fun stop()= synchronized(lock){
        _isRuning=false
        //如果线程处于阻塞状态，就需要唤醒线程。
        //如果不是播放完了才停止的话，会导致阻塞时
        //的那一帧丢失。不过既然都退出播放了，丢帧似乎也
        //算不上什么问题了。
        lock.notify()
    }

    /**
     * 设置开始播放的时间点。
     */
    fun setTimeStamp(timeStamp:Long){
        try {
            grabber.setTimestamp(timeStamp)
            synchronized(lock){
                //清空缓存，并唤醒可能被阻塞的线程重新开始抓取帧。
                frameQueue?.clear()
                lock.notify()
            }
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

}