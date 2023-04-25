package com.zhaoxinyu.bind.logic.network

import com.zhaoxinyu.bind.logic.network.`diary-network`.DiaryService
import com.zhaoxinyu.bind.logic.network.`map-network`.MapService
import com.zhaoxinyu.bind.logic.network.`user-network`.UserService
import com.zhaoxinyu.bind.logic.network.`watch-together-network`.WatchTogetherService
import com.zhaoxinyu.bind.logic.network.weatherService.WeatherService
import com.zhaoxinyu.bind.utils.LogUtil
import com.zhaoxinyu.bind.utils.Utils
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create

object Network {
    const val severAddress="192.168.3.33"
    const val srsAddress="192.168.3.33"
    const val BASE_URL="http://${severAddress}:8081/"

    const val STREAM_URL="rtmp://${srsAddress}/live/livestream"

    const val WATCH_STREAM_URL="http://${srsAddress}:8080/live/livestream.flv"
    //"http://192.168.3.33:8080/live/livestream.flv"
    //"http://192.168.3.33:8080/players/srs_player.html?schema=http"

    private val client= OkHttpClient.Builder()
        .addInterceptor { chain->
            val request=chain.request()
            //使用被拦截的请求的builder来构建新的请求
            val builder=request.newBuilder()
            //这个拦截器主要是往请求中添加token，同时取出响应中的新token
            val newRequest=builder.addHeader("token", Utils.getToken()).build()
            //LogUtil.d("Interceptor","${newRequest.url()},${newRequest.header("token")}")
            val response=chain.proceed(newRequest)
            val newToken=response.header("token")
            if(newToken!=null) Utils.setToken(newToken)
            response
        }
        .build()


    private val retrofit= Retrofit.Builder()
        .client(client)
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
        .build()

    private val weatherRetrofit=Retrofit.Builder()
        .baseUrl("https://api.caiyunapp.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
        .build()

    val userService= retrofit.create(UserService::class.java)

    val watchTogetherService= retrofit.create(WatchTogetherService::class.java)

    val diaryService= retrofit.create(DiaryService::class.java)

    val mapService= retrofit.create(MapService::class.java)

    val weatherService= weatherRetrofit.create(WeatherService::class.java)

}