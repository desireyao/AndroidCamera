package com.beacool.beacoolcamera.listeners;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * Package com.beacool.beacoolcamera.listeners.
 * Created by yaoh on 2016/11/16.
 * Company Beacool IT Ltd.
 * <p/>
 * Description:
 */
public abstract class ZoomListener implements View.OnTouchListener {

    private int mode = 0;
    private double oldDist;
    private int moveDuration = 0;

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mode = 1;
                break;
            case MotionEvent.ACTION_UP:
//                Log.e("TAG","moveDuration: " + moveDuration);
                if(moveDuration <= 5) {
                    focusTouch(event.getX(),event.getY());
                }
                moveDuration = 0;
                mode = 0;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                mode -= 1;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                oldDist = spacing(event);
                mode += 1;
                break;
            case MotionEvent.ACTION_MOVE:
                moveDuration++;
                if (mode >= 2) {
                    double newDist = spacing(event);
                    if (newDist > oldDist + 1) {
                        int value = (int) (newDist - oldDist);
                        zoomIn(value);
                        oldDist = newDist;
                    }

                    if (newDist < oldDist - 1) {
                        int value = (int) (newDist - oldDist);
                        zoomOut(value);
                        oldDist = newDist;
                    }
                }
                break;
        }
//        Log.e("TAG","MODE: " + mode);
        return true;
    }

    /**
     * 计算双指移动的距离
     * @param event
     * @return
     */
    private double spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return Math.sqrt(x * x + y * y);
    }

    public abstract void zoomIn(int vaule);             // 放大
    public abstract void zoomOut(int vaule);            // 缩小
    public abstract void focusTouch(float x,float y);   // 定点聚焦
}
