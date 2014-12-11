package com.antilosedevice;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.antilosedevice.util.Utils;

public class CrashHandler implements UncaughtExceptionHandler {

	private Thread.UncaughtExceptionHandler mDefaultHandler;// 系统默认的UncaughtException处理类
	private static CrashHandler crashHandler;// CrashHandler实例
	private Context mContext;// 程序的Context对象
	private SimpleDateFormat simpleDateFormat=new SimpleDateFormat("_yyyy_MM_dd_HH_mm_ss");

	/** 保证只有一个CrashHandler实例 */
	private CrashHandler() {

	}

	/** 获取CrashHandler实例 ,单例模式 */
	public static CrashHandler getInstance() {

		if (crashHandler == null) {
			synchronized (CrashHandler.class) {
				crashHandler = new CrashHandler();
			}
		}
		return crashHandler;

	}

	/**
	 * 初始化
	 * 
	 * @param context
	 */
	public void init(Context context) {
		mContext = context;
		mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();// 获取系统默认的UncaughtException处理器
		Thread.setDefaultUncaughtExceptionHandler(this);// 设置该CrashHandler为程序的默认处理器
	}

	/**
	 * 当UncaughtException发生时会转入该重写的方法来处理
	 */
	public void uncaughtException(Thread thread, Throwable ex) {
		
		
		System.out.println("bbbbbbbbbbbbbbbbbbbbbbb");
		if (thread == null || ex == null || mDefaultHandler == null) {
			System.exit(0);
			return;
		}
		System.out.println("ccccccccccccccccccccccccc");

		File savePathFile = getLogFilePath(mContext);

		if (savePathFile == null) {
			mDefaultHandler.uncaughtException(thread, ex);
			return;
		}
		System.out.println("ddddddddddddddddddddddddd");

		String logMessage = String
				.format("CustomUncaughtExceptionHandler.uncaughtException: Thread %d Message %s",
						thread.getId(), ex.getMessage());
		PrintWriter printWriter = null;

		try {
			printWriter = new PrintWriter(new FileWriter(savePathFile, true));

			logMessage = String
					.format("%s\r\n\r\n%s\r\n\r\nThread: %d\r\n\r\nMessage:\r\n\r\n%s\r\n\r\nStack Trace:\r\n\r\n%s",
							Utils.geDeviceInfo(mContext), new Date(),
							thread.getId(), ex.getMessage(),
							Log.getStackTraceString(ex));

			printWriter.print(logMessage);
			printWriter
					.print("\n\n---------------------------------------------------------------------------\n\n");
		} catch (Throwable tr2) {
		} finally {
			if (printWriter != null) {
				printWriter.close();
			}
		}
		
		mDefaultHandler.uncaughtException(thread, ex);
	}

	public File getLogFilePath(Context ctx) {
		String sdStatus = Environment.getExternalStorageState();
		if (!sdStatus.equals(Environment.MEDIA_MOUNTED)) {
			return null;
		}
		

		String pathName =  Environment.getExternalStorageDirectory() + "/AntiLoseDevice/errorLog";
		String name = "exception_"
				+ Utils.getPhoneVersion() + "_" + simpleDateFormat.format(new Date()) +".log";

		File path = new File(pathName);
		
		if (!path.exists()) {
			path.mkdirs();
			System.out.println("pathName : " + pathName);
		}
		
//		
//		if(!f.exists()){
//			try {
//				f.createNewFile();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}

		return new File(path, name);
	}
	
//	public String readCrashLog(){
//		
//		File savePathFile = getLogFilePath(mContext);
//		
//		if(savePathFile == null)
//			return "";
//		
//		
//		
//		return FileUtils.getStringFromFile(getLogFilePath(mContext));
//	}

}
