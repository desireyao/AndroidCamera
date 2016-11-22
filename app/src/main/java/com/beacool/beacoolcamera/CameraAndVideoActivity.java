package com.beacool.beacoolcamera;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
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
import android.widget.ImageView;
import android.widget.RelativeLayout;

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

public class CameraAndVideoActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {

    public static final String TAG = "TAG";
    private static int MAX_VIDEO_DURATION = 1 * 60 * 1000;  //录制视频的最大时长

    // 照片路径
    private String IMG_FILE_PATH
            = Environment.getExternalStoragePublicDirectory("DCIM").getAbsolutePath() + "/Camera";

//    // 视频缓存路径
//    private String VIDEO_TEMP_FILE_PATH = IMG_FILE_PATH + "/TEMP";

    // 视频保存路径
    private String VIDEO_FILE_PATH = IMG_FILE_PATH;

    private int CAMERA_MODE = 0;  // 拍照模式
    private int VIDEO_MODE = 1;   // 录像模式
    private int CURRENT_MODE = CAMERA_MODE;

    private CamcorderProfile profile;
    private MediaRecorder mediaRecorder;
    private Camera mCamera;
    private SurfaceHolder mHolder;

    // 默认前置或者后置相机 0:后置 1:前置
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Camera.Parameters mParameters;
    private int mCurrentZoom = 0;             // 当前的焦距大小

    // 录制视频的方向
    private int recorderRotation;

    private SurfaceView surfaceView;

    private IOrientationEventListener orienListener;
    private Lock mLock = new ReentrantLock();                          // 锁对象

    private Button btn_record;
    private Button btn_switch;
    private Button btn_takePic;
    private ImageView mFocusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera_video);

        initView();
    }

    private void initView() {
        btn_takePic = (Button) findViewById(R.id.btn_takePic);
        btn_record = (Button) findViewById(R.id.btn_record);
        btn_switch = (Button) findViewById(R.id.btn_switch);
        mFocusView = (ImageView) findViewById(R.id.id_focusView);
        btn_takePic.setOnClickListener(this);
        btn_record.setOnClickListener(this);
        btn_switch.setOnClickListener(this);

        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mHolder = surfaceView.getHolder();
        mHolder.addCallback(this);

        orienListener = new IOrientationEventListener(this);
        surfaceView.setOnTouchListener(new ZoomListener() {

            @Override
            public void zoomIn(int vaule) {
//                Log.e("TAG","zoomIn value: " + vaule + " getMaxZoom(): " + mParameters.getMaxZoom());
                if(vaule < surfaceView.getWidth() / 40)
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
                if(vaule > -surfaceView.getWidth() / 40)
                    return;

                if (mCurrentZoom > 0) {
                    mCurrentZoom--;
                    mParameters.setZoom(mCurrentZoom);
                    mCamera.setParameters(mParameters);
                }
            }

            @Override
            public void focusTouch(float x, float y) {
                pointFocus(createRect(x, y));

                showFocusView((int) x, (int) y);
            }
        });
    }

    private boolean isFinishAnim = false;

    private void showFocusView(int x, int y) {
        if (isFinishAnim)
            return;

        isFinishAnim = true;
        mFocusView.setVisibility(View.VISIBLE);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(CommonUtil.dp2px(this, 70),
                CommonUtil.dp2px(this, 70));
        layoutParams.leftMargin = x - layoutParams.width / 2;
        layoutParams.topMargin = y - layoutParams.height / 2;
        mFocusView.setLayoutParams(layoutParams);

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

    private Rect createRect(float x, float y) {
        int areaSize = 100;

        // 映射到新的坐标系
        int centerX = (int) (y / surfaceView.getHeight() * 2000 - 1000);
        int centerY = (int) (-x / surfaceView.getWidth() * 2000 + 1000);

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

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startPreview();
        orienListener.enable();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
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
     * 设置Camera参数
     */
    private void setCameraParameters() {
        if (mCamera != null) {
            mParameters = mCamera.getParameters();
            List<Camera.Size> pictureSizeList = mParameters.getSupportedPictureSizes();
            /* 从列表中选取合适的分辨率 */
            Camera.Size picSize = CameraUtil.getProperSize4Ratio(pictureSizeList, (float) surfaceView.getHeight() / surfaceView.getWidth());
            mParameters.setPictureSize(picSize.width, picSize.height);
            Log.e("TAG", "最终设置的picsize: picSize.width: " + picSize.width + " picSize.height: " + picSize.height);

            List<Camera.Size> videoSiezes = mParameters.getSupportedVideoSizes();
            int videoWidth = 0;
            int videoHeight = 0;
            if (videoSiezes != null && !videoSiezes.isEmpty()) {
//                Camera.Size videoSize = VideoUtil.getInstance().getPropVideoSize(videoSiezes,surfaceView.getWidth());
                Camera.Size videoSize = CameraUtil.getMaxSize4Width(videoSiezes, surfaceView.getWidth());
                Log.e("TAG", "获取到的：video_width===" + videoSize.width + " video_height=== " + videoSize.height);
                videoWidth = videoSize.width;
                videoHeight = videoSize.height;
            }
            List<Camera.Size> previewSizes = mParameters.getSupportedPreviewSizes();
//            Camera.Size previewSize = VideoUtil.getInstance().getPropPreviewSize(mParameters.getSupportedPreviewSizes(), videoWidth);
            Camera.Size previewSize = CameraUtil.getProperSize4Ratio(previewSizes, (float) videoWidth / videoHeight);
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

    @Override
    protected void onPause() {
        super.onPause();
        orienListener.disable();
        stopRecord();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        orienListener.disable();
        stopRecord();
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
     * 开始预览
     */
    public void startPreview() {
        if (mCamera == null) {
            mCamera = getCamera(mCameraId);
        }
        try {
            setCameraParameters();
            mCamera.setPreviewDisplay(mHolder);
            recorderRotation = CameraUtil.getRecorderRotation(mCameraId);  //获取相机预览角度,后面录制视频需要用
            CommonUtil.setCameraDisplayOrientation(this, mCameraId, mCamera);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始拍照
     */
    private void takePic() {
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] data, Camera mCamera) {
                startPreview(); //重新预览

                // 启动存储照片的线程
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mLock.lock();
                        File dir = new File(IMG_FILE_PATH);
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
                        mLock.unlock();
                    }
                }).start();
            }
        });
    }

    /**
     * 开始录制视频
     */
    protected void startRecord() {
        try {
            //视频存储路径
            String file_name = "VID_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance()) + ".mp4";

            File videoDir = new File(VIDEO_FILE_PATH);
            if (!videoDir.exists()) {
                videoDir.mkdirs();
            }
            // 视频存储的最终文件
            File file = new File(VIDEO_FILE_PATH, file_name);

            //初始化一个MediaRecorder
            if (mediaRecorder == null) {
                mediaRecorder = new MediaRecorder();
            } else {
                mediaRecorder.reset();
            }
            mCamera.unlock();
            mediaRecorder.setCamera(mCamera);
            //设置视频输出的方向 很多设备在播放的时候需要设个参数 这算是一个文件属性
            mediaRecorder.setOrientationHint(recorderRotation);

            //视频源类型
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
                Log.e(TAG, "QUALITY_HIGH");
            }

            if (profile != null) {
                profile.audioCodec = MediaRecorder.AudioEncoder.AAC;
                profile.audioChannels = 1;
                profile.audioSampleRate = 16000;

                profile.videoCodec = MediaRecorder.VideoEncoder.H264;
                mediaRecorder.setProfile(profile);
            }

            //视频尺寸
            mediaRecorder.setVideoSize(surfaceView.getHeight(), surfaceView.getWidth());

            //数值越大 视频质量越高
            mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
            // 设置视频的采样率，每秒帧数
            // mediaRecorder.setVideoFrameRate(5);
            // 设置录制视频文件的输出路径
            mediaRecorder.setOutputFile(file.getAbsolutePath());
            mediaRecorder.setMaxDuration(MAX_VIDEO_DURATION);

            // 设置捕获视频图像的预览界面
            mediaRecorder.setPreviewDisplay(surfaceView.getHolder().getSurface());
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止录像
     */
    private void stopRecord() {
        btn_record.setText("录像");
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        }
        releaseCamera();
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

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_record) {
            if ("录像".equals(btn_record.getText().toString())) {
                btn_record.setText("停止");
                startRecord(); // 开始录像
            } else {
                stopRecord();
                startPreview(); // 重新预览
            }
        } else if (id == R.id.btn_switch) {
            switchCamera();
        } else if (id == R.id.btn_takePic) {
            takePic();
        }
    }

}
