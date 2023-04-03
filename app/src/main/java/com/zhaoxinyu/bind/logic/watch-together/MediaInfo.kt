package com.zhaoxinyu.bind.logic.`watch-together`

import org.bytedeco.ffmpeg.global.avcodec

data class ImageInfo(var videoCodec:Int=avcodec.AV_CODEC_ID_H264,var width:Int=1920,var height:Int=1080,
                     var frameRate:Double=24.0,var videoBiteRate:Int=8500*1024)
data class AudioInfo(var audioCodec:Int=avcodec.AV_CODEC_ID_AAC,var sampleFormat:Int=avcodec.AV_CODEC_ID_AAC,
                     var audioChannels:Int=2,var sampleRate:Int=48000,var audioBiteRate:Int=24)
data class MediaInfo(var lengthInTime:Long=0L,var format:String="flv", var frameRate: Double=24.0,
                     var imageInfo:ImageInfo=ImageInfo(),
                    var audioInfo:AudioInfo= AudioInfo()
)