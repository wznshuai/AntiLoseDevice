package com.antilosedevice.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.antilosedevice.MainActivity_Bluetooth_Under4;
import com.antilosedevice.util.SharedPreferencesUtil;

public class ConnectService_bluetooth_Under4 extends BaseService {
	// Debugging
	private static final String TAG = "ConnectService";
	private static final boolean D = true;
	private static final int RERTY_TIME = 10 * 1000;// 5分钟（单位毫秒）

	// Unique UUID for this application
	private static final UUID MY_UUID_SECURE = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private TimerThread mTimerThread;
	private boolean isStopRertyNow = false;
	
	protected List<UUID> mUUIDList;
	
	

	@Override
	public void resetState() {
		isStopRertyNow = false;

		if (mState == STATE_CONNECTING) {
			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
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
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
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
		// Cancel any thread attempting to make a connection
		if (mState == STATE_CONNECTING) {
			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		mDevice = device;
		mUUIDList = uuidList;
		if(!mUUIDList.contains(MY_UUID_SECURE)){
			mUUIDList.add(MY_UUID_SECURE);
		}
		
		uuidList = null;
		
		System.out.println("connect uuidList size : " + mUUIDList.size());

		// Start the thread to connect with the given device
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		if (null == mTimerThread
				|| mTimerThread.getStartState() != STATE_CONNECTED)
			setState(STATE_CONNECTING);

		return true;
	}

	class TimerThread extends Thread {

		private int startState;
		private boolean flag = true;

		public int getStartState() {
			return startState;
		}

		public TimerThread(int startState) {
			this.startState = startState;
			isStopRertyNow = false;
		}

		@Override
		public void run() {
			long startTime = System.currentTimeMillis();
			while (flag) {
				if (System.currentTimeMillis() - startTime > RERTY_TIME) {
					if (mConnectThread != null) {
						mConnectThread.cancel();
						mConnectThread.interrupt();
						mConnectThread = null;
					}

					// Cancel any thread currently running a connection
					if (mConnectedThread != null) {
						mConnectedThread.cancel();
						mConnectedThread.interrupt();
						mConnectedThread = null;
					}

					if (startState == STATE_CONNECTED) {
//						if (null != mHandler)
							connectionLost();
//						else
//							resetState();
					} else if (startState == STATE_CONNECTING) {
						Log.d(TAG, "重连失败");
						System.out.println("重试时间到");
						setState(STATE_CONNECT_FAIL);
					}

					isStopRertyNow = true;

					mTimerThread = null;

					break;
				} else {
					try {
						sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}

		public void cancel() {
			flag = false;
			isStopRertyNow = true;
			mTimerThread = null;
		}
	}

	private void retryConnect() {
		if (!isStopRertyNow) {// 如果判断为立即停止则不继续重连
			if (mTimerThread == null) {
				Log.d(TAG, "重试");
				mTimerThread = new TimerThread(mState);
				mTimerThread.start();
			}
			connect(mDevice, mUUIDList);
		} else {
			System.out.println("连接失败 停止重试");
		}
	}

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 * 
	 * @param socket
	 *            The BluetoothSocket on which the connection was made
	 * @param device
	 *            The BluetoothDevice that has been connected
	 */
	public synchronized void connected(BluetoothSocket socket,
			BluetoothDevice device) {
		if (D)
			Log.d(TAG, "connected");

		// Cancel the thread that completed the connection
		// if (mConnectThread != null) {
		// mConnectThread.cancel();
		// mConnectThread = null;
		// }

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Start the thread to manage the connection and perform transmissions
		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();

		setState(STATE_CONNECTED);
	}

	/**
	 * Stop all threads
	 */
	public synchronized void stop() {
		if (D)
			Log.d(TAG, "stop");

		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		setState(STATE_NONE);
	}

	/**
	 * Write to the ConnectedThread in an unsynchronized manner
	 * 
	 * @param out
	 *            The bytes to write
	 * @see ConnectedThread#write(byte[])
	 */
	public void write(byte[] out) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (mState != STATE_CONNECTED)
				return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(out);
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
//	@Override
//	public void connectionLost() {
//		mState = STATE_LOSE_CONNECT;
//		Intent intent = new Intent(
//				MainActivity_Bluetooth_Under4.ACTION_CONNECT_LOSE);
//		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
//		intent.setClass(getBaseContext(),
//				MainActivity_Bluetooth_Under4.class);
//		getApplicationContext().startActivity(intent);
//	}

	/**
	 * This thread runs while attempting to make an outgoing connection with a
	 * device. It runs straight through; the connection either succeeds or
	 * fails.
	 */
	private class ConnectThread extends Thread {
		private BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;
		boolean isSuccess = false;
		boolean isStopNow = false;

		public ConnectThread(BluetoothDevice device) {
			mmDevice = device;

			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try {
				// tmp = device
				// .createInsecureRfcommSocketToServiceRecord(MY_UUID_SECURE);

				// Method m =
				// mmDevice.getClass().getMethod("createRfcommSocket",
				// new Class[] { int.class });
				//
				// mmSocket = (BluetoothSocket) m.invoke(mmDevice, 1);
				// createRfcommSocketToServiceRecord(MY_UUID_SECURE);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
		public void run() {
			try {
				System.out.println("连接线程开始");
				setName("ConnectThread");
				for (UUID uuid : mUUIDList) {
					System.out.println("连接 uuid ：" + uuid.toString() + " -- isStop : " + isStopNow);
					if (isInterrupted()||isStopNow||isStopRertyNow) {
						System.out.println("停止循环");
						break;
					}
					try {
						mmSocket = mmDevice
								.createRfcommSocketToServiceRecord(uuid);
						if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
							if (null != mmSocket && !mmSocket.isConnected())
								mmSocket.connect();
						} else {
							if (null != mmSocket)
								mmSocket.connect();
						}
						isSuccess = true;
					} catch (Exception e) {
						// Close the socket
						Log.e(TAG, "连接失败--uuid : " + uuid.toString(), e);
						isSuccess = false;
					}

					// Reset the ConnectThread because we're done
					synchronized (this) {
						mConnectThread = null;
					}

					// Start the connected thread
					if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
						isSuccess = mmSocket.isConnected();
					if (isSuccess)
						break;
				}
			} catch (Exception e) {
				Log.e(TAG, "数组被改变？", e);
			}
			if (isSuccess) {
				Log.d(TAG, "连接成功~~");
				if (null != mTimerThread) {
					mTimerThread.cancel();
				}
				new Thread() {
					public void run() {
						saveSuccessedInfo();
					};
				}.start();
				isStopRertyNow = false;
				setState(STATE_CONNECTED);
				// while(true){
				// if(!mmSocket.isConnected()){
				// System.out.println("断开连接~~~~~~~~~~~");
				// connectionLost();
				// setState(STATE_LOSE_CONNECT);
				// break;
				// }
				// SystemClock.sleep(300);
				// }

				connected(mmSocket, mmDevice);
			} else {
				System.out.println("去重试");
				retryConnect();
				isSuccess = false;
				mConnectThread = null;
				// setState(STATE_CONNECT_FAIL);

			}

		}

		public void cancel() {
			isStopNow = true;
			mConnectThread = null;
		}
	}

	private void saveSuccessedInfo() {
		SharedPreferencesUtil spu = SharedPreferencesUtil
				.getInstance(getApplicationContext());
		spu.saveList(UUIDS_KEY, mUUIDList);
		spu.saveString(DEVICE_ADDRESS_KEY, mDevice.getAddress());
	}

	/**
	 * This thread runs during a connection with a remote device. It handles all
	 * incoming and outgoing transmissions.
	 */
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			Log.d(TAG, "create ConnectedThread");
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the BluetoothSocket input and output streams
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
				Log.e(TAG, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectedThread");
			byte[] buffer = new byte[1024];
			// Keep listening to the InputStream while connected
			while (true) {
				try {
					// Read from the InputStream
					mmInStream.read(buffer);
					Log.d(TAG, "从蓝牙读取 : " + new String(buffer));
				} catch (IOException e) {
					Log.e(TAG, "断开连接", e);
					retryConnect();
					// if(null != mHandler)
					// connectionLost();
					// else
					// resetState();
					mConnectedThread = null;
					break;
				}
			}
		}

		/**
		 * Write to the connected OutStream.
		 * 
		 * @param buffer
		 *            The bytes to write
		 */
		public void write(byte[] buffer) {
			try {
				mmOutStream.write(buffer);

				// Share the sent message back to the UI Activity
				// mHandler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1,
				// buffer).sendToTarget();
			} catch (IOException e) {
				Log.e(TAG, "Exception during write", e);
			}
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onDestroy() {
		if(null != mDevice){
			resetState();
			mDevice = null;
		}
		super.onDestroy();
	}
}
