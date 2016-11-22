package com.beacool.beacoolcamera;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.hardware.Camera.CameraInfo;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.beacool.beacoolcamera.listeners.ZoomListener;
import com.beacool.beacoolcamera.utils.CameraUtil;
import com.beacool.beacoolcamera.utils.CommonUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener,SurfaceHolder.Callback {

    private Camera mCamera;
    private Button btn_takePic;
    private Button btn_switchCamera;
    private ImageView mFocusView;

    private SurfaceView surfaceView;

    private int camera_id = 0;
    private final int TAKEPIC_SUCCESS = 0;                            // 拍照成功
    private int camera_direction = CameraInfo.CAMERA_FACING_BACK;     // 摄像头方向
    private Camera.Parameters mParameters;
    private int mCurrentZoom = 0;                                     // 当前焦距

    private Lock lock = new ReentrantLock();                          // 锁对象

    private IOrientationEventListener orienListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Log.e("TAG","CameraActivity_onCreate");
        // 显示界面
        setContentView(R.layout.activity_camera);
        orienListener = new IOrientationEventListener(this);
        initView();
    }

    private void initView() {
        surfaceView = (SurfaceView) this.findViewById(R.id.surfaceView);
        btn_takePic = (Button) findViewById(R.id.btn_takePic);
        btn_switchCamera = (Button) this.findViewById(R.id.btn_switch);

        btn_takePic.setOnClickListener(this);
        btn_switchCamera.setOnClickListener(this);
        surfaceView.getHolder().addCallback(this);

        mFocusView  = (ImageView) findViewById(R.id.id_focusView);
    }

    private ZoomListener zoomListener = new ZoomListener() {

        @Override
        public void zoomIn(int vaule) {
            if(mCurrentZoom < mParameters.getMaxZoom()){
                mCurrentZoom ++;
                mParameters.setZoom(mCurrentZoom);
                mCamera.setParameters(mParameters);
            }
        }
        @Override
        public void zoomOut(int vaule) {
            if(mCurrentZoom > 0){
                mCurrentZoom --;
                mParameters.setZoom(mCurrentZoom);
                mCamera.setParameters(mParameters);
            }
        }

        @Override
        public void focusTouch(float x,float y) {
            pointFocus(createRect(x,y));
            showFocusView((int)x,(int)y);
        }
    };

    private boolean isFinishAnim = false;
    private void showFocusView(int x,int y) {
        if(isFinishAnim)
            return;

        isFinishAnim = true;
        mFocusView.setVisibility(View.VISIBLE);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(CommonUtil.dp2px(this,70),
                CommonUtil.dp2px(this,70));
        Log.e("TAG","layoutParams.width: " + layoutParams.width);

        layoutParams.leftMargin = x - layoutParams.width / 2;
        layoutParams.topMargin =  y - layoutParams.height / 2;
        mFocusView.setLayoutParams(layoutParams);

        // 放大缩小动画会有边界溢出
        ObjectAnimator _x = ObjectAnimator.ofFloat(mFocusView, "scaleX", 1,1.0f, 1);
        ObjectAnimator _y = ObjectAnimator.ofFloat(mFocusView, "scaleY", 1,1.0f, 1);

        AnimatorSet animSet = new  AnimatorSet();
        animSet.setDuration(200);
        animSet.playTogether(_x, _y);
        animSet.start();
        animSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {}
            @Override
            public void onAnimationEnd(Animator animation) {
                mFocusView.setVisibility(View.GONE);
                isFinishAnim = false;
            }
            @Override
            public void onAnimationCancel(Animator animation) {}
            @Override
            public void onAnimationRepeat(Animator animation) {}
        });


    }

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
              meteringAreas.add(new Camera.Area(rect,1000)); // set weight to 40%
              mParameters.setMeteringAreas(meteringAreas);
        }else{
            Log.e("TAG", "metering areas not supported");
        }
        mCamera.cancelAutoFocus();
        mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);

        final String currentFocusMode = mParameters.getFocusMode();
        mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
        mCamera.setParameters(mParameters);
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                Camera.Parameters params = camera.getParameters();
                params.setFocusMode(currentFocusMode);
                camera.setParameters(params);
            }
        });

//        mCamera.autoFocus(new Camera.AutoFocusCallback() {
//            @Override
//            public void onAutoFocus(boolean success, Camera camera) {
//                Log.e("TAG","对焦 success: " + success);
//                List<Camera.Area> list = mParameters.getMeteringAreas();
//                for (Camera.Area area:list){
//                    Log.e("TAG","area: left: " + area.rect.left + " top: " + area.rect.top + "right: " + area.rect.right + " bottom:" + area.rect.bottom);
//                }
//            }
//        });
    }

    private static Rect createRect(float x, float y) {
        int areaSize = 100;
        // 映射到新的坐标系
        int centerX = (int) (y / 1920 * 2000 - 1000);
        int centerY = (int) (-x / 1080 * 2000 + 1000);

        int left = clamp(centerX - areaSize, -1000, 1000);
        int right = clamp(centerX + areaSize, -1000, 1000);
        int top = clamp(centerY - areaSize, -1000, 1000);
        int bottom = clamp(centerY + areaSize, -1000, 1000);

        RectF rectF = new RectF(left, top, right, bottom);
        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private static int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    /**
     * 设置参数
     */
    public void setCameraParameters(int width, int height) {
        Log.e("TAG","surfaceview: width: " + width + " height: " + height);
        mParameters = mCamera.getParameters();
        /* 获取摄像头支持的PictureSize列表 */
        List<Camera.Size> pictureSizeList = mParameters.getSupportedPictureSizes();
        for (Camera.Size size : pictureSizeList){
            Log.e("TAG","SupportedPictureSizes: width: " + size.width + " height: " + size.height);
        }
        /* 从列表中选取合适的分辨率 */
        Camera.Size picSize = CameraUtil.getProperSize4Ratio(pictureSizeList, (float) height / width);
        mParameters.setPictureSize(picSize.width, picSize.height);
        Log.e("TAG","最终设置 picSize.width: " + picSize.width + " picSize.height: " +  picSize.height);

        /* 获取摄像头支持的PreviewSize列表 */
        List<Camera.Size> previewSizeList = mParameters.getSupportedPreviewSizes();
        for (Camera.Size size : previewSizeList){
            Log.e("TAG","SupportedPreviewSizes: width: " + size.width + " height: " + size.height);
        }
        Camera.Size preSize = CameraUtil.getMaxSize4Width(previewSizeList, width);
        mParameters.setPreviewSize(preSize.width, preSize.height);
        Log.v("TAG", "最终设置 preSize.width: " + preSize.width + "," + " preSize.height: " + preSize.height);

        /* 根据选出的PictureSize重新设置SurfaceView大小 */
        mParameters.setJpegQuality(100); // 设置照片质量
        // 先判断是否支持，否则会报错
        if (mParameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE );
        }
        mCamera.cancelAutoFocus();
        CommonUtil.setCameraDisplayOrientation(CameraActivity.this, camera_id, mCamera);
        mCamera.setParameters(mParameters);
    }

    /**
     * 切换镜头
     */
    public void switchCamera() {
        if (camera_direction == CameraInfo.CAMERA_FACING_BACK) {
            camera_direction = CameraInfo.CAMERA_FACING_FRONT;
        } else {
            camera_direction = CameraInfo.CAMERA_FACING_BACK;
        }
        int mNumberOfCameras = Camera.getNumberOfCameras();
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < mNumberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == camera_direction) {
                camera_id = i;
            }
        }
        if (null != mCamera) {
            mCamera.stopPreview();
            mCamera.release();
        }
        mCamera = Camera.open(camera_id);
        try {
            mCamera.setPreviewDisplay(surfaceView.getHolder());
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

        setCameraParameters(surfaceView.getWidth(), surfaceView.getHeight());
    }

    /**
     * 拍照
     */
    private void takePic(){

        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] data, Camera mCamera) {
                mHandler.sendEmptyMessage(TAKEPIC_SUCCESS);
                // 启动存储照片的线程
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        lock.lock();
                        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
                        if (!dir.exists()) {
                            dir.mkdirs();      // 创建文件夹
                        }
                        String name = "IMG_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance()) + ".jpg";
                        File file = new File(dir, name);
                        FileOutputStream outputStream;
                        try {
                            outputStream = new FileOutputStream(file);
                            outputStream.write(data);                  // 写入sd卡中
                            outputStream.close();                      // 关闭输出流
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        lock.unlock();
                    }
                }).start();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mCamera) {
            mCamera.stopPreview();
            mCamera.release();
        }
        orienListener.disable();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id){
            case R.id.btn_takePic:
                takePic();
                break;
            case R.id.btn_switch:
                switchCamera();
        }
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        int mNumberOfCameras = Camera.getNumberOfCameras();
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < mNumberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                camera_id = i;
            }
        }
        mCamera = Camera.open(camera_id);
        surfaceView.setOnTouchListener(zoomListener);
        try {
            setCameraParameters(surfaceView.getWidth(), surfaceView.getHeight());
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview(); // 开始预览

            orienListener.enable();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.e("TAG","surfaceChanged");
        setCameraParameters(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e("TAG","surfaceChanged");
        if (null != mCamera) {
            mCamera.release();
            mCamera = null;
        }
        orienListener.disable();
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
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(camera_id, info);
            orientation = (orientation + 45) / 90 * 90;
            int rotation = 0;
            if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
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

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == TAKEPIC_SUCCESS) {
                Toast.makeText(getApplicationContext(), "照片存储至 DCIM/Camera文件夹", Toast.LENGTH_SHORT).show();
            }
            try {
                mCamera.setPreviewDisplay(surfaceView.getHolder());
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };
}
