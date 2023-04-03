package com.zhaoxinyu.bind.logic.network.`watch-together-network`

import com.zhaoxinyu.bind.logic.entities.User
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.Body
import retrofit2.http.POST
import com.zhaoxinyu.bind.logic.entities.Result
import retrofit2.http.Query

interface WatchTogetherService {
    @POST("get_new_message")
    fun getNewMessage(@Body user: User):Observable<Result>

    @POST("send_message")
    fun sendMessage(@Body user: User,@Query("msg") msg:String):Observable<Result>

    @POST("start_streaming")
    fun startStreaming(@Body map:HashMap<String,String>):Observable<Result>

    @POST("close_streaming")
    fun closeStreaming(@Body user:User):Observable<Result>

    @POST("get_media_info")
    fun getMediaInfo(@Body user:User):Observable<Result>

    @POST("check_video_on")
    fun checkVideoOn(@Body user:User):Observable<Result>

    @POST("update_progress")
    fun updateProgress(@Body user: User,@Query("p") progress:Long):Observable<Result>

    //获取控制命令
    @POST("get_progress")
    fun getProgress(@Body user:User):Observable<Result>

    //更新实时播放进度
    @POST("update_playing_timestamp")
    fun updatePlayingTimeStamp(@Body user: User,@Query("timeStamp") v:Long):Observable<Result>

    //获取实时播放进度
    @POST("get_playing_timestamp")
    fun getPlayingTimeStamp(@Body user: User):Observable<Result>

    //设置cacheTime
    @POST("set_cache_time")
    fun setCacheTime(@Body user: User,@Query("v") v:Long):Observable<Result>

    //获取cacheTime
    @POST("get_cache_time")
    fun getCacheTime(@Body user: User):Observable<Result>
}