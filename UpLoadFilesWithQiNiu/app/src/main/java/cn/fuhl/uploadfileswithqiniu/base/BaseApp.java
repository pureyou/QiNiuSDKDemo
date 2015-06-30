package cn.fuhl.uploadfileswithqiniu.base;

import android.app.Application;

/**
 * UpLoadFilesWithQiNiu
 * Description:
 * Created by Fu.H.L on
 * Date:2015-06-25
 * Time:上午12:45
 * Copyright © 2015年 Fu.H.L All rights reserved.
 */
public class BaseApp extends Application {

    private static BaseApp mBaseApplication;
    @Override
    public void onCreate() {
        super.onCreate();
        mBaseApplication = this;
    }

    public static BaseApp getApplication() {
        return mBaseApplication;
    }
}
