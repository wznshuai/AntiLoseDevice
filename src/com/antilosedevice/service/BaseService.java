package com.antilosedevice.service;

import java.util.List;
import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.antilosedevice.MainActivity;

public abstract class BaseService extends Service{
	// Debugging
	protected static final String TAG = "ConnectService";
	private static final boolean D = true;
	protected static final int RERTY_TIME = 1 * 60 * 1000;// 5分钟（单位毫秒）

	// Unique UUID for this application
	protected static final UUID MY_UUID_SECURE = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");

	public static final String UUIDS_KEY = "UUIDS_KEY";
	public static final String DEVICE_ADDRESS_KEY = "DEVICE_ADDRESS_KEY";
	// Member fields
	protected BluetoothAdapter mAdapter;
	protected Handler mHandler;
	protected int mState;
	protected BluetoothDevice mDevice;

	public BluetoothDevice getCurDevice() {
		return mDevice;
	}

	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0; // we're doing nothing
	public static final int STATE_LISTEN = 1; // now listening for incoming
												// connections
	public static final int STATE_CONNECTING = 2; // now initiating an outgoing
													// connection
	public static final int STATE_CONNECTED = 3; // now connected to a remote
													// device
	public static final int STATE_LOSE_CONNECT = 4;

	public static final int STATE_CONNECT_FAIL = 5; // now connected to a remote

	public static final int STATE_PAIR_FAIL = 6;// 匹配失败
	// device
	protected static BaseService mInstance;
	private static Object mWait = new Object();

	public static synchronized BaseService get(
			Context context, Handler handler, Class<? extends BaseService> c) {
		if (mInstance == null) {
			context.startService(new Intent(context, c));
			while (mInstance == null) {
				try {
					synchronized (mWait) {
						mWait.wait();
					}
					mInstance.mAdapter = BluetoothAdapter.getDefaultAdapter();
					mInstance.mHandler = handler;
					mInstance.mState = STATE_NONE;
				} catch (InterruptedException ignored) {
				}
			}
		} else {
			mInstance.mHandler = handler;
		}

		return mInstance;
	}

	public abstract void resetState();

	@Override
	public void onCreate() {
		super.onCreate();
		mInstance = this;
		synchronized (mWait) {
			mWait.notifyAll();
		}
	}

	public boolean isConnected() {
		setState(mState);
		if (mState == STATE_CONNECTED) {
			return true;
		}
		return false;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		return Service.START_STICKY;
	}

	/**
	 * Set the current state of the chat connection
	 * 
	 * @param state
	 *            An integer defining the current connection state
	 */
	protected synchronized void setState(int state) {
		if (D)
			Log.d(TAG, "setState() " + mState + " -> " + state);
		mState = state;

		// Give the new state to the Handler so the UI Activity can update
		if (null == mHandler)
			return;
		if (state != STATE_CONNECTED)
			mHandler.obtainMessage(
					MainActivity.MESSAGE_STATE_CHANGE, state,
					-1).sendToTarget();
		else
			mHandler.obtainMessage(
					MainActivity.MESSAGE_STATE_CHANGE, state,
					-1, "已连接至" + mDevice.getName()).sendToTarget();
	}

	/**
	 * Return the current connection state.
	 */
	public synchronized int getState() {
		return mState;
	}


	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 * 
	 * @param device
	 *            The BluetoothDevice to connect
	 * @param secure
	 *            Socket Security type - Secure (true) , Insecure (false)
	 */
	public abstract boolean connect(BluetoothDevice device,
			List<UUID> uuidList);

	
	/**
	 * Stop all threads
	 */
	abstract void stop();

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	public void connectionLost(){
		mState = STATE_LOSE_CONNECT;
		Intent intent = new Intent(
				MainActivity.ACTION_CONNECT_LOSE);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.setClass(getBaseContext(),
				MainActivity.class);
		getApplicationContext().startActivity(intent);
	}
}
