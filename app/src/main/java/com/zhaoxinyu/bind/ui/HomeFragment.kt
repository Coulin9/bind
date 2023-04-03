package com.zhaoxinyu.bind.ui

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.HomeFragmentBinding
import com.zhaoxinyu.bind.logic.LoginStateCheckTask
import com.zhaoxinyu.bind.ui.diary.DiaryFragment
import com.zhaoxinyu.bind.ui.map.MapFragment
import com.zhaoxinyu.bind.ui.user.*
import com.zhaoxinyu.bind.ui.watchTogether.WatchTestFragment
import com.zhaoxinyu.bind.utils.LogUtil
import java.io.File

class HomeFragment:Fragment() {
    companion object{
        val buttonNavBar get() = _buttonNavBar
        private var _buttonNavBar: BottomNavigationView?=null

        var iconFile: File?=null
        var taIconFile: File?=null

        //不断检查绑定状态的线程
        val bindStateCheckTask get() = _bindStateCheckTask
        private var _bindStateCheckTask:BindStateCheckTask?=null

    }
    private val binding:HomeFragmentBinding get() = _binding!!
    private var _binding:HomeFragmentBinding?=null

    //用来记录上次退出时正在展示的页面的序号
    private var lastPageNum=0

    //不断获取头像的线程
    var iconRequestTask: IconRequestTask?=null



    override fun onCreate(savedInstanceState: Bundle?) {
        //如果进入了主页且未开启登陆检查，那么就开启。
        if(MainActivity.loginStateCheckTask==null||!MainActivity.loginStateCheckTask?.isAlive!!){
            MainActivity.loginStateCheckTask=LoginStateCheckTask()
            MainActivity.loginStateCheckTask?.start()
        }


        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding=HomeFragmentBinding.inflate(inflater,container,false)
        binding.viewPager.adapter=HomePagerAdapter(listOf<Fragment>(MapFragment(),DiaryFragment(),WatchTestFragment(),MyFragment()),this.requireActivity())
        //禁止用户滑动切换页面。
        binding.viewPager.isUserInputEnabled=false
        binding.viewPager.isSaveEnabled=false
        //在HomeFragment的最底部设置一个ButtonNavigationBar来作为导航。与大部分国产app类似
        _buttonNavBar=binding.btnNavigationBar
        return binding.root
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {

        //初始化头像文件
        val iconStoragePath= requireContext().getExternalFilesDir(null)?.path+"/Pictures/${MainActivity.loggedUser?.account}_icon.PNG"
        val taIconFilePath= requireContext().getExternalFilesDir(null)?.path+"/Pictures/${MainActivity.loggedUser?.binderId}_icon.PNG"
        if(MainActivity.loggedUser!=null) iconFile=File(iconStoragePath)
        if(MainActivity.loggedUser?.binderId!=null) taIconFile=File(taIconFilePath)


        binding.btnNavigationBar.setOnItemSelectedListener {item->
            when(item.itemId){
                R.id.location->{
                    binding.viewPager.setCurrentItem(0,false)
                    true
                }
                R.id.dairy->{
                    binding.viewPager.setCurrentItem(1,false)
                    true
                }
                R.id.watch->{
                    binding.viewPager.setCurrentItem(2,false)
                    true
                }
                R.id.my->{
                    binding.viewPager.setCurrentItem(3,false)
                    true
                }
                else -> false
            }
            //true
        }
        super.onActivityCreated(savedInstanceState)
    }

    override fun onDestroyView() {
        _binding=null
        _buttonNavBar=null
        super.onDestroyView()
    }

    override fun onResume() {
        try {
            binding.viewPager.setCurrentItem(lastPageNum,false)
        }catch (e:Exception){
            e.printStackTrace()
        }

        iconRequestTask=IconRequestTask(requireContext())
        iconRequestTask?.startRequest()

        _bindStateCheckTask= BindStateCheckTask(requireContext())
        bindStateCheckTask?.startCheck()

        LogUtil.d("HomeFragment","onResume")
        super.onResume()
    }

    override fun onPause() {
        LogUtil.d("HomeFragment","onPause")

        iconRequestTask?.stopRequest()
        iconRequestTask=null

        _bindStateCheckTask?.endCheck()
        _bindStateCheckTask=null

        super.onPause()
    }

    override fun onStop() {
        lastPageNum= binding.viewPager.currentItem
        LogUtil.d("HomeFragment","onStop")
        super.onStop()
    }

    override fun onDestroy() {
        LogUtil.d("HomeFragment","onDestroy")
        iconFile=null
        taIconFile=null
        super.onDestroy()
    }
}