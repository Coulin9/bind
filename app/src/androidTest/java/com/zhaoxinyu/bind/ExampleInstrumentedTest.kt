package com.zhaoxinyu.bind

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore.Audio.Media
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.badlogic.gdx.Net
import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter
import com.zhaoxinyu.bind.logic.UserRepository
import com.zhaoxinyu.bind.logic.`watch-together`.FrameQueue
import com.zhaoxinyu.bind.logic.`watch-together`.MediaGrabber
import com.zhaoxinyu.bind.logic.`watch-together`.MediaPusher
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.utils.DateJsonAdapter
import com.zhaoxinyu.bind.utils.LogUtil
import com.zhaoxinyu.bind.utils.Utils
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.util.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.zhaoxinyu.bind", appContext.packageName)
    }

    @Test
    fun networkUserLoginTest(){
        /*UserRepository.login("123456@qq.com","12345678")
            .subscribe {
                LogUtil.d("netWorkTest",it.toString())
                Utils.token=(it.ojb as Map<String,Any>)["token"] as String
                LogUtil.d("netWorkTest:token",Utils.token)
            }*/
    }

    @Test
    fun networkUserLogoutTest(){
        /*UserRepository.logout()
            .subscribe{
                LogUtil.d("netWorkTest",it.toString())
                LogUtil.d("netWorkTest:token",Utils.token)
            }*/
    }

    data class Tdate(@JsonAdapter(DateJsonAdapter::class) val date: Date)
    @Test
    fun jsonDateTest(){
        val jsonString=Gson().toJson(Tdate(Date()))
        LogUtil.d("jsonDateTest",jsonString)
        LogUtil.d("jsonDateTest",Gson().fromJson(jsonString,Tdate::class.java).toString())
    }

    @Test
    fun mediaPusherTest(){
        val path="/storage/emulated/0/Movies/VID_20230226_110243.mp4"
        val cacheQueue= FrameQueue()
        val mediaGrabber= MediaGrabber(path,cacheQueue)
        mediaGrabber.start()
        val mediaPusher= MediaPusher(Network.STREAM_URL,cacheQueue,mediaGrabber.mediaInfo)
        mediaPusher.start()
    }

    @Test
    fun frameConstructTest(){
        val path="/storage/emulated/0/Movies/VID_20230226_110243.mp4"
        val frame= Frame()
        val recorder=FFmpegFrameRecorder(Network.STREAM_URL,0)
        val mediaExtractor=MediaExtractor()
        mediaExtractor.setDataSource(path)
        var videoFormat:MediaFormat?=null
        var videoTrack=0
        var audioFormat:MediaFormat?=null
        var audioTrack=0
        //解析视频和音频的格式信息
        for(i in 0 until mediaExtractor.trackCount){
            //遍历各个轨道，找到视频轨道和音频轨道
            val format=mediaExtractor.getTrackFormat(i)
            val mime=format.getString(MediaFormat.KEY_MIME)
            if(mime?.startsWith("video")!!){
                videoFormat=format
                videoTrack=i
            }else if(mime?.startsWith("audio")!!){
                audioFormat=format
                audioTrack=i
            }
        }

        recorder.audioChannels=audioFormat?.getInteger(MediaFormat.KEY_CHANNEL_COUNT)!!
        recorder.apply {
            format="flv"
            imageWidth=videoFormat?.getInteger(MediaFormat.KEY_WIDTH)!!
            imageHeight=videoFormat?.getInteger(MediaFormat.KEY_HEIGHT)!!
            videoCodec= avcodec.AV_CODEC_ID_H264
            audioCodec= avcodec.AV_CODEC_ID_AAC
            sampleRate=44100
        }
        try {
            recorder.start()
        }catch (e:Exception){
            e.printStackTrace()
        }

        //先尝试解码视频
        //初始化
        val mediaCodec= MediaCodec.createDecoderByType(videoFormat?.getString(MediaFormat.KEY_MIME)!!)
        //设置解码回调
        mediaCodec.setCallback(object :MediaCodec.Callback(){
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                //index指示了目前可用的buffer的下标，基本思路就是：
                //依据index获取可用buffer，从mediaExtractor中取一帧数据放到buffer中。
                //如果成功，提交buffer并让mediaExtractor前进到下一帧。
                //否则表示播放结束.
                val buffer=codec.getInputBuffer(index)
                val size=mediaExtractor.readSampleData(buffer!!,0)
                if(size>0){
                    //输入读取成功
                    codec.queueInputBuffer(index,0,size,mediaExtractor.sampleTime,mediaExtractor.sampleFlags)
                }else{
                    //输入读取失败,读取结束
                    codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo
            ) {
                val buffer=codec.getOutputBuffer(index)
                frame.data=buffer
                frame.timestamp=info.presentationTimeUs
                frame.imageHeight=videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
                frame.imageWidth=videoFormat.getInteger(MediaFormat.KEY_WIDTH)
                //frame.imageDepth=videoFormat.getInteger(MediaFormat.KEY_OUTPUT_REORDER_DEPTH)
                LogUtil.d("MediaCodecTest",frame.toString())
                recorder.record(frame)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                e.printStackTrace()
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            }
        })
        //进行配置
        mediaCodec.configure(videoFormat,null,null,0)
        //开始解码
        mediaCodec.start()
    }

    @Test
    fun MediaPlayerTest(){
        val player=MediaPlayer().apply {
            setDataSource(MainActivity().applicationContext, Uri.parse(Network.WATCH_STREAM_URL))
            prepare()
            start()
        }
    }
}