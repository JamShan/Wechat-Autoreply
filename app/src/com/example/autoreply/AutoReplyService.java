package com.example.autoreply;
import java.util.List;
import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public class AutoReplyService extends AccessibilityService {  
	
	private boolean canGet = false;//能否回复
	private boolean enableKeyguard = true;//默认有屏幕锁
	private int mode = 1;//微信通知模式：1.详细通知2.非详细通知
	private AccessibilityNodeInfo editText = null;

	//锁屏、唤醒相关
	private KeyguardManager km;
	private KeyguardLock kl;
	private PowerManager pm;
	private PowerManager.WakeLock wl = null;
	private ScreenOffReceiver sreceiver;
	private PhoneReceiver preceiver;
	
    /** 唤醒和解锁相关*/
	private void wakeAndUnlock(boolean unLock)
	{
	    if(unLock)
	    {
	    	if(!pm.isScreenOn()) {
	    		//获取电源管理器对象
	    		wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "bright");
		   		//点亮屏幕
		    	wl.acquire();
		    	Log.i("demo", "亮屏");
	    	}
	    	if(km.inKeyguardRestrictedInputMode()) {
			    //解锁
	    		enableKeyguard = false;
	    		//kl.reenableKeyguard();
		        kl.disableKeyguard();
		        Log.i("demo", "解锁");
	    	}
	    } else {
	    	if(!enableKeyguard) {
	    		//锁屏
	    		kl.reenableKeyguard();
	    		Log.i("demo", "加锁");
	    	}
	    	if(wl != null) {
	    	    //释放wakeLock，关灯
	      	    wl.release();
	      	    wl = null;
	      	    Log.i("demo", "关灯");
	        }
	    }
	}

	/** 通过文本查找*/
	public  AccessibilityNodeInfo findNodeInfosByText(AccessibilityNodeInfo nodeInfo, String text) {
        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText(text);
        if(list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
	
	//通过组件名递归查找编辑框
    private void findNodeInfosByName(AccessibilityNodeInfo nodeInfo, String name) {
    	if(name.equals(nodeInfo.getClassName())) {
    			editText = nodeInfo;
    			return;
    	}
		for(int i = 0; i < nodeInfo.getChildCount(); i++) {
	        findNodeInfosByName(nodeInfo.getChild(i), name);
	    }
    }
    
	/** 点击事件*/
    public void performClick(AccessibilityNodeInfo nodeInfo) {
        if(nodeInfo == null) {
            return;
        }
        if(nodeInfo.isClickable()) {
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            performClick(nodeInfo.getParent());
        }
    }

    /** 返回事件*/
    public  void performBack(AccessibilityService service) {
        if(service == null) {
            return;
        }
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    }
    
    /** 处理相关事件*/
    @Override  
    public void onAccessibilityEvent(AccessibilityEvent event) {  
        int eventType = event.getEventType();
        //Log.i("demo", Integer.toString(eventType));
        switch (eventType) {  
        //第一步：监听通知栏消息  
        case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
        	
            List<CharSequence> texts = event.getText();
            if (!texts.isEmpty()) {
            	String message = texts.get(0).toString();
            	StaticData.total++;
            	setData(message);
            	
            	//Log.i("demo", "收到通知栏消息:" + message);
            	//收到信息发送更新锁屏界面广播
            	Intent i = new Intent("com.example.autoreply.SHOW_ACTION");
            	sendBroadcast(i);
            	
            	if(!StaticData.auto)
            		return;
            	
            	if(message.equals("微信：你收到了一条消息。"))
            		mode = 2;
            	else
            		mode = 1;
            	
            	//判断是否指定好友并过滤
            	if(StaticData.isfriend && (mode == 1) && ( !message.contains(StaticData.friend) )) {
        			return;
            	}
                //模拟打开通知栏消息  
                if (event.getParcelableData() != null && event.getParcelableData() instanceof Notification) {
                	Log.i("demo", "标题栏Get=true");
                	canGet = true;
                	wakeAndUnlock(true);
                	try {
                		Notification notification = (Notification) event.getParcelableData();  
                		PendingIntent pendingIntent = notification.contentIntent;  
                        pendingIntent.send();
                    } catch (CanceledException e) {  
                        e.printStackTrace();  
                    }
                }
                break;
            }

        //第二步：监听是否进入微信聊天界面    
        case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
	    	if(canGet) {
	        	canGet = false;
	        	reply();
	        	performBack(this);
	        }
        	break;
        }
    }   
 

    @SuppressWarnings("static-access")
	private void setData(String data) {
    	Time time = new Time(); 
		time.setToNow(); 
		int hour = time.hour;
		int minute = time.minute;
		if(StaticData.showall) {

		} else {
			if(!data.equals("微信：你收到了一条消息。")) {
				data = data.split(":")[0];
				//Log.i("demo", "showall=" + StaticData.showall+" data=" + data);
				data += " 发来一条消息。";
			}
		}
		data = data.format("%s     %02d:%02d", data, hour, minute);
		StaticData.data.add(data);
    }
    
    /** 自动回复*/
    @SuppressLint("NewApi")
    private void reply() {
    	AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if(nodeInfo == null) {
            return;
        }
        
        AccessibilityNodeInfo targetNode = null;
        
        //判断是否群聊以及mode=2时是否匹配好友
        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(StaticData.qunId);
    	if( !list.isEmpty() ) {
    		targetNode = list.get(0);
    		
			String temp = targetNode.getText().toString();
			if( temp.matches(".*\\(([3-9]|[1-9]\\d+)\\)") || (mode == 2 && StaticData.isfriend && ( !temp.equals(StaticData.friend) ) ) ) {
				performBack(this);
				wakeAndUnlock(false);
				return;
    		}
    	}
        
        //查找文本编辑框
        if(editText == null) {
        	Log.i("demo", "正在查找编辑框...");
        	//第一种查找方法
        	List<AccessibilityNodeInfo> list1 = nodeInfo.findAccessibilityNodeInfosByViewId(StaticData.editId);
        	if( !list1.isEmpty() )
        		editText = list1.get(0);
        	//第二种查找方法
        	if(editText == null)
        		findNodeInfosByName(nodeInfo, "android.widget.EditText");
        }
        targetNode = editText;
        
        //粘贴回复信息
        if(targetNode != null) {
        	//android >= 21=5.0时可以用ACTION_SET_TEXT 
	        //android >= 18=4.3时可以通过复制粘贴的方法,先确定焦点，再粘贴ACTION_PASTE 
	        //使用剪切板
	        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); 
	        ClipData clip = ClipData.newPlainText("message", StaticData.message);
	        clipboard.setPrimaryClip(clip);
	        //Log.i("demo", "设置粘贴板");
	        //焦点 （n是AccessibilityNodeInfo对象） 
	        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
	        //Log.i("demo", "获取焦点");
	        //粘贴进入内容 
	        targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
	        //Log.i("demo", "粘贴内容");
        }
        
        //查找发送按钮
        if(targetNode != null) { //通过组件查找
        	Log.i("demo", "查找发送按钮...");
        	targetNode = null;
        	List<AccessibilityNodeInfo> list2 = nodeInfo.findAccessibilityNodeInfosByViewId(StaticData.sendId);
        	if( !list2.isEmpty() )
        		targetNode = list2.get(0);
        	//第二种查找方法
        	if(targetNode == null)
        		targetNode = findNodeInfosByText(nodeInfo, "发送");
        }

        //点击发送按钮
        if(targetNode != null) {
        	Log.i("demo", "点击发送按钮中...");
            final AccessibilityNodeInfo n = targetNode;
            performClick(n);
            StaticData.replaied++;
            
        }
        //恢复锁屏状态
        wakeAndUnlock(false);
    } 
    
    @Override
    public void onInterrupt() {
        Toast.makeText(this, "微信助手服务被中断啦~", Toast.LENGTH_LONG).show();
    }

    //服务开启时初始化
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i("demo", "开启");
        //获取电源管理器对象
    	pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
    	//得到键盘锁管理器对象
	    km= (KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
	    //得到键盘锁管理器对象
	    kl = km.newKeyguardLock("unLock");
	    
	    editText = null;
	    
	    //注册广播接收器
	    sreceiver = new ScreenOffReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(sreceiver, filter);
        
        preceiver = new PhoneReceiver();
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        registerReceiver(preceiver, filter);
        
        tm = (TelephonyManager)getSystemService(Service.TELEPHONY_SERVICE);  
   	 	//设置一个监听器
   	 	tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
	    
        Toast.makeText(this, "_已开启微信助手服务_", Toast.LENGTH_LONG).show();
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	Log.i("demo", "关闭");
        wakeAndUnlock(false);
        
        editText = null;
        
        //注销广播接收器
        unregisterReceiver(sreceiver);
        unregisterReceiver(preceiver);
        
        StaticData.total = 0;
        StaticData.replaied = 0;
        Toast.makeText(this, "_已关闭微信助手服务_", Toast.LENGTH_LONG).show();
    }
    
    //屏幕状态变化广播接收器，黑屏或亮屏显示锁屏界面
    class ScreenOffReceiver extends BroadcastReceiver {
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		//若在通话则不显示锁屏界面
    		if(StaticData.iscalling)
    			return;
    	    String action = intent.getAction();
    	    if (action.equals(Intent.ACTION_SCREEN_OFF)) {
    	    	Log.i("demo", "screen off");
    	        Intent lockscreen = new Intent(AutoReplyService.this, LockScreenActivity.class);
	        	lockscreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        	startActivity(lockscreen);
    	    } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
    	    	Log.i("demo", "screen on");
    	    	if(canGet)
    	    		return;
    	        Intent lockscreen = new Intent(AutoReplyService.this, LockScreenActivity.class);
	        	lockscreen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        	startActivity(lockscreen);
    	    }
    	}
    }
    
    //通话状态变化广播接收器，通话期间不弹出锁屏活动
    public class PhoneReceiver extends BroadcastReceiver {
    	 @Override
    	 public void onReceive(Context context, Intent intent) {
    		 if(intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
    			 StaticData.iscalling = true;
    			 Log.i("demo", "去电");
    		 } else {
    			 StaticData.iscalling = true;
    			 Log.i("demo", "来电");
    		 }
    	 }
	}
    //通话状态变化广播接收器，通话期间不弹出锁屏活动
	private PhoneStateListener listener = new PhoneStateListener() {
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
		 	super.onCallStateChanged(state, incomingNumber);
		 	switch(state) {
		 	case TelephonyManager.CALL_STATE_IDLE:
		 		StaticData.iscalling = false;
		 		Log.i("demo", "挂断");
		 		break;
		 	case TelephonyManager.CALL_STATE_OFFHOOK:
		 		StaticData.iscalling = true;
		 		Log.i("demo", "接听");
		 		break;
		 	case TelephonyManager.CALL_STATE_RINGING:
		 		StaticData.iscalling = true;
		 		Log.i("demo", "来电");
		 		break;
		   }
		}
	};
	private TelephonyManager tm;
}