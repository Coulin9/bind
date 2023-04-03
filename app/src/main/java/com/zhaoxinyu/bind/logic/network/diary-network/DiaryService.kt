package com.zhaoxinyu.bind.logic.network.`diary-network`

import com.zhaoxinyu.bind.logic.entities.Diary
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.Body
import retrofit2.http.POST
import com.zhaoxinyu.bind.logic.entities.Result
import retrofit2.http.GET
import retrofit2.http.Query

interface DiaryService {
    @POST("addDiary")
    fun addDairy(@Body diary: Diary):Observable<Result>

    @POST("updateDiary")
    fun updateDiary(@Body diary: Diary):Observable<Result>

    @GET("getAllDiary")
    fun getAllDiary(@Query("userId") userId:Int?):Observable<Result>

    @GET("getNewNDiary")
    fun getNewNDiary(@Query("userId") userId: Int,@Query("count") count:Int):Observable<Result>

    @POST("deleteOneDiary")
    fun deleteOneDiary(@Body diary: Diary):Observable<Result>

    @GET("deleteAllDiary")
    fun deleteAllDiary(@Query("userId") userId: Int):Observable<Result>

    @GET("getDairyVersion")
    fun getDiaryVersion(@Query("userId") userId: Int):Observable<Result>
}