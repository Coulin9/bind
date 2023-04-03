package com.zhaoxinyu.bind.ui.user

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.databinding.BindDialogBinding
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.ui.HomeFragment
import com.zhaoxinyu.bind.utils.LogUtil
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File

class BindDialog(val bindCode:String):DialogFragment() {
    val binding get() = _binding!!
    private var _binding:BindDialogBinding?=null


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding= BindDialogBinding.inflate(layoutInflater)

        binding.bindCode.text=bindCode

        binding.bindBtn.setOnClickListener {
            Network.userService.bind(binding.bindCodeInputText.editText?.text?.toString())
                .onErrorReturn { e->
                    LogUtil.e("[userService]:bind",e.message!!)
                    return@onErrorReturn Result(false,e.message!!)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { r->
                    LogUtil.d("[userService]:bind",r.msg)
                    if(r.success){
                        Toast.makeText(requireContext(),"绑定成功！",Toast.LENGTH_SHORT).show()
                        val taId=(r.ojb as Double).toInt()
                        MainActivity.loggedUser?.binderId=taId

                        //绑定成功后需要初始化保存对方头像的文件
                        val taIconFilePath= requireContext().getExternalFilesDir(null)?.path+"/Pictures/${MainActivity.loggedUser?.binderId}_icon.PNG"
                        HomeFragment.taIconFile= File(taIconFilePath)

                        //MainActivity.taDiaryVersion=-1

                        dialog?.cancel()
                    }else{
                        Toast.makeText(requireContext(),"绑定失败！",Toast.LENGTH_SHORT).show()
                    }
                }
        }
        val dialog=AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        LogUtil.d("bindDialogCancel","onCancel")

        Network.userService.exitBindingState(bindCode)
            .onErrorReturn { e->
                LogUtil.e("[userService]:exitBindingState",e.message!!)
                return@onErrorReturn Result(false,e.message!!)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { r->
                LogUtil.d("[userService]:exitBindingState",r.msg)
            }
    }
}