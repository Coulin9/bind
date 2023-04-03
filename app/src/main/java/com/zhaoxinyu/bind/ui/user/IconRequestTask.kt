package com.zhaoxinyu.bind.ui.user

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.logic.UserRepository
import com.zhaoxinyu.bind.ui.HomeFragment
import com.zhaoxinyu.bind.ui.watchTogether.RealTimeMSGAdapter
import java.io.File

class IconRequestTask(val context: Context):Thread() {
    val isRunning get() = _isRunning
    private var _isRunning=false

    override fun run() {
        while(isRunning&&MainActivity.loggedUser!=null){
            //首先看一下存储头像的文件夹是否存在
            val parentPath= context.getExternalFilesDir(null)?.path+"/Pictures"
            val parentFile=File(parentPath)
            if(!parentFile.exists()){
                try {
                    val res=parentFile.mkdir()
                    println(res)
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }
            //请求自己和对方的头像
            /*if(!MainActivity.iconFile?.exists()!!){

            }*/
            if(HomeFragment.iconFile!=null){
                UserRepository.getUserIcon(MainActivity.loggedUser?.id!!, HomeFragment.iconFile!!)
            }
            if(HomeFragment.taIconFile!=null&&MainActivity.loggedUser?.binderId!=null){
                UserRepository.getUserIcon(MainActivity.loggedUser?.binderId!!, HomeFragment.taIconFile!!)
            }
            sleep(12*1000)
        }
        _isRunning=false
    }
    fun startRequest(){
        if(!isRunning){
            _isRunning=true
            this.start()
        }
    }

    fun stopRequest(){
        if(isRunning){
            _isRunning=false
        }
    }
}