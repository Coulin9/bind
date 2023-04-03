package com.zhaoxinyu.bind.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.MainActivity.Companion.diaryVersion
import com.zhaoxinyu.bind.MainActivity.Companion.taDiaryVersion
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.DiaryFragmentBinding
import com.zhaoxinyu.bind.logic.entities.Diary
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.utils.LogUtil
import java.util.*
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.entities.User
import com.zhaoxinyu.bind.utils.Utils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.core.Observable
import java.util.ArrayDeque

class DiaryFragment:Fragment() {

    companion object{
        val DIARY_RECORD="diaryRecord_${MainActivity.loggedUser?.id}"
        val DIARY_VERSION="diaryVersion_${MainActivity.loggedUser?.id}"
        val DIARY_VERSION_TA="diaryVersionTa_${MainActivity.loggedUser?.binderId}"
    }

    val binding get() = _binding!!
    private var _binding:DiaryFragmentBinding?=null

    private var adapter:DiaryListAdapter?=null


    /**
     * 从本地获取日记记录
     */
    private fun loadDairyFromStorage(adapter: DiaryListAdapter){
       try {
           val data=Gson().fromJson<List<Diary>>(MainActivity.sharedPreference.getString(DIARY_RECORD,""),Utils.getType(List::class.java,Diary::class.java))
           //val version=MainActivity.sharedPreference.getInt(DIARY_VERSION,-1)
           if(!data.isEmpty()) {
               adapter.setDiaryData(data)
           } else{
               binding.parent.setBackgroundResource(R.drawable.pic_no_record)
               binding.parent.invalidate()
           }
       }catch (e:Exception){
           LogUtil.e("loadDairyFromStorage",e.message!!)
       }

    }

    /**
     * 从网络获取自己和另一半（如果绑定了的话）的日记记录，并合并展示。
     * @param adapter 用来展示请求结果的RecyclerView的adapter
     * @param newVersion 服务器日记库的新版本
     * @param isBinder 新版本日记库是否是另一半的日记库
     */
    private fun loadDiaryFromNet(adapter: DiaryListAdapter,newVersion:Int,isBinder:Boolean){
        /**
         * 先不考虑优化，直接从服务器拉取全部的日记
         */
        val res= mutableListOf<Diary>()
        val data1=ArrayDeque<Diary>()
        val data2=ArrayDeque<Diary>()
        val user=MainActivity.loggedUser
            //请求计数
        var count=0
        if(user!=null){
            //同时发起两个请求，不论第二个请求结果如何，第二个请求返回时都应该进行合并操作
            val o=Observable.concat(listOf(Network.diaryService.getAllDiary(user.id!!),Network.diaryService.getAllDiary(user.binderId)))
                .onErrorReturn {e->
                    LogUtil.e("[diaryService]:getAllDiary",e.message!!)
                    return@onErrorReturn Result(false,e.message!!)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {r->
                    count++
                    if(count==1){
                        //第一次请求
                        if(r.success){
                            val data=Gson().fromJson<List<Diary>>((r.ojb as String),Utils.getType(List::class.java,Diary::class.java))
                            data1.addAll(data)
                        }
                    }else if(count==2){
                        //第二次请求
                        if(r.success){
                            val data=Gson().fromJson<List<Diary>>((r.ojb as String),Utils.getType(List::class.java,Diary::class.java))
                            data2.addAll(data)
                        }
                        //按顺序合并两个请求并添加月份分割
                        while(!data1.isEmpty()||!data2.isEmpty()){
                            /**
                             * 1.先判断添加哪一个队列的元素：添加时间较大的那个队列的元素或不为空的那个元素
                             * 2.添加到结果数组前需要判断一下是否需要添加月份分割，当结果队列为空或者结果队列
                             * 尾部元素的日期的月份和当前将要添加到结果数组中的元素的月份不同，就需要添加月份分割。
                             * 月份分割的月份是将要添加的那个元素的月份。
                             */
                            val d1=if(!data1.isEmpty()) data1.peekFirst() else null
                            val d2=if(!data2.isEmpty()) data2.peekFirst() else null
                            var candidate:Diary?=null
                            if(d1!=null&&d2!=null&&d1.date>=d2.date){
                                candidate=data1.pollFirst()
                            }else if(d1!=null&&d2!=null&&d1.date<d2.date){
                                candidate=data2.pollFirst()
                            }else if(d1!=null&&d2==null){
                                candidate=data1.pollFirst()
                            }else if(d1==null&&d2!=null){
                                candidate=data2.pollFirst()
                            }
                            if(candidate!=null){
                                if(res.isEmpty()||res[res.size-1].date.month!=candidate.date.month){
                                    res.add(Diary(-1,candidate.date.month+1,candidate.date,""))
                                }
                                res.add(candidate)
                            }
                        }
                        if(!isBinder) diaryVersion=newVersion
                        else taDiaryVersion=newVersion
                        if(!res.isEmpty()){
                            adapter.setDiaryData(res)
                        } else{
                            binding.parent.setBackgroundResource(R.drawable.pic_no_record)
                            binding.parent.invalidate()
                        }
                    }
                }
        }
    }

    /**
     * 请求用户日记
     * @param userId 要请求日记的用户的id
     * @param localVersion 当前的本地日记库版本，用于判断是否需要向服务器请求新的日记。
     */
    private fun requestDiary(userId:Int,localVersion:Int){
        fun updateVersion(userId: Int,version:Int){
            if(userId==MainActivity.loggedUser?.id){
                diaryVersion=version
            }else if(userId==MainActivity.loggedUser?.binderId){
                taDiaryVersion=version
            }
        }
        val o=Network.diaryService.getDiaryVersion(userId)
            .onErrorReturn { e->
                LogUtil.e("[diaryService]:getDiaryVersion",e.message!!)
                return@onErrorReturn Result(false,e.message!!)
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { r->
                LogUtil.d("[diaryService]:getDiaryVersion",r.msg)
                if(r.success){
                    val serverVersion=(r.ojb as Double).toInt()
                    if(localVersion!=-1&&localVersion==serverVersion){
                        //本地保存的日记库版本和服务器的一致
                        if(adapter?.data?.isEmpty()!!) {
                            loadDairyFromStorage(adapter!!)
                            //更新fragment持有的日记库版本
                            updateVersion(userId,localVersion)
                        }
                    }else{
                        //本地没有日记库或者本地保存的日记库版本与服务器不一致
                        if(userId==MainActivity.loggedUser?.id) loadDiaryFromNet(adapter!!,serverVersion,false)
                        else if(userId==MainActivity.loggedUser?.binderId) loadDiaryFromNet(adapter!!,serverVersion,true)
                    }
                }else {
                    //网络请求失败，默认加载本地日记库
                    if(adapter?.data?.isEmpty()!!){
                        loadDairyFromStorage(adapter!!)
                        updateVersion(userId,localVersion)
                    }
                }
            }
    }

    /**
     * 从外部修改自己的日记库的版本号
     */
    fun setDiaryVersion(v:Int){
        diaryVersion=v
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding=DiaryFragmentBinding.inflate(inflater,container,false)

        /*val testData= listOf(Diary(-1,3, Date(),""),Diary(1,MainActivity.loggedUser?.id!!, Date(),"这种方法可以实现上述需求，但是不得不写一个\"limit 1\"，以后改需求查两个的话又需要改为\"limit 2\"，而且这里写一个常量字符串感觉也怪怪的，基于此可以用两种方法代替。"),
            Diary(2,MainActivity.loggedUser?.binderId!!, Date(),"这种方法可以实现上述需求，但是不得不写一个\"limit 1\"，以后改需求查两个的话又需要改为\"limit 2\"，而且这里写一个常量字符串感觉也怪怪的，基于此可以用两种方法代替。"))*/

        adapter= DiaryListAdapter(mutableListOf(), this)
        //adapter?.data?.addAll(testData)
        binding.diaryRecyclerView.adapter=adapter
        binding.diaryRecyclerView.layoutManager=LinearLayoutManager(requireContext())
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.addBtn.setOnClickListener {
            MainActivity.context?.navController?.navigate(R.id.action_homeFragment_to_editDiaryFragment)
        }
        super.onActivityCreated(savedInstanceState)
    }

    override fun onResume() {
        /**
         * 首先，获取保存在本地的版本号,如果当前fragment中保存的版本号为-1，则需要获取本地保存的版本号
         */
        var localVersion=-1
        var taLocalVersion=-1
        if(diaryVersion==-1){
            diaryVersion=MainActivity.sharedPreference.getInt(DIARY_VERSION,-1)
        }
        localVersion=diaryVersion
        if(taDiaryVersion==-1){
            taDiaryVersion=MainActivity.sharedPreference.getInt(DIARY_VERSION_TA,-1)
        }
        taLocalVersion=taDiaryVersion
        /**
         * 然后请求获取服务器的版本号
         */
        if(MainActivity.loggedUser!=null) {
            //请求自己的日记
            requestDiary(MainActivity.loggedUser?.id!!,localVersion)
        }
        if(MainActivity.loggedUser?.binderId!=null){
            //请求另一半的日记
            requestDiary(MainActivity.loggedUser?.binderId!!,taLocalVersion)
        }
        super.onResume()
    }

    override fun onStop() {
        //将日记和日记库版本号保存在本地
        MainActivity.sharedPreference.edit().apply {
            putString(DIARY_RECORD, Gson().toJson(adapter?.data))
            putInt(DIARY_VERSION,diaryVersion)
            putInt(DIARY_VERSION_TA,taDiaryVersion)
            apply()
        }
        super.onStop()
    }

    override fun onDestroyView() {
        _binding=null
        super.onDestroyView()
    }


    override fun onDestroy() {
        adapter=null
        diaryVersion=-1
        taDiaryVersion=-1
        super.onDestroy()
    }
}