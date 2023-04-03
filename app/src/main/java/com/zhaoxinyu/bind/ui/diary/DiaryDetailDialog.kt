package com.zhaoxinyu.bind.ui.diary

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.gson.Gson
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.DiaryDetailDialogBinding
import com.zhaoxinyu.bind.logic.entities.Diary
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.utils.LogUtil
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.ui.HomeFragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * @param diary 用于展示的日记
 * @param adapter 当前日记列表的recyclerView的Adapter
 * @param fragment 对话框所依附的Fragment
 */
class DiaryDetailDialog(val diary: Diary,val adapter: DiaryListAdapter,val fragment: DiaryFragment):DialogFragment() {

    companion object{
        const val EDIT_DIARY="editDiary"
        const val DIARY_POSITION="diaryPosition"
    }
    val binding get() = _binding!!
    private var _binding:DiaryDetailDialogBinding?=null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding= DiaryDetailDialogBinding.inflate(layoutInflater)

        val d=diary
        val year=d.date.year-100+2000
        val month=when(d.date.month+1){
            1->"一月"
            2->"二月"
            3->"三月"
            4->"四月"
            5->"五月"
            6->"六月"
            7->"七月"
            8->"八月"
            9->"九月"
            10->"十月"
            11->"十一月"
            12->"十二月"
            else -> "一月"
        }
        val date="${d.date.date}"
        val day=when(d.date.day){
            0->"星期日"
            1->"星期一"
            2->"星期二"
            3->"星期三"
            4->"星期四"
            5->"星期五"
            6->"星期六"
            else -> "星期日"
        }
        val hour=if(d.date.hours>=10) "${d.date.hours}" else "0${d.date.hours}"
        val minute=if(d.date.minutes>=10) "${d.date.minutes}" else "0${d.date.minutes}"
        val yearAndMonth="${year}年，${month}"
        val dayAndTime="${day} ${hour}:${minute}"

        binding.contentText.text=diary.content
        binding.yearAndMonthText.text=yearAndMonth
        binding.dayAndTimeText.text=dayAndTime
        binding.dateText.text=date

        binding.closeBtn.setOnClickListener {
            dialog?.cancel()
        }

        binding.deleteBtn.setOnClickListener {
            val dialog=AlertDialog.Builder(requireContext())
                .setMessage("你确定要删除这篇日记吗？")
                .setPositiveButton("确定",object :DialogInterface.OnClickListener{
                    override fun onClick(p0: DialogInterface?, p1: Int) {
                        /**
                         * 删除操作的逻辑：
                         * 先判断是不是自己的日记，当前用户只能删除自己的日记（编辑也是同理）
                         * 先请求服务器将服务器上保存的日记删除，如果服务器的删除成功，则同步删除
                         * 本地adapter中的这条日记以及更新DairyFragment中保存的日记库版本号。
                         */
                        if(diary.userId!=MainActivity.loggedUser?.id){
                            Toast.makeText(requireContext(),"你只能删除自己的日记！",Toast.LENGTH_SHORT).show()
                        }else{
                           val o= Network.diaryService.deleteOneDiary(diary)
                                .onErrorReturn {e->
                                    LogUtil.e("[diaryService]:deleteOneDiary",e.message!!)
                                    return@onErrorReturn Result(false,e.message!!)
                                }
                               .subscribeOn(Schedulers.io())
                               .observeOn(AndroidSchedulers.mainThread())
                               .subscribe {r->
                                   LogUtil.d("[diaryService]:deleteOneDiary",r.msg)
                                   if(r.success){
                                       val newVersion=(r.ojb as Double).toInt()
                                       val position=adapter.data.indexOf(diary)
                                       adapter.notifyItemRemoved(position)
                                       adapter.data.remove(diary)
                                       adapter.notifyItemRangeChanged(0,adapter.itemCount)
                                       fragment.setDiaryVersion(newVersion)
                                       this@DiaryDetailDialog.dialog?.cancel()
                                   }else{
                                       Toast.makeText(requireContext(),"删除失败！",Toast.LENGTH_SHORT).show()
                                   }
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

        binding.editBtn.setOnClickListener {
            if(diary.userId==MainActivity.loggedUser?.id){
                val bundle=Bundle()
                bundle.putString(EDIT_DIARY,Gson().toJson(diary))
                MainActivity.context?.navController?.navigate(R.id.action_homeFragment_to_editDiaryFragment,bundle)
                this.dialog?.cancel()
            }else{
                Toast.makeText(requireContext(),"你只能编辑自己的日记！",Toast.LENGTH_SHORT).show()
            }
        }
        val dialog=AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
        return dialog
    }
}