package com.beacool.beacoolcamera.tools;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import com.beacool.beacoolcamera.App;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

	private static final String TAG = "CrashHandler";

	private static volatile CrashHandler mInstance;
	private Context mContext;

	private CrashHandler() {
	}

	public static CrashHandler getInstance() {
		if (mInstance == null) {
			synchronized (CrashHandler.class) {
				if (mInstance == null) {
					mInstance = new CrashHandler();
				}
			}
		}
		return mInstance;
	}

	public void init(Context context) {
		this.mContext = context;
		Thread.setDefaultUncaughtExceptionHandler(this);
	}

	/**
	 * 当UncaughtException发生时会转入该函数来处理
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		handleException(ex);
		SystemClock.sleep(2000);
		App.get().exit();
	}

	/**
	 * 自定义错误处理，收集错误信息
	 *
	 * @param ex
	 */
	private void handleException(Throwable ex) {
		// 打印并保存日志文件
//		LogTool.LogE(TAG, "Crash", ex);
        LogTool.LogSave(TAG,"crash",ex);

		// 使用Toast来显示异常信息
		new Thread() {
			@Override
			public void run() {
				Looper.prepare();
				Toast.makeText(mContext, "很抱歉，程序出现异常，即将退出", Toast.LENGTH_SHORT).show();
				Looper.loop();
			}
		}.start();
	}
}
