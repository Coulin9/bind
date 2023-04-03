package com.zhaoxinyu.bind.logic.network.`user-network`

import android.graphics.Bitmap
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.GET
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.entities.User
import com.zhaoxinyu.bind.utils.Utils
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface UserService {

    @GET("login")
    fun login(@Query("account") account:String,@Query("password") password:String):Observable<Result>


    @GET("logout")
    fun logout():Observable<Result>

    @POST("register")
    fun register(@Body user: User):Observable<Result>

    @POST("unRegister")
    fun unRegister(@Body user: User):Observable<Result>

    @GET("enterBindingState")
    fun enterBindingState():Observable<Result>

    @GET("exitBindingState")
    fun exitBindingState(@Query("bindCode") bindCode:String):Observable<Result>

    @GET("bind")
    fun bind(@Query("bindCode") bindCode:String?):Observable<Result>

    @GET("unBind")
    fun unBind():Observable<Result>

    @GET("testBind")
    fun testBind():Observable<Result>

    @GET("testLoginState")
    fun testLoginState():Observable<Result>

    @POST("updateUser")
    fun updateUserInfo(@Body user: User):Observable<Result>

    @Multipart
    @POST("updateIcon")
    fun updateIcon(@Part file:MultipartBody.Part):Observable<Result>

    @GET("getUserIcon")
    fun getUserIcon(@Query("id") id:Int):Observable<ResponseBody>

    @GET("getBinder")
    fun getBinder():Observable<Result>
}