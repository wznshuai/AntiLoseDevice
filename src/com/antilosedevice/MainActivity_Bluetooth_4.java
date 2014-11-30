package com.antilosedevice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.antilosedevice.service.BaseService;
import com.antilosedevice.service.ConnectService_bluetooth_4;

public class MainActivity_Bluetooth_4 extends Activity {

	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	public static final int STATE_INIT = 98;
	public static final int STATE_SEARCHING = 99;
	public static final int STATE_PAIRING = 100;
	public static final int STATE_PAIRFAIL = 101;
	public static final int STATE_CONNECTING = 102;
	public static final int STATE_SEARCH_COMPLETED = 103;

	private static final int REQUEST_OPEN_BLUETOOTH_PAIRED = 1;
	private static final int REQUEST_OPEN_BLUETOOTH_GOSCAN = 2;

	public static final String ACTION_CONNECT_LOSE = "ACTION_CONNECT_LOSE";

	public static final String DEVICE_NAME = "device_name";

	public static String EXTRA_DEVICE_ADDRESS = "device_address";

	private BluetoothAdapter mBluetoothAdapter = null;

	private BaseService mConnectService;
	private ArrayAdapter<String> mPairedDevicesArrayAdapter;
	private TextView mStatusTxt, mConnectDetail;
	private Object lock = new Object();
	private BluetoothDevice mClickDevice;
	private View mSearch;
	private ImageView mHeader;
	private Animation mRotateAnim;
	private MediaPlayer player;
	private volatile boolean isScaning = false;
	private BluetoothManager mBluetoothManager;
	private List<String> mScanedDevice;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		if (!enableBluetooth()) {
			new AlertDialog.Builder(MainActivity_Bluetooth_4.this).setMessage("蓝牙设备不可用")
					.setNeutralButton("确定", new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.cancel();
							finish();
						}
					}).show();
		} else {
			initData();
			findViews();
			initViews();
			// getPairedDevices();
			initService();
		}
		// String action = getIntent().getAction();
		//
		// if (null != action && action.equals(ACTION_CONNECT_LOSE)) {
		// setBuyStatus(ConnectService.STATE_LOSE_CONNECT);
		// }
	}

	private void setBuyStatus(int status) {
		if (status == ConnectService_bluetooth_4.STATE_CONNECTED) {
			mHeader.setImageResource(R.drawable.ic_linked);
		} else {
			mHeader.setImageResource(R.drawable.ic_unlink);
		}
		switch (status) {
		case STATE_INIT:
			if (null == mHeader.getAnimation())
				mHeader.startAnimation(mRotateAnim);
			mStatusTxt.setText(R.string.init);
			break;
		case STATE_PAIRING:
			if (null == mHeader.getAnimation())
				mHeader.startAnimation(mRotateAnim);
			mStatusTxt.setText(R.string.pairing);
			break;
		case STATE_CONNECTING:
			if (null == mHeader.getAnimation())
				mHeader.startAnimation(mRotateAnim);
			break;
		case STATE_SEARCHING:
			if (null == mHeader.getAnimation())
				mHeader.startAnimation(mRotateAnim);
			mStatusTxt.setText(R.string.scaning);
			break;
		case STATE_SEARCH_COMPLETED:
			if (mConnectService.isConnected()) {
				setBuyStatus(ConnectService_bluetooth_4.STATE_CONNECTED);
			} else {
				mHeader.clearAnimation();
				mStatusTxt.setText(R.string.not_connect);
			}
			break;
		case ConnectService_bluetooth_4.STATE_CONNECTING:
			if (null == mHeader.getAnimation())
				mHeader.startAnimation(mRotateAnim);
			mStatusTxt.setText(R.string.connecting);
			break;
		case ConnectService_bluetooth_4.STATE_CONNECTED:
			mHeader.clearAnimation();
			mStatusTxt.setText(R.string.connected);
			break;
		case ConnectService_bluetooth_4.STATE_LOSE_CONNECT:
			mHeader.clearAnimation();
			mStatusTxt.setText(R.string.lose_connect);
			systemRingtone(getApplicationContext());
			break;
		case ConnectService_bluetooth_4.STATE_CONNECT_FAIL:
			mHeader.clearAnimation();
			mStatusTxt.setText(R.string.connect_fail);
			break;
		case STATE_PAIRFAIL:
			mHeader.clearAnimation();
			mStatusTxt.setText(R.string.pairing_fail);
			break;
		default:
			mHeader.clearAnimation();
			break;
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		String action = intent.getAction();
		if (null != action && action.equals(ACTION_CONNECT_LOSE)) {
			setBuyStatus(ConnectService_bluetooth_4.STATE_LOSE_CONNECT);
		}
	}

	private void findViews() {
		mStatusTxt = (TextView) findViewById(R.id.act_main_status);
		mConnectDetail = (TextView) findViewById(R.id.act_main_connect_detail);
		mSearch = findViewById(R.id.act_main_search);
		mHeader = (ImageView) findViewById(R.id.act_main_header);
	}

	private void initViews() {
		mSearch.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
//				scanNearBluetooths();
				scanNearBluetoothsUnder4();
			}
		});
	}

	private void initData() {
		mRotateAnim = AnimationUtils.loadAnimation(getApplicationContext(),
				R.anim.anim_rotate);
		mRotateAnim.setInterpolator(new LinearInterpolator());
	}

	private void initService() {
		new Thread() {
			public void run() {
				if (null == mConnectService) {
					mConnectService = ConnectService_bluetooth_4.get(
							getApplicationContext(), mHandler);
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

				if (null != mConnectService) {
					mConnectService.isConnected();
				}
			};
		}.start();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	private void initBluetoothAdapter() {
		mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, getString(R.string.not_available),
					Toast.LENGTH_LONG).show();
		}
	}

	private boolean checkBluetoothIsOpen(int requestCode) {
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, requestCode);
			return false;
		} else {
			return true;
		}
	}

	/**
	 * 是否有蓝牙
	 * 
	 * @return
	 */
	private boolean enableBluetooth() {
		initBluetoothAdapter();
		if (null == mBluetoothAdapter)
			return false;
		return true;
	}

	// private void setStatus(int resId) {
	// mStatusTxt.setText(resId);
	// }
	//
	// private void setStatus(String str) {
	// mStatusTxt.setText(str);
	// }

	private void getPairedDevices() {
		if (checkBluetoothIsOpen(REQUEST_OPEN_BLUETOOTH_PAIRED)) {
			/**
			 * 扫描已配对的设备
			 */
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
					.getBondedDevices();
			mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this,
					R.layout.device_name);
			ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
			pairedListView.setVisibility(View.VISIBLE);
			pairedListView.setAdapter(mPairedDevicesArrayAdapter);
			pairedListView.setOnItemClickListener(mDeviceClickListener);
			if (pairedDevices.size() > 0) {
				for (BluetoothDevice device : pairedDevices) {
					mPairedDevicesArrayAdapter.add(device.getName() + "\n"
							+ device.getAddress());
				}
			}
		}
	}

	private void stopScan() {
		if (isScaning) {
			isScaning = false;
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
			setBuyStatus(STATE_SEARCH_COMPLETED);
		}
	}
	
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			// When discovery finds a device
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				// Get the BluetoothDevice object from the Intent

				if (!canGoOn())
					return;
				BluetoothDevice device = intent
						.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (null == mScanedDevice) {
					mScanedDevice = new ArrayList<String>();
				}
				if (null == mPairedDevicesArrayAdapter) {
					mPairedDevicesArrayAdapter = new ArrayAdapter<String>(
							MainActivity_Bluetooth_4.this, R.layout.device_name);
					ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
					pairedListView.setVisibility(View.VISIBLE);
					pairedListView.setAdapter(mPairedDevicesArrayAdapter);
					pairedListView
							.setOnItemClickListener(mDeviceClickListener);
				}
				if (!mScanedDevice.contains(device.getAddress())) {
					mScanedDevice.add(device.getAddress());
					mPairedDevicesArrayAdapter.add(device.getName() + "\n"
							+ device.getAddress());
					mPairedDevicesArrayAdapter.notifyDataSetChanged();
				}
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED
					.equals(action)) {
				setBuyStatus(STATE_SEARCH_COMPLETED);
			}
		}
	};
	
	private void scanNearBluetoothsUnder4() {
		if (checkBluetoothIsOpen(REQUEST_OPEN_BLUETOOTH_GOSCAN)) {
			setBuyStatus(STATE_SEARCHING);
			if (null != mBluetoothAdapter) {


				IntentFilter filter = new IntentFilter(
						BluetoothDevice.ACTION_FOUND);
				filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
				filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
				registerReceiver(mReceiver, filter);

				if (mBluetoothAdapter.isDiscovering()) {
					mBluetoothAdapter.cancelDiscovery();
				}

				mBluetoothAdapter.startDiscovery();
				
				isScaning = true;
			}
		}
	}

	private void scanNearBluetooths() {
		if (checkBluetoothIsOpen(REQUEST_OPEN_BLUETOOTH_GOSCAN)) {
			setBuyStatus(STATE_SEARCHING);
			if (null != mBluetoothAdapter) {
				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						stopScan();
					}
				}, 10000);
				mBluetoothAdapter.startLeScan(mLeScanCallback);
				isScaning = true;
			}
		}
	}

	LeScanCallback mLeScanCallback = new LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, int rssi,
				byte[] scanRecord) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (!canGoOn())
						return;
					if (null == mScanedDevice) {
						mScanedDevice = new ArrayList<String>();
					}
					if (null == mPairedDevicesArrayAdapter) {
						mPairedDevicesArrayAdapter = new ArrayAdapter<String>(
								MainActivity_Bluetooth_4.this, R.layout.device_name);
						ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
						pairedListView.setVisibility(View.VISIBLE);
						pairedListView.setAdapter(mPairedDevicesArrayAdapter);
						pairedListView
								.setOnItemClickListener(mDeviceClickListener);
					}
					if (!mScanedDevice.contains(device.getAddress())) {
						mScanedDevice.add(device.getAddress());
						mPairedDevicesArrayAdapter.add(device.getName() + "\n"
								+ device.getAddress());
						mPairedDevicesArrayAdapter.notifyDataSetChanged();
					}
				}
			});
		}
	};

	/**
	 * 播放系统响铃
	 * 
	 * @param context
	 * @return
	 * @throws Exception
	 * @throws IOException
	 */
	public MediaPlayer systemRingtone(Context context) {
		if (null == player)
			player = new MediaPlayer();
		try {
			new AlertDialog.Builder(MainActivity_Bluetooth_4.this).setMessage("蓝牙连接已断开!")
					.setCancelable(false)
					.setNeutralButton("确定", new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (null != player) {
								player.stop();
								player.reset();
							}
							mConnectService.resetState();
							dialog.cancel();
						}
					}).show();
			Uri alert = RingtoneManager
					.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
			player.setDataSource(context, alert);
			// final AudioManager audioManager = (AudioManager)
			// mContext.getSystemService(Context.AUDIO_SERVICE);
			player.setAudioStreamType(AudioManager.STREAM_RING);
			player.setLooping(true);
			player.prepare();
			player.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return player;
	}

	// The Handler that gets information back from the BluetoothChatService
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				switch (msg.arg1) {
				case ConnectService_bluetooth_4.STATE_CONNECTED:
					setBuyStatus(ConnectService_bluetooth_4.STATE_CONNECTED);
					break;
				case ConnectService_bluetooth_4.STATE_CONNECTING:
					setBuyStatus(ConnectService_bluetooth_4.STATE_CONNECTING);
					break;
				case ConnectService_bluetooth_4.STATE_LISTEN:
				case ConnectService_bluetooth_4.STATE_NONE:
					setBuyStatus(msg.arg1);
					break;
				case ConnectService_bluetooth_4.STATE_LOSE_CONNECT:
					setBuyStatus(ConnectService_bluetooth_4.STATE_LOSE_CONNECT);
					break;
				case ConnectService_bluetooth_4.STATE_CONNECT_FAIL:
					setBuyStatus(ConnectService_bluetooth_4.STATE_CONNECT_FAIL);
					break;
				case STATE_PAIRING:
					setBuyStatus(STATE_PAIRING);
					break;
				case STATE_INIT:
					setBuyStatus(STATE_INIT);
					break;
				}
				break;
			case STATE_PAIRFAIL:
				setBuyStatus(STATE_PAIRFAIL);
				new AlertDialog.Builder(MainActivity_Bluetooth_4.this)
						.setMessage("PIN或配对密钥不正确，无法与" + msg.obj + "配对")
						.setNeutralButton("确定", new OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.cancel();
							}
						}).show();
				break;
			}
		}
	};

	private void connect(BluetoothDevice device) {
		if (!mConnectService.connect(device, null)) {
			connect(device);
		}
	}

	public synchronized boolean pairMethod() {
		int connectState = mClickDevice.getBondState();
		switch (connectState) {
		// 未配对
		case BluetoothDevice.BOND_NONE:
			mHandler.obtainMessage(MESSAGE_STATE_CHANGE, STATE_PAIRING, 0,
					mClickDevice.getName()).sendToTarget();
			mClickDevice.createBond();
			break;
		// 已配对
		case BluetoothDevice.BOND_BONDED:
			// 连接
			synchronized (lock) {
				lock.notifyAll();
			}
			break;
		}
		return true;
	}

	private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> av, final View v, int arg2,
				long arg3) {
			String info = ((TextView) v).getText().toString();
			String name = info.substring(0, info.length() - 17);
			String address = info.substring(info.length() - 17);
			if (null != mConnectService
					&& null != mConnectService.getCurDevice()) {
				if (mConnectService.getState() == ConnectService_bluetooth_4.STATE_CONNECTED && mConnectService.getCurDevice().getAddress().equals(address)) {
					return;
				}
			}
			stopScan();
			mConnectDetail.setText(name);
			new Thread() {
				public void run() {
					String info = ((TextView) v).getText().toString();
					String address = info.substring(info.length() - 17);

					if (null == mConnectService) {
						mConnectService = ConnectService_bluetooth_4.get(
								getApplicationContext(), mHandler);
						try {
							Thread.sleep(200);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

					if (null != mConnectService) {
//						List<BluetoothDevice> bmList = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
//						if(null != bmList && bmList.size() > 0){
//							Iterator<BluetoothDevice> iterator = bmList.iterator();
//							while(iterator.hasNext()){
//								BluetoothDevice bd = iterator.next();
//								if(bd.getAddress().equals(address)){
//								}
//							}
//						}
						mHandler.obtainMessage(MESSAGE_STATE_CHANGE,
								STATE_INIT, 0).sendToTarget();
						mConnectService.resetState();
						mClickDevice = mBluetoothAdapter
								.getRemoteDevice(address);
						connect(mClickDevice);
					}
				};
			}.start();

		}
	};

	@Override
	protected void onDestroy() {
		// Make sure we're not doing discovery anymore
		if (mBluetoothAdapter != null && mBluetoothAdapter.isDiscovering()) {
			mBluetoothAdapter.cancelDiscovery();
		}
		
		try {
			unregisterReceiver(mReceiver);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		super.onDestroy();
	};

	private boolean canGoOn() {
		return null != this && !isFinishing() && !isDestroyed();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_OPEN_BLUETOOTH_GOSCAN:
			if (resultCode == Activity.RESULT_OK) {
				scanNearBluetoothsUnder4();;
			} else {
				Toast.makeText(this, R.string.bluetooth_disable,
						Toast.LENGTH_SHORT).show();
				finish();
			}
			break;
		case REQUEST_OPEN_BLUETOOTH_PAIRED:
			// getPairedDevices();
			break;
		}

	}
}
