package com.zhaoxinyu.bind.ui.user

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Images.Media
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.UpdateUserInfoFragmentBinding
import com.zhaoxinyu.bind.logic.UserRepository
import com.zhaoxinyu.bind.logic.entities.User
import com.zhaoxinyu.bind.ui.HomeFragment
import com.zhaoxinyu.bind.utils.LogUtil
import java.io.File
import java.io.FileOutputStream
import java.util.*

class UpdateUserInfoFragment:Fragment() {

    val binding get() = _binding!!
    private var _binding:UpdateUserInfoFragmentBinding?=null

    private var birthDay:Date?=null



    private val activityResultLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(it!=null&&it.resultCode== Activity.RESULT_OK) {
            val url = it.data?.data!!
            //设定要查询的数据
            val projection =
                arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATA)
            //使用contentProvider进行查询
            val cursor =
                MainActivity.context?.contentResolver?.query(url, projection, null, null, null)
            cursor?.moveToFirst()
            //保存图片文件的路径和大小
            val path = cursor?.getString(0)
            val size = cursor?.getString(1)
            LogUtil.d("selectImages", "path:${path},size:${size}")

            //下面使用Glide进行图片压缩
            Glide.with(requireContext())
                .asBitmap()
                .load(path)
                .override(200,200)
                .centerCrop()
                .into(object :CustomTarget<Bitmap>(){
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        binding.iconPicker.setImageBitmap(resource)
                        /**
                         * 将压缩过后的图片存储为文件
                         */
                        try {
                            val iconFile=HomeFragment.iconFile!!
                            if(iconFile.exists()){
                                val deleted=iconFile.delete()
                                //println(deleted)
                            }
                            val out=FileOutputStream(iconFile)
                            resource.compress(Bitmap.CompressFormat.PNG,100,out)
                            out.close()
                        }catch (e:Exception){
                            LogUtil.e("ImageCompress",e.message!!)
                        }

                        UserRepository.updateUserIcon(HomeFragment.iconFile)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }
                })
        }
    }

    /**
     * 文件读取及相册访问权限申请的回调处理
     */
    private val permissionResultLauncher=registerForActivityResult(ActivityResultContracts.RequestPermission()){res->
        if (res){
            launchActivityForRes()
        }else{
            Toast.makeText(requireContext(),"你拒绝了文件读写权限，app将无法读取图片", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchActivityForRes(){
        //启动系统相册选择图片
        val intent= Intent()
        intent.apply {
            action= Intent.ACTION_PICK
            type="image/*"
        }
        activityResultLauncher.launch(intent)
    }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            _binding= UpdateUserInfoFragmentBinding.inflate(inflater,container,false)

            //设置gender_selector的选项
            val items = arrayOf(getString(R.string.gender_male),getString(R.string.gender_female))
            (binding.genderSelectInputText.editText as? MaterialAutoCompleteTextView)?.setSimpleItems(items)


            //设置需要被修改的默认值
            val user=MainActivity.loggedUser!!
            binding.userNameInputText.editText?.setText(user.userName)
            binding.passwordInputText.editText?.setText(user.password)
            binding.ensurePasswordInputText.editText?.setText(user.password)
            //binding.genderSelectInputText.editText?.setText(if(user.gender=="male") "男" else "女")
            binding.birthDayText.text="生日：${if(user.birthday!=null) user.birthday?.year!!-100+2000 else null}" +
                    "/${if(user.birthday!=null) user.birthday?.month!!+1 else null}/${user.birthday?.date}"
            binding.iconPicker.setImageURI(Uri.parse(HomeFragment.iconFile?.path))

            //设置日期选择按钮监听
            binding.datePickBtn.setOnClickListener {
                val datePicker=MaterialDatePicker.Builder.datePicker()
                    .setTitleText("选择生日")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build()
                datePicker.addOnPositiveButtonClickListener { time->
                    birthDay= Date(time)
                    binding.birthDayText.text="生日：${birthDay!!.year-100+2000}/${birthDay!!.month+1}/${birthDay!!.date}"
                }
                datePicker.show(parentFragmentManager,null)
            }

            binding.iconPicker.setOnClickListener {
                //先检查一下是否有权限
                var permissionCode:String?=null
                //安卓13及以上申请图拍读取权限，否则申请存储读取权限
                if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.TIRAMISU){
                    //val perm=ContextCompat.checkSelfPermission(requireContext(),Manifest.permission.READ_MEDIA_VIDEO)
                    permissionCode= Manifest.permission.READ_MEDIA_IMAGES
                }else{
                    permissionCode= Manifest.permission.READ_EXTERNAL_STORAGE
                }

                if(
                    ContextCompat.checkSelfPermission(requireContext(),permissionCode)!= PackageManager.PERMISSION_GRANTED
                ){
                    //没有权限就手动请求权限
                    permissionResultLauncher.launch(permissionCode)
                }else{
                    //有权限的话就直接打开系统相册
                    launchActivityForRes()
                }
            }

            return binding.root
        }
    override fun onActivityCreated(savedInstanceState: Bundle?) {

        //二次密码校验
        binding.ensurePasswordInputText.editText?.addTextChangedListener {
            if(binding.passwordInputText.editText?.text?.toString()!=it?.toString()){
                binding.ensurePasswordInputText.error=getString(R.string.wrong_ensure_password)
            }else binding.ensurePasswordInputText.error=null
        }

        //性别选择校验
        binding.genderSelectInputText.editText?.addTextChangedListener {
            if(binding.genderSelectInputText.editText?.text?.toString()==""){
                binding.genderSelectInputText.error=getString(R.string.empty_gender_error)
            }else binding.genderSelectInputText.error=null
        }

        binding.submitBtn.setOnClickListener {
            //密码不能为空
            if(binding.passwordInputText.editText?.text?.toString()==""){
                binding.passwordInputText.error = getString(R.string.empty_password_error)
            }else binding.passwordInputText.error=null
            //确认密码不能为空
            if(binding.ensurePasswordInputText.editText?.text?.toString()==""){
                binding.ensurePasswordInputText.error=getString(R.string.empty_ensure_password_error)
            }else binding.ensurePasswordInputText.error=null
            //性别不能为空
            if(binding.genderSelectInputText.editText?.text?.toString()==""){
                binding.genderSelectInputText.error=getString(R.string.empty_gender_error)
            }else binding.genderSelectInputText.error=null
            //没有格式错误就提交
            if(binding.passwordInputText.error==null &&binding.ensurePasswordInputText.error==null&&binding.genderSelectInputText.error==null){
                val userName=binding.userNameInputText.editText?.text?.toString()!!
                val password=binding.passwordInputText.editText?.text?.toString()!!
                val gender=when(binding.genderSelectInputText.editText?.text?.toString()){
                    getString(R.string.gender_male)->"male"
                    getString(R.string.gender_female)->"female"
                    else -> ""
                }
                val user=MainActivity.loggedUser
                val newUser=User(user?.id,user?.account!!,userName,password,gender,birthDay,user.iconPath,user.binderId)

                //更新个人信息
                UserRepository.updateUserInfo(newUser,requireContext())
            }
        }

        super.onActivityCreated(savedInstanceState)
    }


    override fun onDestroyView() {
        _binding=null
        super.onDestroyView()
    }
}