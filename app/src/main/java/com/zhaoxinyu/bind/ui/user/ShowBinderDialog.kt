package com.zhaoxinyu.bind.ui.user

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.gson.Gson
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.databinding.BinderShowLayoutBinding
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.entities.User
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.ui.HomeFragment
import com.zhaoxinyu.bind.ui.diary.DiaryFragment
import com.zhaoxinyu.bind.utils.LogUtil
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

class ShowBinderDialog(val user:User) :DialogFragment(){
    val binding get() = _binding!!
    private var _binding:BinderShowLayoutBinding?=null
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding=BinderShowLayoutBinding.inflate(layoutInflater)

        if(HomeFragment.taIconFile!=null) binding.binderIconView.setImageURI(Uri.parse(HomeFragment.taIconFile?.path))
        binding.nickNameText.text=user.userName
        binding.emailText.text=user.account
        binding.genderText.text=if(user.gender=="male") "男" else "女"
        val birthDay=user.birthday
        binding.birthDayText.text="${birthDay!!.year-100+2000}/${birthDay!!.month+1}/${birthDay!!.date}"

        binding.unBindBtn.setOnClickListener {
            val dialog=AlertDialog.Builder(requireContext())
                .setMessage("你确定要解绑吗？")
                .setPositiveButton("确定",object :DialogInterface.OnClickListener{
                    override fun onClick(p0: DialogInterface?, p1: Int) {
                        val o=Network.userService.unBind()
                            .onErrorReturn {
                                LogUtil.e("[userNetWork]:unBind",it.message!!)
                                return@onErrorReturn Result(false,it.message!!)
                            }
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {r->
                                LogUtil.d("[userNetWork]:unBind",r.toString())
                                if(r.success){
                                    Toast.makeText(requireContext(),"解绑成功！",Toast.LENGTH_SHORT).show()
                                    MainActivity.loggedUser?.binderId=null
                                    MainActivity.diaryVersion=-1
                                    MainActivity.taDiaryVersion=-1
                                    MainActivity.sharedPreference.edit().apply {
                                        putInt(DiaryFragment.DIARY_VERSION,-1)
                                        putInt(DiaryFragment.DIARY_VERSION_TA,-1)
                                        apply()
                                    }
                                    dialog?.cancel()
                                }
                            }
                    }
                })
                .setNegativeButton("取消",object :DialogInterface.OnClickListener{
                    override fun onClick(p0: DialogInterface?, p1: Int) {
                        p0?.cancel()
                    }
                })
                .create()
            dialog.show()
        }
        val dialog=AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        return dialog
    }
}