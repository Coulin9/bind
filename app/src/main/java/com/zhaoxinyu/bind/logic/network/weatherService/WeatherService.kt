package com.zhaoxinyu.bind.logic.network.weatherService

import com.zhaoxinyu.bind.logic.entities.RealTime
import com.zhaoxinyu.bind.logic.network.`map-network`.MapService
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.GET
import retrofit2.http.Path

interface WeatherService {

    @GET("v2.5/${MapService.token}/{lng},{lat}/realtime.json")
    fun getRealTimeWeather(@Path("lat") lat:Double, @Path("lng") lng:Double): Observable<RealTime>
}