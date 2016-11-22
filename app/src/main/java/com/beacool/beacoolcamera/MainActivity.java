package com.beacool.beacoolcamera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private Button btn_camera;
    private Button btn_vedio;
    private Button btn_camera_video;

    private int TARGET_ACTIVITY = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn_camera = (Button) findViewById(R.id.btn_cameara);
        btn_camera.setOnClickListener(this);
        btn_vedio = (Button) findViewById(R.id.btn_vedio);
        btn_vedio.setOnClickListener(this);
        btn_camera_video = (Button) findViewById(R.id.btn_camera_video);
        btn_camera_video.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {

        if(v.getId() == R.id.btn_cameara){
            TARGET_ACTIVITY = 0;
            if(checkPermisson()){
               startActivity(new Intent(MainActivity.this,CameraActivity.class));
            }
        }else if(v.getId() == R.id.btn_camera_video){
            TARGET_ACTIVITY = 2;
            if(checkPermisson()){
                startActivity(new Intent(MainActivity.this,CameraAndVideoActivity.class));
            }
        }else{
            TARGET_ACTIVITY = 1;
            if(checkPermisson()){
                startActivity(new Intent(MainActivity.this,VideoActivity.class));
            }
        }
    }

    private static final int REQUEST_PERMISSION_CAMERA_CODE = 1;


    private boolean checkPermisson(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!(checkSelfPermission(Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED)) {
                requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO},
                        REQUEST_PERMISSION_CAMERA_CODE);
                return false;
            }else
                return true;
        }else{
                return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_CAMERA_CODE) {
            int grantResult = grantResults[0];
            boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
            if(granted){
                if(TARGET_ACTIVITY == 0){
                    startActivity(new Intent(MainActivity.this,CameraActivity.class));
                }else if(TARGET_ACTIVITY == 1){
                    startActivity(new Intent(MainActivity.this,VideoActivity.class));
                }else{
                    startActivity(new Intent(MainActivity.this,CameraAndVideoActivity.class));
                }
            }
        }
    }
}
