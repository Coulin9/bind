package com.zhaoxinyu.bind.ui.user

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.MyFragmentBinding
import com.zhaoxinyu.bind.logic.UserRepository
import com.zhaoxinyu.bind.logic.entities.Result
import com.zhaoxinyu.bind.logic.entities.User
import com.zhaoxinyu.bind.logic.network.Network
import com.zhaoxinyu.bind.ui.HomeFragment
import com.zhaoxinyu.bind.utils.LogUtil
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

class MyFragment:Fragment() {
    lateinit var binding: MyFragmentBinding

    private val navController get() = MainActivity.context?.navController!!

    private fun bindData(){
        if(MainActivity.loggedUser!=null&&HomeFragment.iconFile!=null){
            binding.userName.text=MainActivity.loggedUser?.userName
            binding.account.text=MainActivity.loggedUser?.account
            binding.headIconView.setImageURI(Uri.parse(HomeFragment.iconFile?.path))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LogUtil.d("MyFragment","onCreate")
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding=MyFragmentBinding.inflate(inflater,container,false)
        bindData()
        LogUtil.d("MyFragment","onCreateView")
        return binding.root
    }

    override fun onResume() {
        LogUtil.d("MyFragment","onResume")
        bindData()
        super.onResume()

    }

    override fun onPause() {
        LogUtil.d("MyFragment","onPause")
        super.onPause()
    }

    override fun onStop() {
        LogUtil.d("MyFragment","onStop")
        super.onStop()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.updateMyInfoBtn.setOnClickListener {
            navController.navigate(R.id.action_homeFragment_to_updateUserInfoFragment)
        }
        binding.logoutBtn.setOnClickListener {
            val dialog=LogoutConfirmDialog()
            dialog.show(parentFragmentManager,null)
        }
        binding.unRegisterBtn.setOnClickListener {
            val dialog=UnRegisterConfirmDialog()
            dialog.show(parentFragmentManager,null)
        }
        binding.myBinderBtn.setOnClickListener {
            if(MainActivity.loggedUser?.binderId!=null){
                Network.userService.getBinder()
                    .onErrorReturn { e->
                        LogUtil.e("[userService]:getBinder",e.message!!)
                        return@onErrorReturn Result(false,e.message!!)
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { r->
                        LogUtil.d("[userService]:getBinder",r.msg)
                        if(r.success){
                            val user= Gson().fromJson(r.ojb as String, User::class.java)
                            val dialog=ShowBinderDialog(user)
                            dialog.show(parentFragmentManager,null)
                        }
                    }
            }else{
                Toast.makeText(requireContext(),"你还没有绑定另一半！",Toast.LENGTH_SHORT).show()
            }
        }
        binding.bindBtn.setOnClickListener {
            if(MainActivity.loggedUser?.binderId==null){
                Network.userService.enterBindingState()
                    .onErrorReturn { e->
                        LogUtil.e("[userService]:enterBindingState",e.message!!)
                        return@onErrorReturn Result(false,e.message!!)
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { r->
                        LogUtil.d("[userService]:enterBindingState",r.msg)
                        if(r.success){
                            val bindCode=r.ojb as String
                            val dialog=BindDialog(bindCode)
                            HomeFragment.bindStateCheckTask?.setBindDialog(dialog)
                            dialog.show(parentFragmentManager,null)
                        }else{
                            Toast.makeText(requireContext(),"准备绑定失败！",Toast.LENGTH_SHORT).show()
                        }
                    }
            }else{
                Toast.makeText(requireContext(),"你已经绑定了另一半，请先解绑！",Toast.LENGTH_SHORT).show()
            }
        }

    }


    override fun onDestroy() {
        LogUtil.d("MyFragment","onDestroy")
        super.onDestroy()
    }
}