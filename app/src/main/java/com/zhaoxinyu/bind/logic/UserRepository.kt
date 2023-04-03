package com.zhaoxinyu.bind.logic

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import com.badlogic.gdx.Net
import com.google.gson.Gson
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.entities.User
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.utils.LogUtil
import com.zhaoxinyu.bind.utils.Utils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import java.io.File


object UserRepository {

    fun login(account:String,password:String)= Network.userService.login(account,password)
        .onErrorReturn {
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe{r->
            //如果登陆成功
            if(r.success){
                //将返回的token放到Utils.token中
                Utils.setToken((r.ojb as Map<*, *>)["token"] as String)
                //保存已登陆用户
                val loginUserString=(r.ojb as Map<*, *>)["loginUser"].toString()
                MainActivity.loggedUser= Gson().fromJson(loginUserString,User::class.java)
                //跳转至主页面
                MainActivity.context?.navController?.navigate(R.id.action_loginFragment_to_homeFragment)
            }else{
                Toast.makeText(MainActivity.context,"登陆失败！",Toast.LENGTH_SHORT).show()
            }

            LogUtil.d("UserNetWork:Login",r.toString()+"/////"+MainActivity.loggedUser.toString())
        }

    fun logout()= Network.userService.logout()
        .onErrorReturn {
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { r->
            LogUtil.d("UserNetWork:Logout",r.toString())
        }

    fun register(user: User)= Network.userService.register(user)
        .onErrorReturn {
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {r->
            //注册成功
            if(r.success){
               //注册成功跳转到登陆页面
                MainActivity.context?.navController?.navigate(R.id.action_global_loginFragment)
                Toast.makeText(MainActivity.context,"注册成功！",Toast.LENGTH_SHORT).show()
            }else Toast.makeText(MainActivity.context,"注册失败！",Toast.LENGTH_SHORT).show()
            LogUtil.d("UserNetWork:register",r.toString())
        }

    fun unRegister(user: User)= Network.userService.unRegister(user)
        .onErrorReturn {
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {r->
            LogUtil.d("UserNetWork:unRegister",r.toString())
        }

    fun enterBindingState()= Network.userService.enterBindingState()
        .onErrorReturn {
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {r->
            //如果申请成功
            if(r.success){
                val code=r.ojb as String?
                MainActivity.bindCode=code
            }
            LogUtil.d("UserNetWork:enterBindingState",r.toString()+"////"+MainActivity.bindCode)
        }

    fun exitBindingState(bindCode:String)= Network.userService.exitBindingState(bindCode)
        .onErrorReturn {
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {r->
            LogUtil.d("UserNetWork:exitBindingState",r.toString())
        }

    fun bind(bindCode: String)=Network.userService.bind(bindCode)
        .onErrorReturn {
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {r->
            LogUtil.d("UserNetWork:bind",r.toString())
        }

    fun unBind()=Network.userService.unBind()
        .onErrorReturn {
            LogUtil.e("[userNetWork]:unBind",it.message!!)
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {r->
            LogUtil.d("[userNetWork]:unBind",r.toString())
            if(r.success){

            }
        }

    fun testBind()=Network.userService.testBind()
        .onErrorReturn {
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {r->
            LogUtil.d("UserNetWork:testBind",r.toString())
        }

    fun testLoginState()=Network.userService.testLoginState()
        .onErrorReturn {
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { r->
            //登陆失效
            if(!r.success&&r.msg=="Invalid token!"){
                //登陆失效时强制跳转到登陆页并且停止登陆状态检查任务。
                MainActivity.loginStateCheckTask?.valid=false
                MainActivity.context?.navController?.navigate(R.id.action_global_loginFragment)
                if(MainActivity.loggedUser!=null){
                    //登陆的用户不为空表示不是第一次打开，那么就需呀提示登陆失效。
                    Toast.makeText(MainActivity.context,"登陆失效，请重新登陆！",Toast.LENGTH_SHORT).show()
                }
            }
            LogUtil.d("UserNetWork:testLoginState",r.toString())
        }

    //更新用户信息（除了头像）
    fun updateUserInfo(user: User,context: Context)=Network.userService.updateUserInfo(user)
        .onErrorReturn {
            return@onErrorReturn Result(false,it.message!!)
        }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { r ->
            LogUtil.d("[userService]:updateUserInfo",r.msg)
            if(r.success){
                val user=Gson().fromJson(r.ojb as String,User::class.java)
                MainActivity.loggedUser=user
                Toast.makeText(context,"修改成功！",Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(context,"提交失败，请检查网络连接！",Toast.LENGTH_SHORT).show()
            }
        }

    //更新用户头像
    fun updateUserIcon(file:File?) {
        if(file!=null){
            // 构造新的请求体
            val requestBody: RequestBody =
                RequestBody.create(MediaType.parse("multipart/form-data"), file)

            // 获取MultipartBody对象
            val mFile = MultipartBody.Part.createFormData("file", file.getName(), requestBody)

            Network.userService.updateIcon(mFile)
                .onErrorReturn {
                    return@onErrorReturn Result(false,it.message!!)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe { r ->
                    LogUtil.d("[userService]:updateIcon",r.msg)
                }
        }

    }

    //获取用户头像
    fun getUserIcon(id:Int,file:File)=Network.userService.getUserIcon(id)
        .onErrorReturn { e->
            LogUtil.e("[userService]:getUserIcon",e.message!!)
            return@onErrorReturn ResponseBody.create(MediaType.parse("multipart/form-data"),"")
        }
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.io())
        .subscribe { r ->
            if(r!=null){
                LogUtil.d("[userService]:getUserIcon",r.contentType().toString())
                try{
                    val ips=r.byteStream()
                    val reader=ByteArray(1024)
                    val ops=file.outputStream()
                    //开始下载
                    var size=ips.read(reader)
                    while(size>0){
                        ops.write(reader,0,size)
                        size=ips.read(reader)
                    }
                    ips.close()
                    ops.flush()
                    ops.close()
                }catch (e:Exception){
                    LogUtil.e("[userService]:getUserIcon",e.message!!)
                }

            }

        }
}