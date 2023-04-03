package com.zhaoxinyu.bind.ui.map

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.badlogic.gdx.math.Interpolation.BounceIn
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdate
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.baidu.mapapi.search.weather.WeatherDataType
import com.baidu.mapapi.search.weather.WeatherSearch
import com.baidu.mapapi.search.weather.WeatherSearchOption
import com.google.gson.Gson
import com.zhaoxinyu.bind.MainActivity
import com.zhaoxinyu.bind.R
import com.zhaoxinyu.bind.databinding.DiaryItemLayoutBinding
import com.zhaoxinyu.bind.databinding.MapFragmentBinding
import com.zhaoxinyu.bind.logic.entities.BinderInfo
import com.zhaoxinyu.bind.logic.entities.RealTime
import com.zhaoxinyu.bind.logic.entities.Res
import com.zhaoxinyu.bind.ui.HomeFragment
import com.zhaoxinyu.bind.utils.LogUtil
import com.zhaoxinyu.bind.logic.network.Network
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

class MapFragment():Fragment() {

    val binding get() = _binding!!
    private var _binding:MapFragmentBinding?=null

    private val per1=android.Manifest.permission.ACCESS_COARSE_LOCATION
    private val per2=android.Manifest.permission.ACCESS_FINE_LOCATION

    /**
     * 用于定位的客户端
     */
    private var locationClient:LocationClient?=null

    /**
     * 用于显示地图的视图
     */
    private var mapView:MapView?=null

    /**
     * 获取对方信息的线程
     */
    private var infoFetchTask:InfoFetchTask?=null

    private val permissionActivityLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){res->
        if(res.size==2){
            if(res[per1]!!&&res[per2]!!){
                /**
                 * 如果成功获取定位权限
                 */
                startLocation()
                LogUtil.d("locationPermission","true")
            }else{
                Toast.makeText(requireContext(),"你拒绝了定位权限",Toast.LENGTH_SHORT).show()
            }
        }else if(res.size==1){
            if(res[Manifest.permission.READ_PHONE_STATE]!!){
                getNetCondition()
            }else{
                Toast.makeText(requireContext(),"无法读取网络状态！",Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 定位回调监听
     */
    inner class MyLocationListener:BDAbstractLocationListener(){
        override fun onReceiveLocation(p0: BDLocation?) {
            /**
             * 获取到定位后应该做的事情
             */
            if(mapView==null||p0==null){
                return
            }
            /*val locData=MyLocationData.Builder()
                .accuracy(p0.radius)
                .direction(p0.direction)
                .latitude(p0.latitude)
                .longitude(p0.longitude)
                .build()
            mapView?.map?.setMyLocationData(locData)*/
            val info=BinderInfo(p0,getBatteryCapacity(),getNetCondition())
            val o=Network.mapService.updateInfo(MainActivity.loggedUser!!, Gson().toJson(info))
                .onErrorReturn { e->
                    LogUtil.e("[mapService]:updateInfo",e.message!!)
                    return@onErrorReturn com.zhaoxinyu.bind.logic.entities.Result(false,e.message!!)
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {r->
                    LogUtil.d("[mapService]:updateInfo",r.msg)
                }
            LogUtil.d("location",p0.toString())
        }
    }

    /**
     * 后台获取对方信息的线程
     */
    inner class InfoFetchTask:Thread(){
        val isRunning get() = _isRunning
        private var _isRunning=false

        private var oldLat=0.0
        private var oldLng=0.0

        override fun run() {
            while(isRunning){
                val o=Network.mapService.getInfo(MainActivity.loggedUser!!)
                    .onErrorReturn { e->
                        LogUtil.e("[mapService]:getInfo",e.message!!)
                        return@onErrorReturn com.zhaoxinyu.bind.logic.entities.Result(false,e.message!!)
                    }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {r->
                        LogUtil.d("[mapService]:getInfo",r.msg)
                        if(r.success){
                            val info=Gson().fromJson((r.ojb as String),BinderInfo::class.java)
                            //获取到信息后，更新地图定位以及显示信息
                            //更新地图定位
                            val locData=MyLocationData.Builder()
                                .direction(info.location.direction)
                                .latitude(info.location.latitude)
                                .longitude(info.location.longitude)
                                .accuracy(info.location.radius)
                                .build()
                            mapView?.map?.setMyLocationData(locData)
                            val bound=LatLngBounds.Builder()
                                .include(LatLng(info.location.latitude,info.location.longitude))
                                .build()
                            mapView?.map?.setMapStatus(MapStatusUpdateFactory.newLatLngBounds(bound))

                            //更新设备信息
                            binding.batteryText.text="${info.batteryCapacity}%"
                            binding.netConditionText.text=when(info.netWork){
                                BinderInfo.WIFI->"WIFI"
                                BinderInfo.FIVE_G->"5G"
                                BinderInfo.FOUR_G->"4G"
                                BinderInfo.NET_ERROR->"离线"
                                else->"离线"
                            }
                            val batteryIconId=if(info.batteryCapacity==0){
                                R.drawable.battery_0_bar_fill0_wght400_grad0_opsz48
                            }else if(info.batteryCapacity in 1 until 17){
                                R.drawable.battery_1_bar_fill0_wght400_grad0_opsz48
                            }else if(info.batteryCapacity in 17 until 34){
                                R.drawable.battery_2_bar_fill0_wght400_grad0_opsz48
                            }else if(info.batteryCapacity in 34 until 51){
                                R.drawable.battery_3_bar_fill0_wght400_grad0_opsz48
                            }else if(info.batteryCapacity in 51 until 68){
                                R.drawable.battery_4_bar_fill0_wght400_grad0_opsz48
                            }else if(info.batteryCapacity in 68 until 85){
                                R.drawable.battery_5_bar_fill0_wght400_grad0_opsz48
                            }else if(info.batteryCapacity in 85 until 100){
                                R.drawable.battery_6_bar_fill0_wght400_grad0_opsz48
                            }else R.drawable.battery_full_fill0_wght400_grad0_opsz48

                            binding.batteryIcon.setImageResource(batteryIconId)

                            val netIcon=when(info.netWork){
                                BinderInfo.WIFI->R.drawable.wifi_fill1_wght400_grad0_opsz48
                                BinderInfo.FIVE_G->R.drawable._g_fill1_wght400_grad0_opsz48
                                BinderInfo.FOUR_G->R.drawable._g_mobiledata_fill1_wght400_grad0_opsz48
                                else->R.drawable.signal_disconnected_fill1_wght400_grad0_opsz48
                            }

                            binding.netConditionIcon.setImageResource(netIcon)

                            /**
                             * 更新天气信息
                             */
                            if(oldLat!=info.location.latitude||oldLng!=info.location.longitude){
                                val o=Network.weatherService.getRealTimeWeather(info.location.latitude,info.location.longitude)
                                    .onErrorReturn { e->
                                        LogUtil.e("[weatherService]:getRealTimeWeather",e.message!!)
                                        return@onErrorReturn RealTime("",null)
                                    }
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe {r->
                                        LogUtil.d("[weatherService]:getRealTimeWeather",r.toString())
                                        if(r.status=="ok"){
                                            binding.tempText.text="${r.realtimeRes?.realtime?.temperature?.toInt()?.toString()}℃"
                                            val weatherIcon=when(r.realtimeRes?.realtime?.skycon){
                                                "CLEAR_DAY","CLEAR_NIGHT"-> R.drawable.sunny_fill1_wght400_grad0_opsz48
                                                "PARTLY_CLOUDY_DAY","PARTLY_CLOUDY_NIGHT","CLOUDY"->R.drawable.cloudy_fill1_wght400_grad0_opsz48
                                                "LIGHT_RAIN","MODERATE_RAIN","HEAVY_RAIN","STORM_RAIN"->R.drawable.rainy_fill1_wght400_grad0_opsz48
                                                "LIGHT_SNOW","MODERATE_SNOW","HEAVY_SNOW","STORM_SNOW"->R.drawable.weather_snowy_fill1_wght400_grad0_opsz48
                                                else ->R.drawable.sunny_fill1_wght400_grad0_opsz48
                                            }
                                            binding.weatherIcon.setImageResource(weatherIcon)
                                        }
                                    }
                            }

                            //更新位置信息
                            oldLat=info.location.latitude
                            oldLng=info.location.longitude

                        }
                    }
                sleep(3546)
            }
        }

        fun startFetch(){
            if(!isRunning){
                _isRunning=true
                this.start()
            }
        }

        fun endFetch(){
            if(isRunning){
                _isRunning=false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        //首先检查权限
        if(ContextCompat.checkSelfPermission(requireContext(), per1)==PackageManager.PERMISSION_GRANTED
            &&ContextCompat.checkSelfPermission(requireContext(),per2)==PackageManager.PERMISSION_GRANTED ){
            /**
             * 如果拥有权限
             */
            startLocation()
        }else{
            permissionActivityLauncher.launch(arrayOf(per1,per2))
        }
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding= MapFragmentBinding.inflate(inflater,container,false)

        //显示定位的头像
        val iconBinding=DiaryItemLayoutBinding.inflate(inflater,container,false)
        if(HomeFragment.taIconFile!=null) iconBinding.iconView.setImageURI(Uri.parse(HomeFragment.taIconFile?.path))

        mapView=binding.bmapView
        mapView?.map?.apply {
            isMyLocationEnabled=true
            setMyLocationConfiguration(MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL,
                false,
                BitmapDescriptorFactory.fromView(iconBinding.root)
            ))
        }
        mapView?.showZoomControls(false)
        return binding.root
    }


    override fun onPause() {
        binding.bmapView.onPause()
        infoFetchTask?.endFetch()
        infoFetchTask=null
        super.onPause()
    }

    override fun onResume() {
        binding.bmapView.onResume()
        infoFetchTask=InfoFetchTask()
        infoFetchTask?.startFetch()
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        locationClient?.stop()
        binding.bmapView.onDestroy()
        _binding=null
        mapView=null
        super.onDestroy()
    }

    /**
     * 初始化定位客户端并开启定位
     */
    private fun startLocation(){
        locationClient= LocationClient(requireContext())
        val options=LocationClientOption().apply {
            openGps=true
            coorType="bd09ll"
            scanSpan=1377
        }
        //设置定位选项
        locationClient?.locOption=options
        //注册定位监听
        locationClient?.registerLocationListener(MyLocationListener())
        //开始定位
        locationClient?.start()

    }

    /**
     * 获取手机电量
     */
    fun getBatteryCapacity():Int{
        /**
         * 获取电池电量的代码。
         */
        val bManager= activity?.getSystemService(Activity.BATTERY_SERVICE) as BatteryManager
        val capacity=bManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        LogUtil.d("battery",capacity.toString())
        return capacity
    }

    /**
     * 获取手机网络情况
     */
    fun getNetCondition():String{
        /**
         * 获取网络类型的代码
         */
        val cManager= activity?.getSystemService(Activity.CONNECTIVITY_SERVICE) as ConnectivityManager
        val tManager= activity?.getSystemService(Activity.TELEPHONY_SERVICE) as TelephonyManager
        var res=""

        //if(cManager==null) return BinderInfo.NET_ERROR
        val netInfo= if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cManager.activeNetwork
        } else {
            cManager.activeNetworkInfo
        }
        if(netInfo==null) return  BinderInfo.NET_ERROR

        val wifiInfo=cManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)

        if(wifiInfo==null) return  BinderInfo.NET_ERROR
        else{
            val wifiState=wifiInfo.state
            if(wifiState==null) return  BinderInfo.NET_ERROR
            else if(wifiState==NetworkInfo.State.CONNECTED||wifiState==NetworkInfo.State.DISCONNECTING){
                return  BinderInfo.WIFI
            }
        }

        var netType=-1
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionActivityLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
        }else{
            netType=tManager.networkType
        }

        if(netType==-1) return BinderInfo.NET_ERROR

        res=when(netType){
            TelephonyManager.NETWORK_TYPE_LTE,19->BinderInfo.FOUR_G
            TelephonyManager.NETWORK_TYPE_NR->BinderInfo.FIVE_G
            else ->BinderInfo.NET_ERROR
        }
        return res
    }

}