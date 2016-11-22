package com.beacool.beacoolcamera.utils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.hardware.Camera.Size;
import android.util.Log;

public class CameraUtil {

	/**
	 * 根据比例得到合适的尺寸的最大尺寸
     */
	public static Size getProperSize4Ratio(List<Size> sizeList, float displayRatio) {
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

	/**
	 * 根据宽度得到最大合适的尺寸
	 * @param sizeList
	 * @param Width
     * @return
     */
	public static Size getMaxSize4Width(List<Size> sizeList, int Width) {
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
	public static Size getMaxSize(List<Size> sizeList) {
		// 先对传进来的size列表进行排序
		Collections.sort(sizeList, new SizeL2hComparator());
		Size result = null;
		if(sizeList != null && !sizeList.isEmpty()){
			result = sizeList.get(sizeList.size() - 1);
		}
		return result;
	}

	/**
	 * 从小到大排序
	 */
	private static class SizeL2hComparator implements Comparator<Size> {
		@Override
		public int compare(Size size1, Size size2) {
			if (size1.width < size2.width) {
				return -1;
			}else if (size1.width > size2.width) {
				return 1;
			}
			return 0;
		}
	}

	public static int getRecorderRotation(int cameraId){
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cameraId, info);
		return info.orientation;
	}
}
