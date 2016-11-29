package com.beacool.beacoolcamera.managers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.beacool.beacoolcamera.R;
import com.beacool.beacoolcamera.listeners.ZoomListener;

public class CameraManager implements SurfaceHolder.Callback {
    private final String TAG = "CameraManager";

    private final int MAX_VIDEO_DURATION = 60;    // 录制视频的最大时长，单位秒
    private final int START_RECORD = 100;         // 开始录制视频
    private final int STOP_RECORD = 101;          // 停止录制视频
    private final int RECORDING = 102;            // 正在录制视频

    private final int TAKE_PHOTO_SUCCESS = 103;   // 拍照成功

    private final int VIDEO_SAVE_SUCCESS = 104;   // 正在录制视频

    // 照片路径
    private String IMG_FILE_PATH
            = Environment.getExternalStoragePublicDirectory("DCIM").getAbsolutePath() + "/Camera";

    // 视频保存路径
    private String VIDEO_FILE_PATH = Environment.getExternalStoragePublicDirectory("DCIM").getAbsolutePath() + "/Video";

    // 视频缓存路径
    private String VIDEO_TEMP_FILE_PATH
            = Environment.getExternalStoragePublicDirectory("DCIM").getAbsolutePath() + "/Video/TEMP";

    private String CURRTRT_VIDEO_NAME;

    private SurfaceView mSurfaceview;
    private SurfaceHolder mHolder;
    private Camera mCamera;

    private CamcorderProfile profile;
    private MediaRecorder mediaRecorder;

    // 默认前置或者后置相机 0:后置 1:前置
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Camera.Parameters mParameters;
    private int mCurrentZoom = 0;             // 当前的发大倍数

    private long mCurrentStartRecordTime ;

    // 录制视频的方向
    private int recorderRotation;

    private IOrientationEventListener orienListener;
    private Lock mLock = new ReentrantLock();                          // 锁对象

    private Activity mActivity;

    Handler mHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int result = msg.what;
            if (result == START_RECORD) {
                mCurrentStartRecordTime = System.currentTimeMillis();
            } else if (result == STOP_RECORD) {
                new Thread(new TaskCopyFile()).start();  // 开始将缓存文件复制到指定文件夹
            } else if (result == RECORDING) {
            } else if (result == TAKE_PHOTO_SUCCESS) {
                startPreview(); //重新预览
                Toast.makeText(mActivity, "照片保存到: DCIM/Camera", Toast.LENGTH_SHORT).show();
            } else if (result == VIDEO_SAVE_SUCCESS) {
                Toast.makeText(mActivity, "视频文件保存成功", Toast.LENGTH_SHORT).show();
            }
        }
    };

    public CameraManager(Activity activity, final SurfaceView surfaceView) {
        mSurfaceview = surfaceView;
        mActivity = activity;
        orienListener = new IOrientationEventListener(mActivity);
        mHolder = mSurfaceview.getHolder();
        mHolder.addCallback(this);

        mSurfaceview.setOnTouchListener(new ZoomListener() {
            @Override
            public void zoomIn(int vaule) {
//                Log.e("TAG","zoomIn value: " + vaule + " getMaxZoom(): " + mParameters.getMaxZoom());
                if (vaule < mSurfaceview.getWidth() / (mParameters.getMaxZoom() * 5))
                    return;

                if (mCurrentZoom < mParameters.getMaxZoom()) {
                    mCurrentZoom++;
                    mParameters.setZoom(mCurrentZoom);
                    mCamera.setParameters(mParameters);
                }
            }

            @Override
            public void zoomOut(int vaule) {
//                Log.e("TAG","zoomOut value: " + vaule);
                if (vaule > -mSurfaceview.getWidth() / (mParameters.getMaxZoom() * 5))
                    return;

                if (mCurrentZoom > 0) {
                    mCurrentZoom--;
                    mParameters.setZoom(mCurrentZoom);
                    mCamera.setParameters(mParameters);
                }
            }

            @Override
            public void focusTouch(float x, float y) {
                pointFocus(calculateRect(x, y));
                showFocusView((int) x, (int) y);
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "surfaceCreated");
        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e("TAG", "surfaceDestroyed");
        stop();
        deleteAllFilesOfDir(new File(VIDEO_TEMP_FILE_PATH));
    }

    /**
     * 获取Camera实例
     *
     * @return
     */
    private Camera getCamera(int id) {
        Camera camera = null;
        try {
            camera = Camera.open(id);
        } catch (Exception e) {
        }
        return camera;
    }

    /**
     * 切换相机
     */
    public void switchCamera() {
        releaseCamera();
        mCameraId = (mCameraId + 1) % mCamera.getNumberOfCameras();
        mCamera = getCamera(mCameraId);
        if (mHolder != null) {
            startPreview();
        }
    }

    /**
     * 开始预览
     */
    public void startPreview() {
        if (mCamera == null) {
            mCamera = getCamera(mCameraId);
        }

        if(orienListener != null){
           orienListener.enable();
        }

        try {
            setCameraParameters();
            mCamera.setPreviewDisplay(mHolder);
            recorderRotation = getRecorderRotation(mCameraId);  //获取相机预览角度,后面录制视频需要用
            setCameraDisplayOrientation(mActivity, mCameraId, mCamera);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 可能是意外停止相机 释放资源
     */
    public void stop() {
        if (mediaRecorder != null) {
            mediaRecorder.setOnErrorListener(null);
            mediaRecorder.setOnInfoListener(null);
            mediaRecorder.setPreviewDisplay(null);

            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            mHandler.sendEmptyMessage(STOP_RECORD);
        }

        if (orienListener != null) {
            orienListener.disable();
        }
        releaseCamera();
    }

    public interface RecordResultCallBack {
        void onSuccess();
        void onError();
    }

    /**
     * 停止相机 释放资源
     */
    public void stopRecord(RecordResultCallBack resultCallBack) {
        // 大于1s的为有效视频
        if (System.currentTimeMillis() - mCurrentStartRecordTime > 1000) {
            resultCallBack.onSuccess();
            stop();

            startPreview();
        }else{
            resultCallBack.onError();
        }
    }


    /**
     * 释放相机资源
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 设置Camera参数
     */
    private void setCameraParameters() {
        if (mCamera != null) {
            mParameters = mCamera.getParameters();
            List<Camera.Size> pictureSizeList = mParameters.getSupportedPictureSizes();
            /* 从列表中选取合适的分辨率 */
            Camera.Size picSize = getProperSize4Ratio(pictureSizeList, (float) mSurfaceview.getHeight() / mSurfaceview.getWidth());

            mParameters.setPictureSize(picSize.width, picSize.height);
            Log.e("TAG", "最终设置的picsize: picSize.width: " + picSize.width + " picSize.height: " + picSize.height);

            List<Camera.Size> videoSiezes = mParameters.getSupportedVideoSizes();
            int videoWidth = 0;
            int videoHeight = 0;
            if (videoSiezes != null && !videoSiezes.isEmpty()) {
//                Camera.Size videoSize = VideoUtil.getInstance().getPropVideoSize(videoSiezes,surfaceView.getWidth());
                Camera.Size videoSize = getMaxSize4Width(videoSiezes, mSurfaceview.getWidth());
                Log.e("TAG", "获取到的：video_width===" + videoSize.width + " video_height=== " + videoSize.height);
                videoWidth = videoSize.width;
                videoHeight = videoSize.height;
            }
            List<Camera.Size> previewSizes = mParameters.getSupportedPreviewSizes();
//            Camera.Size previewSize = VideoUtil.getInstance().getPropPreviewSize(mParameters.getSupportedPreviewSizes(), videoWidth);
            Camera.Size previewSize = getProperSize4Ratio(previewSizes, (float) videoWidth / videoHeight);
            mParameters.setPreviewSize(previewSize.width, previewSize.height);
            Log.e(TAG, "最终设置的预览尺寸,previewSize.width: " + previewSize.width + " previewSize.height: " + previewSize.height);

            List<String> focusModes = mParameters.getSupportedFocusModes();
            if (focusModes != null && focusModes.size() > 0) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);  //设置自动对焦
                }
            }
            mCamera.setParameters(mParameters);
        }
    }

    private boolean isTakingPic = false;

    /**
     * 开始拍照
     */
    public void takePhoto() {
        if (isTakingPic)
            return;
        isTakingPic = true;

        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] data, Camera mCamera) {
                // 启动存储照片的线程
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mLock.lock();
                        final File dir = new File(IMG_FILE_PATH);
                        if (!dir.exists()) {
                            dir.mkdirs();      // 创建文件夹
                        }
                        String name = "IMG_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance()) + ".jpg";
                        final File file = new File(dir, name);
                        FileOutputStream outputStream;
                        try {
                            outputStream = new FileOutputStream(file);
                            outputStream.write(data);                  // 写入sd卡中
                            outputStream.close();                      // 关闭输出流
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        scanFile(file.getAbsolutePath());
                        mHandler.sendEmptyMessage(TAKE_PHOTO_SUCCESS);
                        isTakingPic = false;
                        mLock.unlock();
                    }
                }).start();
            }
        });
    }

    /**
     * 扫描文件
     * @param path
     */
    private void scanFile(String path){
        MediaScannerConnection.scanFile(mActivity,new String[] {path},
                null, new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Log.e("TAG","onScanCompleted");
                    }
                });
    }

    /**
     * 开始录制视频
     */
    public void startRecord() {
        try {
            // 视频存储的缓存路径
            CURRTRT_VIDEO_NAME = "VID_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance()) + ".mp4";
            File videoTempDir = new File(VIDEO_TEMP_FILE_PATH);
            if (!videoTempDir.exists()) {
                videoTempDir.mkdirs();
            }
            File tempFile = new File(VIDEO_TEMP_FILE_PATH, CURRTRT_VIDEO_NAME);

            // 视频存储的最终文件
            File videoDir = new File(VIDEO_FILE_PATH);
            if (!videoDir.exists()) {
                videoDir.mkdirs();
            }

            //初始化一个MediaRecorder
            if (mediaRecorder == null) {
                mediaRecorder = new MediaRecorder();
            } else {
                mediaRecorder.reset();
            }
            mCamera.unlock();
            mediaRecorder.setCamera(mCamera);

            // 设置视频输出的方向 很多设备在播放的时候需要设个参数 这算是一个文件属性
            mediaRecorder.setOrientationHint(recorderRotation);

            // 视频源类型
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setAudioChannels(2);

            // 设置视频图像的录入源
            // 设置录入媒体的输出格式
//            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            // 设置音频的编码格式
//            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            // 设置视频的编码格式
//            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
                Log.e(TAG, "QUALITY_720P");
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
                Log.e(TAG, "QUALITY_1080P");
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_HIGH)) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                Log.e(TAG, "QUALITY_HIGH");
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_LOW)) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
                Log.e(TAG, "QUALITY_LOW");
            }

            if (profile != null) {
                profile.audioCodec = MediaRecorder.AudioEncoder.AAC;
                profile.audioChannels = 1;
                profile.audioSampleRate = 16000;

                profile.videoCodec = MediaRecorder.VideoEncoder.H264;
                mediaRecorder.setProfile(profile);
            }

            //视频尺寸
            mediaRecorder.setVideoSize(mSurfaceview.getHeight(), mSurfaceview.getWidth());

            //数值越大 视频质量越高
            mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
            // 设置视频的采样率，每秒帧数
//          mediaRecorder.setVideoFrameRate(5);
            // 设置录制视频文件的输出路径
            mediaRecorder.setOutputFile(tempFile.getAbsolutePath());
            mediaRecorder.setMaxDuration(MAX_VIDEO_DURATION * 1000);

            // 设置捕获视频图像的预览界面
            mediaRecorder.setPreviewDisplay(mSurfaceview.getHolder().getSurface());
            mediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
                @Override
                public void onError(MediaRecorder mr, int what, int extra) {
                    // 发生错误，停止录制
                    if (mediaRecorder != null) {
                        mediaRecorder.stop();
                        mediaRecorder.release();
                        mediaRecorder = null;
                    }
                    Log.e("TAG", "onError: " + " what:" + what + " extra: " + extra);
                }
            });

            mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
                    //正在录制...
                }
            });

            // 准备、开始
            mediaRecorder.prepare();
            mediaRecorder.start();

			mHandler.sendEmptyMessage(START_RECORD);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 保证预览方向正确
     *
     * @param activity
     * @param cameraId
     * @param camera
     */
    private void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    private boolean isFinishAnim = false;
    private ImageView mFocusView;

    private void showFocusView(int x, int y) {
        if (isFinishAnim)
            return;

        isFinishAnim = true;
        mFocusView = new ImageView(mActivity);
        mFocusView = (ImageView) mActivity.findViewById(R.id.id_focusView);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                dp2px(mActivity, 70), dp2px(mActivity, 70));
        layoutParams.leftMargin = x - layoutParams.width / 2;
        layoutParams.topMargin = y - layoutParams.height / 2;
        mFocusView.setLayoutParams(layoutParams);
        mFocusView.setVisibility(View.VISIBLE);

        ObjectAnimator _x = ObjectAnimator.ofFloat(mFocusView, "scaleX", 1, 1.2f, 1);
        ObjectAnimator _y = ObjectAnimator.ofFloat(mFocusView, "scaleY", 1, 1.2f, 1);

        AnimatorSet animSet = new AnimatorSet();
        animSet.setDuration(350);
        animSet.playTogether(_x, _y);
        animSet.start();
        animSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mFocusView.setVisibility(View.GONE);
                isFinishAnim = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
    }

    /**
     * 点击对焦
     */
    private void pointFocus(Rect rect) {
        if (mParameters.getMaxNumFocusAreas() > 0) {
            List<Camera.Area> focusAreas = new ArrayList<>();
            focusAreas.add(new Camera.Area(rect, 800));
            mParameters.setFocusAreas(focusAreas);
        } else {
            Log.e("TAG", "focus areas not supported");
        }

        if (mParameters.getMaxNumMeteringAreas() > 0) {
            List<Camera.Area> meteringAreas = new ArrayList<>();
            meteringAreas.add(new Camera.Area(rect, 1000)); // set weight to 40%
            mParameters.setMeteringAreas(meteringAreas);
        } else {
            Log.e("TAG", "metering areas not supported");
        }
        mCamera.cancelAutoFocus();
        mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
        mCamera.setParameters(mParameters);
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
//                Log.e("TAG", "对焦完成 success: " + success);
                mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                camera.setParameters(mParameters);
            }
        });
    }

    /**
     * 计算聚焦局域
     *
     * @param x
     * @param y
     * @return
     */
    private Rect calculateRect(float x, float y) {
        int areaSize = 100;

        // 映射到新的坐标系
        int centerX = (int) (y / mSurfaceview.getHeight() * 2000 - 1000);
        int centerY = (int) (-x / mSurfaceview.getWidth() * 2000 + 1000);

        int left = clamp(centerX - areaSize, -1000, 1000);
        int right = clamp(centerX + areaSize, -1000, 1000);
        int top = clamp(centerY - areaSize, -1000, 1000);
        int bottom = clamp(centerY + areaSize, -1000, 1000);

        RectF rectF = new RectF(left, top, right, bottom);
        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    //--------------------------------------------------------------------------------

    /**
     * 根据比例得到合适的尺寸的最大尺寸
     */
    private Size getProperSize4Ratio(List<Size> sizeList, float displayRatio) {
        Collections.sort(sizeList, new SizeL2hComparator());
        Size result = null;
        for (Size size : sizeList) {
            float curRatio = ((float) size.width) / size.height;
            if (curRatio == displayRatio) {
                result = size;
            }
        }

        if (null == result) {
            for (Size size : sizeList) {
                float curRatio = ((float) size.width) / size.height;
                if (curRatio == 3f / 4) {
                    result = size;
                }
            }
        }
        return result;
    }

    public class IOrientationEventListener extends OrientationEventListener {
        public IOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (ORIENTATION_UNKNOWN == orientation) {
                return;
            }
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraId, info);
            orientation = (orientation + 45) / 90 * 90;
            int rotation = 0;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - orientation + 360) % 360;
            } else {
                rotation = (info.orientation + orientation) % 360;
            }
//            Log.e("TAG","orientation: " + orientation);
            if (null != mCamera) {
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setRotation(rotation);
                mCamera.setParameters(parameters);
            }
        }
    }

    /**
     *
     * @param cameraId
     * @return
     */
    private int getRecorderRotation(int cameraId) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        return info.orientation;
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 根据宽度得到最大合适的尺寸
     *
     * @param sizeList
     * @param Width
     * @return
     */
    private Size getMaxSize4Width(List<Size> sizeList, int Width) {
        // 先对传进来的size列表进行排序
        Collections.sort(sizeList, new SizeL2hComparator());
        Size result = null;
        for (Size size : sizeList) {
            if (size.height == Width) {
                result = size;
            }
        }
        return result;
    }

    /**
     * 获取支持的最大尺寸
     */
    private Size getMaxSize(List<Size> sizeList) {
        // 先对传进来的size列表进行排序
        Collections.sort(sizeList, new SizeL2hComparator());
        Size result = null;
        if (sizeList != null && !sizeList.isEmpty()) {
            result = sizeList.get(sizeList.size() - 1);
        }
        return result;
    }

    /**
     * 从小到大排序
     */
    class SizeL2hComparator implements Comparator<Size> {
        @Override
        public int compare(Size size1, Size size2) {
            if (size1.width < size2.width) {
                return -1;
            } else if (size1.width > size2.width) {
                return 1;
            }
            return 0;
        }
    }

    /**
     * dp转px
     *
     * @param context 上下文
     * @param dpValue dp值
     * @return px值
     */
    private int dp2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    /**
     * 复制单个文件
     *
     * @param oldPath String 原文件路径 如：c:/fqf.txt
     * @param newPath String 复制后路径 如：f:/fqf.txt
     * @return boolean
     */
    private void copyFile(String oldPath, String newPath) {
        try {
            int bytesum = 0;
            int byteread = 0;
            File oldfile = new File(oldPath);
            if (oldfile.exists()) {                                    // 文件存在时
                InputStream inStream = new FileInputStream(oldPath);   // 读入原文件
                FileOutputStream fs = new FileOutputStream(newPath);
                byte[] buffer = new byte[1444];
                int length;
                while ((byteread = inStream.read(buffer)) != -1) {
                    bytesum += byteread;                               // 字节数 文件大小
                    System.out.println(bytesum);
                    fs.write(buffer, 0, byteread);
                }
                inStream.close();
            }
        } catch (Exception e) {
            Log.e("TAG", "复制文件出错");
            e.printStackTrace();
        }
    }

    /**
     * @param path
     */
    private void deleteAllFilesOfDir(File path) {
        if (!path.exists())
            return;
        if (path.isFile()) {
            path.delete();
            return;
        }
        File[] files = path.listFiles();
        for (int i = 0; i < files.length; i++) {
            deleteAllFilesOfDir(files[i]);
        }
        path.delete();
    }

    class TaskCopyFile implements Runnable {
        @Override
        public void run() {
            File tempfile = new File(VIDEO_TEMP_FILE_PATH, CURRTRT_VIDEO_NAME);
            if (tempfile.exists()) {
                if (tempfile.length() > 1024) {
                    File file = new File(VIDEO_FILE_PATH, CURRTRT_VIDEO_NAME);
                    copyFile(tempfile.getAbsolutePath(), file.getAbsolutePath());
                    scanFile(file.getAbsolutePath());
                    mHandler.sendEmptyMessage(VIDEO_SAVE_SUCCESS);
                }
                tempfile.delete();
            }
        }
    }
}
