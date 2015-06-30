package cn.fuhl.uploadfileswithqiniu.upload.utils;

import android.graphics.Bitmap;
import android.os.Environment;

import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UpProgressHandler;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.android.storage.UploadOptions;

import org.json.JSONObject;

import java.util.Random;

import cn.fuhl.uploadfileswithqiniu.upload.config.QiNiuConfig;

public class QiniuUploadUitls {

	private static final String fileName = "temp.jpg";
	private static final String tempJpeg = Environment.getExternalStorageDirectory().getPath() + "/" + fileName;

	public interface QiniuUploadUitlsListener {
		public void onSucess(String fileUrl);
		public void onError(int errorCode, String msg);
		public void onProgress(int progress);
	}

	private QiniuUploadUitls() {

	}

	private static QiniuUploadUitls qiniuUploadUitls = null;

	public static QiniuUploadUitls getInstance() {
		if (qiniuUploadUitls == null) {
			qiniuUploadUitls = new QiniuUploadUitls();
		}
		return qiniuUploadUitls;
	}


	public void uploadImage(Bitmap bitmap, QiniuUploadUitlsListener listener) {
		ImageUtils.saveBitmapToJpegFile(bitmap, tempJpeg,75);
		uploadImage(tempJpeg, listener);
	}

    private UploadManager uploadManager = new UploadManager();
	public void uploadImage(String filePath, final QiniuUploadUitlsListener listener) {
		final String fileUrlUUID = getFileUrlUUID();
		String token = QiNiuConfig.getToken();
		if (token == null) {
			if (listener != null) {
				listener.onError(-1, "token is null");
			}
			return;
		}
		uploadManager.put(filePath, fileUrlUUID, token, new UpCompletionHandler() {

            public void complete(String key, ResponseInfo info, JSONObject response) {
				if (info != null && info.statusCode == 200) {// 上传成功
					String fileRealUrl = getRealUrl(fileUrlUUID);
					if (listener != null) {
						listener.onSucess(fileRealUrl);
					}
				} else {
					if (listener != null) {
						listener.onError(info.statusCode, info.error);
					}
				}
			}
		}, new UploadOptions(null, null, false, new UpProgressHandler() {

            public void progress(String key, double percent) {
				if (listener != null) {
					listener.onProgress((int) (percent * 100));
				}
			}
		}, null));

	}

	/**
	 * 生成远程文件路径（全局唯一）
	 * 
	 * @return
	 */
	private String getFileUrlUUID() {
		String filePath = android.os.Build.MODEL + "_" + System.currentTimeMillis() + "_" + (new Random().nextInt(500000))
				+ "_" + (new Random().nextInt(10000));
		return filePath.replace(".", "0");
	}

	private String getRealUrl(String fileUrlUUID) {
		String filePath ="http://" + "7xjx7p.com1.z0.glb.clouddn.com/" + fileUrlUUID;
		return filePath;
	}

}
