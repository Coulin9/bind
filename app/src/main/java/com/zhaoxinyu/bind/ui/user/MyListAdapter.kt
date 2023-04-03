package com.zhaoxinyu.bind.ui.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.TransformationUtils
import com.bumptech.glide.request.RequestOptions
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.MyListType1Binding
import com.zhaoxinyu.bind.databinding.MyListType2Binding
import com.zhaoxinyu.bind.databinding.MyListType3Binding
import com.zhaoxinyu.bind.databinding.MyListType4Binding
import jp.wasabeef.glide.transformations.CropCircleTransformation

class MyListAdapter(val typeList:List<Int>):RecyclerView.Adapter<ViewHolder>() {
    inner class ViewHolder1(val binding: MyListType1Binding):RecyclerView.ViewHolder(binding.root)
    inner class ViewHolder2(val binding: MyListType2Binding):RecyclerView.ViewHolder(binding.root)
    inner class ViewHolder3(val binding: MyListType3Binding):RecyclerView.ViewHolder(binding.root)
    inner class ViewHolder4(val binding: MyListType4Binding):RecyclerView.ViewHolder(binding.root)

    companion object{
        const val TYPE_ONE=1
        const val TYPE_TWO=2
        const val TYPE_THREE=3
        const val TYPE_FORE=4
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when(viewType){
            TYPE_ONE->{
                val binding=MyListType1Binding.inflate(LayoutInflater.from(parent.context),parent,false)
                ViewHolder1(binding)
            }
            TYPE_TWO->{
                val binding=MyListType2Binding.inflate(LayoutInflater.from(parent.context),parent,false)
                ViewHolder2(binding)
            }
            TYPE_THREE->{
                val binding=MyListType3Binding.inflate(LayoutInflater.from(parent.context),parent,false)
                ViewHolder3(binding)
            }
            TYPE_FORE->{
                val binding=MyListType4Binding.inflate(LayoutInflater.from(parent.context),parent,false)
                ViewHolder4(binding)
            }
            else->{
                val binding=MyListType4Binding.inflate(LayoutInflater.from(parent.context),parent,false)
                ViewHolder4(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(holder is ViewHolder1){
            holder.binding.apply {
                userNameTextView.text=MainActivity.loggedUser?.userName
                accountTextView.text=MainActivity.loggedUser?.account
                Glide.with(this.root.context)
                    .load(MainActivity.loggedUser?.iconPath)
                    .fitCenter()
                    .apply(RequestOptions.bitmapTransform(CircleCrop()))
                    .error(R.drawable.test)
                    .into(headIcon)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return typeList[position]
    }

    override fun getItemCount(): Int {
        return typeList.size
    }

}