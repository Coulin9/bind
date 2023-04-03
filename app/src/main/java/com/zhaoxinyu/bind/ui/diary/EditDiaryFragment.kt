package com.zhaoxinyu.bind.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.databinding.EditDiaryFragmentBinding
import com.zhaoxinyu.bind.logic.entities.Diary
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.utils.LogUtil
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.*

class EditDiaryFragment:Fragment() {
    /**
     * 用于修改的日记，如果为空表明是新书写的日记。
     */
    private var diary:Diary?=null


    val binding get() = _binding!!
    private var _binding:EditDiaryFragmentBinding?=null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding= EditDiaryFragmentBinding.inflate(inflater,container,false)

        val bundle=arguments
        if(bundle!=null){
            diary=Gson().fromJson(bundle.getString(DiaryDetailDialog.EDIT_DIARY),Diary::class.java)
        }

        binding.contentInput.setText(diary?.content)


        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.cancelBtn.setOnClickListener {
            activity?.onBackPressed()
        }

        binding.saveBtn.setOnClickListener {
            /**
             * 保存的逻辑：
             * 首先，判断一个diary是否为空，不为空执行更新，为空执行新增。
             * 进行网络请求，待请求返回后退出当前页面
             */
            if(diary!=null){
                diary?.content=binding.contentInput.text.toString()
                diary?.date=Date(diary?.date?.time!!+diary?.date?.timezoneOffset!!*60*1000)
                LogUtil.e("updateDiary",diary?.date?.toString()!!)
                val o=Network.diaryService.updateDiary(diary!!)
                    .onErrorReturn {e->
                        LogUtil.e("[diaryService]:updateDiary",e.message!!)
                        return@onErrorReturn Result(false,e.message!!)
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {r->
                        LogUtil.d("[diaryService]:updateDiary",r.msg)
                        if(r.success){
                            activity?.onBackPressed()
                        }else{
                            Toast.makeText(requireContext(),"更新失败！",Toast.LENGTH_SHORT).show()
                        }
                    }
            }else{
                val dateValue=Date().time+Date().timezoneOffset*60*1000
                val d=Diary(0,MainActivity.loggedUser?.id!!, Date(dateValue),binding.contentInput.text.toString())
                val o=Network.diaryService.addDairy(d)
                    .onErrorReturn {e->
                        LogUtil.e("[diaryService]:addDairy",e.message!!)
                        return@onErrorReturn Result(false,e.message!!)
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {r->
                        LogUtil.d("[diaryService]:addDairy",r.msg)
                        if(r.success){
                            activity?.onBackPressed()
                        }else{
                            Toast.makeText(requireContext(),"更新失败！",Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
        super.onActivityCreated(savedInstanceState)
    }

    override fun onDestroyView() {
        _binding=null
        super.onDestroyView()
    }
}