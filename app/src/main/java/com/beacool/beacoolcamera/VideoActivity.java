package com.beacool.beacoolcamera;

import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.beacool.beacoolcamera.listeners.ZoomListener;
import com.beacool.beacoolcamera.utils.CommonUtil;
import com.beacool.beacoolcamera.utils.VideoUtil;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

public class VideoActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {

    private static String TAG = "VideoActivity";
    private static int MAX_VIDEO_DURATION = 1 * 60 * 1000;  //录制视频的最大时长

    private CamcorderProfile profile;
    private MediaRecorder mediaRecorder;
    private Camera mCamera;
    private SurfaceView surfaceView;
    private SurfaceHolder mHolder;

    private Button btn_control;
    private Button btn_switch;    //切换镜头

    // 默认前置或者后置相机 0:后置 1:前置
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Camera.Parameters mParameters;
    private int mCurrentZoom = 0;       //当前的大小
    // 录制视频的方向
    private int recorderRotation;


    private int video_width;
    private int video_height;
    private int screenWidth;
    private int screenHeight;

    private FrameLayout mfoucsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setContentView(R.layout.activity_video);
        screenWidth = CommonUtil.getScreenWidth(this);
        screenHeight = CommonUtil.getScreenHeight(this);
        Log.e(TAG, "screenWidth: " + screenWidth + " screenHeight: " + screenHeight);

        initView();
    }

    private void initView() {
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        btn_control = (Button) findViewById(R.id.btn_control);
        btn_switch = (Button) findViewById(R.id.btn_switch);
        btn_control.setOnClickListener(this);
        btn_switch.setOnClickListener(this);

        mHolder = surfaceView.getHolder();
        mHolder.addCallback(this);

        surfaceView.setOnTouchListener(new ZoomListener() {

            @Override
            public void zoomIn(int value) {
                if(mCurrentZoom < mParameters.getMaxZoom()){
                    mCurrentZoom++;
                    mParameters.setZoom(mCurrentZoom);
                    mCamera.setParameters(mParameters);
                }
            }
            @Override
            public void zoomOut(int value) {
                if(mCurrentZoom > 0){
                    mCurrentZoom --;
                    mParameters.setZoom(mCurrentZoom);
                    mCamera.setParameters(mParameters);
                }
            }

            @Override
            public void focusTouch(float x,float y) {

            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_control) {
            if ("录像".equals(btn_control.getText().toString())) {
                btn_control.setText("停止");
                startRecord(); // 开始录像
            } else {
                btn_control.setText("录像");
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                releaseCamera();
            }
        } else if (id == R.id.btn_switch) {
            switchCamera();
        }
    }

    protected void startRecord() {
        try {
            //视频存储路径
            String name = "VID_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance()) + ".mp4";
            File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Beacool/video");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            // 视频存储的最终文件
            File file = new File(dir, name);

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
            mediaRecorder.setVideoSize(video_width, video_height);
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
     * 预览相机
     */
    private void startPreview(Camera camera, SurfaceHolder holder) {
        try {
            setupCamera(camera);
            camera.setPreviewDisplay(holder);
            //获取相机预览角度， 后面录制视频需要用
            recorderRotation = VideoUtil.getInstance().getRecorderRotation(mCameraId);
            CommonUtil.setCameraDisplayOrientation(this, mCameraId, camera);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 切换相机
     */
    public void switchCamera() {
        releaseCamera();
        mCameraId = (mCameraId + 1) % mCamera.getNumberOfCameras();
        mCamera = getCamera(mCameraId);
        if (mHolder != null) {
            startPreview(mCamera, mHolder);
        }
    }

    /**
     * 设置
     */
    private void setupCamera(Camera camera) {
        if (camera != null) {
            mParameters = camera.getParameters();
            List<String> focusModes = mParameters.getSupportedFocusModes();
            if (focusModes != null && focusModes.size() > 0) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    //设置自动对焦
                    mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                }
            }

            List<Camera.Size> videoSiezes = null;
            if (mParameters != null) {
                videoSiezes = mParameters.getSupportedVideoSizes();  // 获取video 所有支持尺寸
            }

            if (videoSiezes != null && videoSiezes.size() > 0) {
                Camera.Size videoSize = VideoUtil.getInstance().getPropVideoSize(videoSiezes, screenWidth);
                video_width = videoSize.width;
                video_height = videoSize.height;
                Log.e("TAG", "video_width===" + video_width + " video_height===" + video_height);
            }

            //这里第三个参数为最小尺寸,取出所有支持尺寸的最小尺寸
            Camera.Size previewSize = VideoUtil.getInstance().getPropPreviewSize(mParameters.getSupportedPreviewSizes(), video_width);
            mParameters.setPreviewSize(previewSize.width, previewSize.height);
            Log.e(TAG, "最终设置的预览尺寸,previewSize.width: " + previewSize.width + " previewSize.height: " + previewSize.height);
            camera.setParameters(mParameters);

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(previewSize.height, previewSize.width);
            surfaceView.setLayoutParams(params);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mCamera == null) {
            mCamera = getCamera(mCameraId);
            if (mHolder != null && mCamera != null) {
                //开启预览
                startPreview(mCamera, mHolder);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy");
        releaseCamera();
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

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startPreview(mCamera, mHolder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mCamera.stopPreview();
        startPreview(mCamera, holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

}
