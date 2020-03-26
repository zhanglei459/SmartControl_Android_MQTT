package com.zyc.zcontrol;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.zyc.Function;
import com.zyc.StaticVariable;
import com.zyc.webservice.WebService;
import com.zyc.zcontrol.deviceItem.DeviceClass.Device;
import com.zyc.zcontrol.deviceItem.DeviceClass.DeviceTC1;
import com.zyc.zcontrol.deviceItem.SettingActivity;
import com.zyc.zcontrol.deviceScan.DeviceAddChoiceActivity;
import com.zyc.zcontrol.mainActivity.MainDeviceFragmentAdapter;
import com.zyc.zcontrol.mainActivity.MainDeviceListAdapter;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.zyc.Function.getLocalVersionName;
import static java.lang.Integer.parseInt;

public class ServiceActivity extends AppCompatActivity {
    public final static String Tag = "ServiceActivity";




    //region 使用本地广播与service通信
    LocalBroadcastManager localBroadcastManager;
    private MsgReceiver msgReceiver;
    //endregion

    private Toolbar toolbar;


    ConnectService mConnectService;
    String device_mac;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//        toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);


        //region MQTT服务有关
        //region 动态注册接收mqtt服务的广播接收器,
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        msgReceiver = new MsgReceiver();
        IntentFilter intentFilter = new IntentFilter();
        Intent getIntent = this.getIntent();
        if (getIntent.hasExtra("index"))//判断是否有值传入,并判断是否有特定key
        {
            try {
                int index = getIntent.getIntExtra("index", -1);
                device_mac = (((MainApplication) getApplication()).getDeviceList()).get(index).getMac();
                intentFilter.addAction(device_mac);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
//        intentFilter.addAction(ConnectService.ACTION_UDP_DATA_AVAILABLE);
//        intentFilter.addAction(ConnectService.ACTION_MQTT_CONNECTED);
//        intentFilter.addAction(ConnectService.ACTION_MQTT_DISCONNECTED);
//        intentFilter.addAction(ConnectService.ACTION_DATA_AVAILABLE);
        }
        localBroadcastManager.registerReceiver(msgReceiver, intentFilter);
        //endregion

        //region 启动MQTT 服务以及启动,无需再启动
        Intent intent = new Intent(this, ConnectService.class);
        bindService(intent, mMQTTServiceConnection, BIND_AUTO_CREATE);
        //endregion
        //endregion


    }



    @Override
    protected void onDestroy() {
        //注销广播
        localBroadcastManager.unregisterReceiver(msgReceiver);
        //停止服务
        Intent intent = new Intent(ServiceActivity.this, ConnectService.class);
        stopService(intent);
        unbindService(mMQTTServiceConnection);
        super.onDestroy();
    }

    //当服务建立完成时执行此函数
    protected void connected(){

    }

    public final void Send(boolean isUDP, String topic, String message) {
        Log.d(Tag, "Send:[" + topic + "]:" + message);
        mConnectService.Send(isUDP ? null : topic, message);
    }

    //数据接收处理函数
    public void Receive(String ip, int port, String topic, String message) {

    }

    //region MQTT服务有关


    private final ServiceConnection mMQTTServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mConnectService = ((ConnectService.LocalBinder) service).getService();
            connected();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mConnectService = null;
        }
    };

    //广播接收,用于处理接收到的数据
    public class MsgReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (ConnectService.ACTION_UDP_DATA_AVAILABLE.equals(action)) {
                String ip = intent.getStringExtra(ConnectService.EXTRA_UDP_DATA_IP);
                String message = intent.getStringExtra(ConnectService.EXTRA_UDP_DATA_MESSAGE);
                int port = intent.getIntExtra(ConnectService.EXTRA_UDP_DATA_PORT, -1);
                Receive(ip, port, null, message);
            } else if (ConnectService.ACTION_MQTT_CONNECTED.equals(action)) {  //连接成功
                Log.d(Tag, "ACTION_MQTT_CONNECTED");
                connected();
            } else if (ConnectService.ACTION_MQTT_DISCONNECTED.equals(action)) {  //连接失败/断开,尝试重新连接
                Log.w(Tag, "ACTION_MQTT_DISCONNECTED");
            } else if (ConnectService.ACTION_DATA_AVAILABLE.equals(action)) {  //接收到数据
                String topic = intent.getStringExtra(ConnectService.EXTRA_DATA_TOPIC);
                String message = intent.getStringExtra(ConnectService.EXTRA_DATA_MESSAGE);
                Receive(null, -1, topic, message);
            } else if (action.equals(device_mac)) {//接收到设备独立数据
                String ip = intent.getStringExtra(ConnectService.EXTRA_UDP_DATA_IP);
                int port = intent.getIntExtra(ConnectService.EXTRA_UDP_DATA_PORT, -1);
                String topic = intent.getStringExtra(ConnectService.EXTRA_DATA_TOPIC);
                String message = intent.getStringExtra(ConnectService.EXTRA_DATA_MESSAGE);
                Receive(ip, port, topic, message);
            }
        }
    }

    //endregion
//    void broadcastUpdate(String action) {
//        localBroadcastManager.sendBroadcast(new Intent(ConnectService.ACTION_MAINACTIVITY_DEVICELISTUPDATE));
//    }

}
