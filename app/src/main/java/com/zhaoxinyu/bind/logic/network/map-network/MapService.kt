package com.zhaoxinyu.bind.logic.network.`map-network`

import com.zhaoxinyu.bind.logic.entities.RealTime
import io.reactivex.rxjava3.core.Observable
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.entities.User
import retrofit2.http.*

interface MapService {

    companion object{
        const val token="zdXMesm3N5hLUVFp"
    }

    @POST("getInfo")
    fun getInfo(@Body user: User):Observable<Result>

    @POST("updateInfo")
    fun updateInfo(@Body user: User,@Query("info") info:String):Observable<Result>
}