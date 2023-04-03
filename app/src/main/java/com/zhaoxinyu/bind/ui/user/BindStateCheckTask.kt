package com.zhaoxinyu.bind.ui.user

import android.content.Context
import android.widget.Toast
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.ui.diary.DiaryFragment
import com.zhaoxinyu.bind.utils.LogUtil
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

class BindStateCheckTask(val context: Context):Thread() {
    //绑定对话框，监测到绑定成功时应该关闭它
    private var bindDialog: BindDialog?=null

    val isRunning get() = _isRunning
    private var _isRunning=false

    override fun run() {
        while(isRunning&&MainActivity.loggedUser!=null){
            Network.userService.testBind()
                .onErrorReturn {
                    LogUtil.e("[userService]:testBind",it.message!!)
                    return@onErrorReturn Result(false,it.message!!)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {r->
                    LogUtil.d("[userService]:testBind",r.toString())
                    if(r.success){
                        if(r.ojb!=null&&MainActivity.loggedUser?.binderId==null){
                            //从未绑定状态进入绑定状态
                            val taId=(r.ojb as Double).toInt()
                            MainActivity.loggedUser?.binderId=taId
                            bindDialog?.dialog?.cancel()
                            Toast.makeText(context,"已绑定",Toast.LENGTH_SHORT).show()
                        }else if(r.ojb==null&&MainActivity.loggedUser?.binderId!=null){
                            //从绑定状态进入未绑定状态
                            MainActivity.loggedUser?.binderId=null
                            bindDialog?.dialog?.cancel()
                            MainActivity.diaryVersion=-1
                            MainActivity.taDiaryVersion=-1
                            MainActivity.sharedPreference.edit().apply {
                                putInt(DiaryFragment.DIARY_VERSION,-1)
                                putInt(DiaryFragment.DIARY_VERSION_TA,-1)
                                apply()
                            }
                        }
                    }
                }
            sleep(5*1000)
        }
        _isRunning=false
    }

    fun startCheck(){
        if(!isRunning){
            _isRunning=true
            this.start()
        }
    }

    fun endCheck(){
        if(isRunning){
            _isRunning=false
        }
    }

    fun setBindDialog(dialog: BindDialog?){
        bindDialog=dialog
    }
}