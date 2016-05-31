package com.example.autoreply;

import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainSetting extends Activity {
	
	private EditText editmsg;
	private EditText editfriend;
	private Button startbtn;
	private Button aboutbtn;
	private Button setbtn;
	private CheckBox autobox;
	private CheckBox allbox;
	private LinearLayout linearlayout3;
	//系统版本是否支持自动回复，需Android4.3以上才能直接通过组件id搜索
	private boolean canAuto;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {  
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.activity_main);
	    
	    //检查系统版本
	    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
	    	canAuto = true;
	    else
	    	canAuto = false;
	    
	    autobox = (CheckBox) this.findViewById(R.id.autocheck);
	    allbox = (CheckBox) this.findViewById(R.id.allcheck);
	    editmsg = (EditText) this.findViewById(R.id.editmsg);
	    editfriend = (EditText) this.findViewById(R.id.editfriend);
	    startbtn = (Button) this.findViewById(R.id.startbtn);
	    aboutbtn = (Button) this.findViewById(R.id.aboutbtn);
	    setbtn = (Button) this.findViewById(R.id.setbtn);
	    linearlayout3 = (LinearLayout) this.findViewById(R.id.linearLayout3);
	    
	    //设置默认信息
	    editmsg.setText(StaticData.message);
	    editfriend.setText(StaticData.friend);
	    linearlayout3.setVisibility(View.GONE);
	    
	    //判断并显示辅助服务是否开启
	    this.showInfo();
	    
	    autobox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton cb, boolean b) {
				if(!canAuto) {
					showTips("_自动回复需Android4.3及以上_");
					return;
				}
				if(b) {
					StaticData.auto = true;
					linearlayout3.setVisibility(View.VISIBLE);
					showTips("_已开启自动回复_");
				} else {
					StaticData.auto = false;
					linearlayout3.setVisibility(View.GONE);
					showTips("_已关闭自动回复_");
				}
			}
	    	
	    });
	    
	    allbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton cb, boolean b) {
				if(b) {
					StaticData.showall = false;
					showTips("_锁屏界面只显示消息发送者_");
				} else {
					StaticData.showall = true;
					showTips("_锁屏界面显示详细消息内容_");
				}
			}
	    	
	    });
	    
	    startbtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String msg = editmsg.getText().toString();
				if( !msg.isEmpty() ) {
					Log.i("demo", msg);
					StaticData.message = msg;
				} else {
					StaticData.message = "您好本人在忙待会回您请稍等  【自动回复】";
				}
				String friend = editfriend.getText().toString();
				if( !friend.isEmpty() ) {
					StaticData.friend = friend;
					StaticData.isfriend = true;
				} else {
					StaticData.friend = "";
					StaticData.isfriend = false;
					
				}
				showTips("成功应用配置");
			}
			
		});
	    
	    aboutbtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainSetting.this, About.class);
				startActivity(intent);
			}
		});
	    
	    setbtn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
			}
		});
	    
	}
	
	//显示提示信息，在按钮事件监听器内调用的
	private void showTips(String info) {
		Toast.makeText(this, info, Toast.LENGTH_SHORT).show();
	}
	
	private void showInfo() {
		if(serviceIsRunning())
	    	setbtn.setText("服务正在运行 >>");
	    else
	    	setbtn.setText("服务尚未开启 >>");
	}
	//获取系统正在运行的服务列表来判断辅助服务是否开启
	private boolean serviceIsRunning() {
		ActivityManager am = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningServiceInfo> lists = am.getRunningServices(150);//获取数量可适当调整
		for (RunningServiceInfo info : lists) {
			if (info.service.getClassName().equals("com.example.autoreply.AutoReplyService")) {
				return true;
			}
		}
		return false;
		
	}
	
	@Override
	public void onResume(){
		super.onResume();  
		showInfo();
	}
	
	//监听返回键并显示退出提示框
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		 if(keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME){
	         showExitDialog();
		 }
		 return super.onKeyDown(keyCode, event);
	}
	//退出提示框
	private void showExitDialog() {  
        AlertDialog.Builder builder = new Builder(MainSetting.this);
        String message = "";
        if(serviceIsRunning())
        	message = "服务正在运行，关闭后仍会回复\n";
        else
        	message = "服务已经关闭\n";
        builder.setMessage(message + "确定要退出吗?");
        builder.setPositiveButton("确认", 
        new android.content.DialogInterface.OnClickListener() {  
            @Override  
            public void onClick(DialogInterface dialog, int which) {  
            	finish();  
            }  
        });  
        builder.setNegativeButton("取消", 
        new android.content.DialogInterface.OnClickListener() {  
            @Override  
            public void onClick(DialogInterface dialog, int which) {  
                dialog.dismiss();  
            }  
        });  
        builder.create().show();  
    }
}
