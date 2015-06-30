package cn.fuhl.uploadfileswithqiniu.upload.utils;

import android.graphics.Bitmap;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;

/**
 * UpLoadFilesWithQiNiu
 * Description:处理图片工具
 * Created by Fu.H.L on
 * Date:2015-06-29
 * Time:上午11:36
 * Copyright © 2015年 Fu.H.L All rights reserved.
 */
public class ImageUtils {

    /**
     * 缩小图片
     *
     * @param bitmap 原图
     * @return result 压缩后的图片
     */
    public static Bitmap resizeBitmap(Bitmap bitmap,int maxWidth,int maxHeight) {
        if (bitmap != null) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > maxWidth) {
                int pWidth = maxWidth;
                int pHeight = maxWidth * height / width;
                Bitmap result = Bitmap.createScaledBitmap(bitmap, pWidth, pHeight, false);
                bitmap.recycle();
                return result;
            }
            if (height > maxHeight) {
                int pHeight = maxHeight;
                int pWidth = maxHeight * width / height;
                Bitmap result = Bitmap.createScaledBitmap(bitmap, pWidth, pHeight, false);
                bitmap.recycle();
                return result;
            }
        }
        return bitmap;
    }

    /**
     * 将bitmap格式转化为jpeg格式
     *
     * @param  bitmap    需要转化的bitmap图
     * @param  filePath  转换后jpeg图路径
     * @param  quality   压缩质量
     * @return ture      表示转换成功
     */
    public static boolean saveBitmapToJpegFile(Bitmap bitmap, String filePath, int quality) {
        try {
            FileOutputStream fileOutStr = new FileOutputStream(filePath);
            BufferedOutputStream bufOutStr = new BufferedOutputStream(fileOutStr);
            ImageUtils.resizeBitmap(bitmap,720,1080).compress(Bitmap.CompressFormat.JPEG, quality, bufOutStr);
            bufOutStr.flush();
            bufOutStr.close();
        } catch (Exception exception) {
            return false;
        }
        return true;
    }


}
