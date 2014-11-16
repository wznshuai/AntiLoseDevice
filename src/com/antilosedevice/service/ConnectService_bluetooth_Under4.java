package com.antilosedevice.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.antilosedevice.MainActivity_Bluetooth_Under4;
import com.antilosedevice.util.SharedPreferencesUtil;

public class ConnectService_bluetooth_Under4 extends Service {
	// Debugging
	private static final String TAG = "ConnectService";
	private static final boolean D = true;
	private static final int MAX_RETRY_COUNT = 0;

	// Name for the SDP record when creating server socket
	private static final String NAME_SECURE = "BluetoothChatSecure";

	// Unique UUID for this application
	private static final UUID MY_UUID_SECURE = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");

	public static final String UUIDS_KEY = "UUIDS_KEY";
	public static final String DEVICE_ADDRESS_KEY = "DEVICE_ADDRESS_KEY";
	// Member fields
	private BluetoothAdapter mAdapter;
	private Handler mHandler;
	private AcceptThread mSecureAcceptThread;
	private AcceptThread mInsecureAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;
	private BluetoothDevice mDevice;
	private int retryCount = MAX_RETRY_COUNT;

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
	private static ConnectService_bluetooth_Under4 mInstance;
	private static Object mWait = new Object();
	private List<UUID> mUUIDList;

	public static synchronized ConnectService_bluetooth_Under4 get(Context context,
			Handler handler) {
		if (mInstance == null) {
			context.startService(new Intent(context, ConnectService_bluetooth_Under4.class));
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

	public void resetState() {
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
	private synchronized void setState(int state) {
		if (D)
			Log.d(TAG, "setState() " + mState + " -> " + state);
		mState = state;

		// Give the new state to the Handler so the UI Activity can update
		if (null == mHandler)
			return;
		if (state != STATE_CONNECTED)
			mHandler.obtainMessage(MainActivity_Bluetooth_Under4.MESSAGE_STATE_CHANGE, state, -1)
					.sendToTarget();
		else
			mHandler.obtainMessage(MainActivity_Bluetooth_Under4.MESSAGE_STATE_CHANGE, state,
					-1, "已连接至" + mDevice.getName()).sendToTarget();
	}

	/**
	 * Return the current connection state.
	 */
	public synchronized int getState() {
		return mState;
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

		// Start the thread to listen on a BluetoothServerSocket
		if (mSecureAcceptThread == null) {
			mSecureAcceptThread = new AcceptThread(true);
			mSecureAcceptThread.start();
		}
		if (mInsecureAcceptThread == null) {
			mInsecureAcceptThread = new AcceptThread(false);
			mInsecureAcceptThread.start();
		}
	}

	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 * 
	 * @param device
	 *            The BluetoothDevice to connect
	 * @param secure
	 *            Socket Security type - Secure (true) , Insecure (false)
	 */
	public synchronized boolean connect(BluetoothDevice device,
			List<UUID> uuidList) {
		System.out.println("aaaaaaaaaaaaaaaa");
		if (null == mAdapter)
			return false;
		System.out.println("vvvvvvvvvvvvvvv");
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
		if(null != mDevice && !mDevice.getAddress().equals(device)){
			retryCount = MAX_RETRY_COUNT;
		}else if(null == mDevice){
			retryCount = MAX_RETRY_COUNT;
		}
		
		mDevice = device;
		mUUIDList = uuidList;
		mUUIDList.add(MY_UUID_SECURE);

		// Start the thread to connect with the given device
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		setState(STATE_CONNECTING);

		return true;
	}

	private boolean retryConnect() {
		if(retryCount > 0){
			Log.d(TAG, "重试" + retryCount);
			connect(mDevice, mUUIDList);
			retryCount--;
			return true;
		}else{
			return false;
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

		// Cancel the accept thread because we only want to connect to one
		// device
		if (mSecureAcceptThread != null) {
			mSecureAcceptThread.cancel();
			mSecureAcceptThread = null;
		}
		if (mInsecureAcceptThread != null) {
			mInsecureAcceptThread.cancel();
			mInsecureAcceptThread = null;
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

		if (mSecureAcceptThread != null) {
			mSecureAcceptThread.cancel();
			mSecureAcceptThread = null;
		}

		if (mInsecureAcceptThread != null) {
			mInsecureAcceptThread.cancel();
			mInsecureAcceptThread = null;
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
	private void connectionLost() {
		mState = STATE_LOSE_CONNECT;
		Intent intent = new Intent(MainActivity_Bluetooth_Under4.ACTION_CONNECT_LOSE);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		intent.setClass(getApplicationContext(), MainActivity_Bluetooth_Under4.class);
		startActivity(intent);
	}

	/**
	 * This thread runs while listening for incoming connections. It behaves
	 * like a server-side client. It runs until a connection is accepted (or
	 * until cancelled).
	 */
	private class AcceptThread extends Thread {
		// The local server socket
		private final BluetoothServerSocket mmServerSocket;
		private String mSocketType;

		public AcceptThread(boolean secure) {
			BluetoothServerSocket tmp = null;
			mSocketType = secure ? "Secure" : "Insecure";

			// Create a new listening server socket
			try {
				tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
						MY_UUID_SECURE);
			} catch (IOException e) {
				Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
			}
			mmServerSocket = tmp;
		}

		public void run() {
			if (D)
				Log.d(TAG, "Socket Type: " + mSocketType
						+ "BEGIN mAcceptThread" + this);
			setName("AcceptThread" + mSocketType);

			BluetoothSocket socket = null;

			// Listen to the server socket if we're not connected
			while (mState != STATE_CONNECTED) {
				try {
					// This is a blocking call and will only return on a
					// successful connection or an exception
					socket = mmServerSocket.accept();
				} catch (IOException e) {
					Log.e(TAG, "Socket Type: " + mSocketType
							+ "accept() failed", e);
					break;
				}

				// If a connection was accepted
				if (socket != null) {
					synchronized (this) {
						switch (mState) {
						case STATE_LISTEN:
						case STATE_CONNECTING:
							// Situation normal. Start the connected thread.
							connected(socket, socket.getRemoteDevice());
							break;
						case STATE_NONE:
						case STATE_CONNECTED:
							// Either not ready or already connected. Terminate
							// new socket.
							try {
								socket.close();
							} catch (IOException e) {
								Log.e(TAG, "Could not close unwanted socket", e);
							}
							break;
						}
					}
				}
			}
			if (D)
				Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

		}

		public void cancel() {
			if (D)
				Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
			try {
				mmServerSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "Socket Type" + mSocketType
						+ "close() of server failed", e);
			}
		}
	}

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
				Log.i(TAG, "BEGIN mConnectThread");
				setName("ConnectThread");
				for (UUID uuid : mUUIDList) {

					if (isStopNow)
						break;
					
					// Always cancel discovery because it will slow down a
					// connection
					// if(!mAdapter.cancelDiscovery()){
					// Log.d(TAG, "关闭扫描失败 , 状态为: " + mAdapter.);
					// return;
					// }

					// Make a connection to the BluetoothSocket
					try {
						mmSocket = mmDevice
								.createRfcommSocketToServiceRecord(uuid);
						// This is a blocking call and will only return on a
						// successful connection or an exception
						// mAdapter.getProfileProxy(ConnectService.this,
						// new ServiceListener() {
						//
						// @Override
						// public void onServiceDisconnected(int profile) {
						// Log.d(TAG, "连接断开~~~~");
						// }
						//
						// @Override
						// public void onServiceConnected(int profile,
						// BluetoothProfile proxy) {
						// BluetoothA2dp a2dp = (BluetoothA2dp) proxy;
						// Method m;
						// try {
						// m = a2dp.getClass().getMethod(
						// "connect",
						// BluetoothDevice.class);
						// boolean b = (Boolean) m.invoke(a2dp,
						// mmDevice);
						// System.out.println("a2dp.getConnectionState(mmDevice) : "
						// + a2dp.getConnectionState(mmDevice));
						// while(true){
						// if(a2dp.getConnectionState(mmDevice) ==
						// BluetoothProfile.STATE_CONNECTED)
						// break;
						// SystemClock.sleep(200);
						// }
						// isSuccess = true;
						// for (BluetoothDevice bd : a2dp
						// .getConnectedDevices()) {
						// Log.d(TAG, "已连接的有: " + bd.getName());
						// }
						// } catch (NoSuchMethodException e) {
						// // TODO Auto-generated catch block
						// e.printStackTrace();
						// } catch (IllegalAccessException e) {
						// // TODO Auto-generated catch block
						// e.printStackTrace();
						// } catch (IllegalArgumentException e) {
						// // TODO Auto-generated catch block
						// e.printStackTrace();
						// } catch (InvocationTargetException e) {
						// // TODO Auto-generated catch block
						// e.printStackTrace();
						// }
						//
						// }
						// }, BluetoothProfile.A2DP);
						if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH){
							if (null != mmSocket && !mmSocket.isConnected())
								mmSocket.connect();
						}else{
							if (null != mmSocket)
								mmSocket.connect();
						}
						isSuccess = true;
					} catch (Exception e) {
						// Close the socket
						Log.e(TAG, "连接失败1", e);
						isSuccess = false;
						try {
							Class<?> clazz = mmSocket.getRemoteDevice()
									.getClass();
							Class<?>[] paramTypes = new Class<?>[] { Integer.TYPE };
							Method m = clazz.getMethod("createRfcommSocket",
									paramTypes);
							Object[] params = new Object[] { Integer.valueOf(1) };
							mmSocket = (BluetoothSocket) m.invoke(
									mmSocket.getRemoteDevice(), params);
							if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH){
								if (null != mmSocket && !mmSocket.isConnected())
									mmSocket.connect();
							}else{
								if (null != mmSocket)
									mmSocket.connect();
							}
							
							isSuccess = true;
							Log.d(TAG, "lianjie成功");
							// mmSocket.close();
						} catch (Exception e2) {
							isSuccess = false;
							// try {
							// mmSocket.close();
							// } catch (IOException e1) {
							// // TODO Auto-generated catch block
							// Log.d(TAG, "unable to close() " + mSocketType
							// + " socket during connection failure", e1);
							//
							// }
							Log.e(TAG, "连接失败2", e2);

						}
						// connectionFailed();

					}

					// Reset the ConnectThread because we're done
					synchronized (this) {
						mConnectThread = null;
					}

					// Start the connected thread
					if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
						isSuccess = mmSocket.isConnected();
					if (isSuccess)
						break;
				}
			} catch (Exception e) {
				Log.e(TAG, "数组被改变？", e);
			}
			if (isSuccess) {
				Log.d(TAG, "连接成功~~");
				retryCount = MAX_RETRY_COUNT;
				new Thread() {
					public void run() {
						saveSuccessedInfo();
					};
				}.start();
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
//				if(!retryConnect()){
					setState(STATE_CONNECT_FAIL);
					isSuccess = false;
//				}
			}

		}

		public void cancel() {
			try {
				isStopNow = true;
				if (null != mmSocket)
					mmSocket.close();
			} catch (IOException e) {
				Log.e(TAG, "close() of connect " + " socket failed", e);
			}
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
					Log.e(TAG, "disconnected", e);
//					if(!retryConnect()){
						if(null != mHandler)
							connectionLost();
						else
							resetState();
//					}
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
}
