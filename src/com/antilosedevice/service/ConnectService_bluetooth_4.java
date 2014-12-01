package com.antilosedevice.service;

import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.antilosedevice.MainActivity_Bluetooth_4;
import com.antilosedevice.MainActivity_Bluetooth_Under4;
import com.antilosedevice.util.SharedPreferencesUtil;

public class ConnectService_bluetooth_4 extends BaseService {
	// Debugging
	private static final String TAG = "ConnectService";
	private static final boolean D = true;

	// Member fields
	private ConnectThread mConnectThread;
	private MyBluetoothGattCallback mGattCallback;
	private BluetoothManager mBluetoothManager;
	

	public BluetoothDevice getCurDevice() {
		return mDevice;
	}

	@Override
	public void resetState() {
		if (mState == STATE_CONNECTING) {
			if (mConnectThread != null) {
				mConnectThread = null;
			}
			mGattCallback = null;
		}

		mState = STATE_NONE;
	}

	


	/**
	 * Start the chat service. Specifically start AcceptThread to begin a
	 * session in listening (server) mode. Called by the Activity onResume()
	 */
	public synchronized void start() {
		if (D)
			Log.d(TAG, "start");

		// Cancel any thread attempting to make a connection
		if (mConnectThread != null) {
			mConnectThread = null;
		}

		setState(STATE_LISTEN);
	}

	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 * 
	 * @param device
	 *            The BluetoothDevice to connect
	 * @param secure
	 *            Socket Security type - Secure (true) , Insecure (false)
	 */
	@Override
	public synchronized boolean connect(BluetoothDevice device,
			List<UUID> uuidList) {
		if (null == mAdapter)
			return false;
		if (mAdapter.isDiscovering()) {
			mAdapter.cancelDiscovery();
		}
		if (D)
			Log.d(TAG, "connect to: " + device);
		if (mState == STATE_CONNECTING) {
			if (mConnectThread != null) {
				mConnectThread = null;
			}
		}


		mDevice = device;

		// Start the thread to connect with the given device
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		setState(STATE_CONNECTING);

		return true;
	}

	private void retryConnect() {
		connect(mDevice, null);
	}


	/**
	 * Stop all threads
	 */
	public synchronized void stop() {
		if (D)
			Log.d(TAG, "stop");

		if (mConnectThread != null) {
			mConnectThread = null;
		}

		setState(STATE_NONE);
	}


	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
//	@Override
//	public void connectionLost() {
//		mState = STATE_LOSE_CONNECT;
//		Intent intent = new Intent(MainActivity_Bluetooth_Under4.ACTION_CONNECT_LOSE);
//		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//		intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
//		intent.setClass(getApplicationContext(), MainActivity_Bluetooth_Under4.class);
//		startActivity(intent);
//	}
//	
	class MyBluetoothGattCallback extends BluetoothGattCallback{

		@Override
		public void onConnectionStateChange(
				BluetoothGatt gatt, int status, int newState) {
			super.onConnectionStateChange(gatt, status,
					newState);
			
			Log.d(TAG, "onConnectionStateChange : "
					+ "gatt -- " + gatt + "||||status -- "
					+ status + "|||newState -- " + newState);
			if(newState == BluetoothProfile.STATE_CONNECTED){
				if(status == BluetoothGatt.GATT_SUCCESS){
					if(null == mBluetoothManager){
						mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
					}
					List<BluetoothDevice> bdList = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
					for(BluetoothDevice bd : bdList){
						System.out.println("已绑定的设备 : " + bd.getName() + "----" +bd.getAddress());
					}
					new Thread() {
						public void run() {
							saveSuccessedInfo();
						};
					}.start();
					gatt.discoverServices();
					setState(STATE_CONNECTED);
				}else{
					gatt.close();
					setState(STATE_CONNECT_FAIL);
				}
			}else if(/*(status == BluetoothGatt.GATT_SUCCESS || status == 1) && */newState == BluetoothProfile.STATE_DISCONNECTED){
				if(!gatt.getDevice().getAddress().equals(mDevice.getAddress()))
					return;
				System.out.println("断开的设备名称 : " + gatt.getDevice().getName());
				if (null != mHandler)
					connectionLost();
				else
					resetState();
			}else{
				setState(STATE_CONNECT_FAIL);
			}
		}

		@Override
		public void onServicesDiscovered(
				BluetoothGatt gatt, int status) {
			super.onServicesDiscovered(gatt, status);
			Log.d(TAG, "onServicesDiscovered : "
					+ "gatt -- " + gatt + "||||status -- "
					+ status);
		}

		@Override
		public void onCharacteristicRead(
				BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic,
				int status) {
			super.onCharacteristicRead(gatt,
					characteristic, status);
			Log.d(TAG, "onCharacteristicRead : "
					+ "gatt -- " + gatt + "||||status -- "
					+ status);
		}

		@Override
		public void onCharacteristicWrite(
				BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic,
				int status) {
			super.onCharacteristicWrite(gatt,
					characteristic, status);
			Log.d(TAG, "onCharacteristicWrite : "
					+ "gatt -- " + gatt + "||||status -- "
					+ status);
		}

		@Override
		public void onCharacteristicChanged(
				BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicChanged(gatt,
					characteristic);
			Log.d(TAG, "onCharacteristicChanged : "
					+ "gatt -- " + gatt + "||||characteristic -- "
					+ characteristic);
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor,
				int status) {
			super.onDescriptorRead(gatt, descriptor, status);
			Log.d(TAG, "onDescriptorRead : "
					+ "descriptor -- " + descriptor + "||||status -- "
					+ status);
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor,
				int status) {
			super.onDescriptorWrite(gatt, descriptor,
					status);
			Log.d(TAG, "onDescriptorWrite : "
					+ "descriptor -- " + descriptor + "||||status -- "
					+ status);
		}

		@Override
		public void onReliableWriteCompleted(
				BluetoothGatt gatt, int status) {
			super.onReliableWriteCompleted(gatt, status);
			Log.d(TAG, "onReliableWriteCompleted : "
					+ "gatt -- " + gatt + "||||status -- "
					+ status);
		}

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt,
				int rssi, int status) {
			super.onReadRemoteRssi(gatt, rssi, status);
			Log.d(TAG, "onReadRemoteRssi : "
					+ "gatt -- " + gatt + "||||status -- "
					+ status);
		}

	} 

	/**
	 * This thread runs while attempting to make an outgoing connection with a
	 * device. It runs straight through; the connection either succeeds or
	 * fails.
	 */
	private class ConnectThread extends Thread {
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			mmDevice = device;
		}

		public void run() {
			try {
				Log.i(TAG, "BEGIN mConnectThread");
				setName("ConnectThread");
				if(null == mGattCallback)
					mGattCallback = new MyBluetoothGattCallback();
				mmDevice.connectGatt(ConnectService_bluetooth_4.this, false, mGattCallback);
			} catch (Exception e) {
				Log.e(TAG, "连接～～～", e);
			}
		}

	}

	private void saveSuccessedInfo() {
		SharedPreferencesUtil spu = SharedPreferencesUtil
				.getInstance(getApplicationContext());
		spu.saveString(DEVICE_ADDRESS_KEY, mDevice.getAddress());
		System.out.println("保存!!~~~~");
	}


	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onDestroy() {
		if(null != mDevice){
			resetState();
			mDevice.connectGatt(ConnectService_bluetooth_4.this, false, null);
			mDevice = null;
		}
		super.onDestroy();
	}
}
