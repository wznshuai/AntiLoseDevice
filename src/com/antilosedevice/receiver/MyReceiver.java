package com.antilosedevice.receiver;

import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.antilosedevice.service.ConnectService;
import com.antilosedevice.util.SharedPreferencesUtil;

public class MyReceiver extends BroadcastReceiver{

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if(action.equals(Intent.ACTION_BOOT_COMPLETED)){
			initService(context);
		}		
	}
	
	private void initService(final Context context) {
		new Thread() {
			public void run() {
				SharedPreferencesUtil spu = SharedPreferencesUtil.getInstance(context);
				String address = spu.getString(ConnectService.DEVICE_ADDRESS_KEY);
				System.out.println("address : " + address);
				List<UUID> uuidList = spu.getList(ConnectService.UUIDS_KEY, UUID.class);
				System.out.println("uuidList : " + uuidList);
				if(!TextUtils.isEmpty(address) && null != uuidList && uuidList.size() > 0){
					ConnectService mConnectService = null;
					if (null == mConnectService) {
						mConnectService = ConnectService.get(context, null);
						try {
							Thread.sleep(200);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					if (null != mConnectService) {
						mConnectService.connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address));
					}
				}
			};
		}.start();
	}
}