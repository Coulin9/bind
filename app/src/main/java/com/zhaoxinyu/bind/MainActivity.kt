package com.zhaoxinyu.bind

import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.google.android.material.color.DynamicColors
import com.google.gson.Gson
import com.zhaoxinyu.bind.databinding.ActivityMainBinding
import com.zhaoxinyu.bind.logic.LoginStateCheckTask
import com.zhaoxinyu.bind.logic.UserRepository
import com.zhaoxinyu.bind.logic.entities.User
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.ui.user.IconRequestTask
import com.zhaoxinyu.bind.utils.Utils
import java.io.File
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    val navController get() = _navController!!
    private var _navController:NavController?=null


    companion object{
        var loggedUser:User?=null
        var bindCode:String?=null
        var context:MainActivity?=null
        var loginStateCheckTask:LoginStateCheckTask?=null
        //sharedPreference用来保存token和已登陆用户
        val sharedPreference get() = _sharedPreference!!
        private var _sharedPreference:SharedPreferences?=null
        const val TOKEN_KEY="token"
        const val LOGGED_USER_KEY="loggedUser"
        var HWASetting=false

        /**
         * 记录日记库的版本号
         */
        var diaryVersion=-1

        var taDiaryVersion=-1
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        //实现沉浸式小横条和状态栏
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        //应用dynamic color
        DynamicColors.applyToActivityIfAvailable(this)


        context=this
        binding=ActivityMainBinding.inflate(layoutInflater)
        _sharedPreference=getPreferences(MODE_PRIVATE)
        _navController=(supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
        //恢复保存的token与user
        Utils.setToken(sharedPreference.getString(TOKEN_KEY,"")!!)
        loggedUser= Gson().fromJson(sharedPreference.getString(LOGGED_USER_KEY,""),User::class.java)




        //首次打开app
        if(loggedUser==null){
            navController.navigate(R.id.action_global_loginFragment)
        }

        setContentView(binding.root)

        apiTest()
    }


    fun apiTest(){
        /*binding.loginBtn.setOnClickListener {
            UserRepository.login("11123@qq.com","1234567")
        }

        binding.logoutBtn.setOnClickListener {
            UserRepository.logout()
        }

        binding.registerBtn.setOnClickListener {
            UserRepository.register(
                User(null,"11123@qq.com","Fake","12345678"
                    ,"female", Date(),null,null
                )
            )
        }

        binding.unRegisterBtn.setOnClickListener {
            if(loggedUser!=null) UserRepository.unRegister(loggedUser!!)
        }

        binding.enterBindingStateBtn.setOnClickListener {
            UserRepository.enterBindingState()
        }

        binding.exitBindingStateBtn.setOnClickListener {
            if(bindCode!=null){
                UserRepository.exitBindingState(bindCode!!)
            }
        }

        binding.bindBtn.setOnClickListener {
            UserRepository.bind(binding.bindCode.text.toString())
        }

        binding.unBindBtn.setOnClickListener {
            UserRepository.unBind()
        }

        binding.testBindBtn.setOnClickListener {
            UserRepository.testBind()
        }*/
    }

    override fun onStop() {

        //app退出前需要保存token和已登陆用户。
        sharedPreference.edit().apply {
            putString(TOKEN_KEY,Utils.getToken())
            putString(LOGGED_USER_KEY, Gson().toJson(loggedUser))
            apply()
        }
        super.onStop()
    }

    override fun onDestroy() {
        context=null
        _sharedPreference=null
        _navController=null
        //MainActivity被销毁意味着app退出，那么就需要停止这个后台的任务。
        loginStateCheckTask?.valid=false


        super.onDestroy()
    }
}