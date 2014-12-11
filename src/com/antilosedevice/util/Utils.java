package com.antilosedevice.util;

import android.content.Context;

public class Utils {
	public static String getPhoneVersion() {
		String phoneVersion = android.os.Build.MODEL;
		return phoneVersion;
	}
	
	public static String geDeviceInfo(Context ctx) {
		StringBuffer deviceId = new StringBuffer();

		deviceId.append(getPhoneVersion()).append("/");

		deviceId.append(getSystemVersion());

		return deviceId.toString();
	}
	
	public static String getSystemVersion() {
		int phoneVersion = android.os.Build.VERSION.SDK_INT;
		return "Android-" + phoneVersion;
	}
}
