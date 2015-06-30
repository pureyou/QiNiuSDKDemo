package cn.fuhl.uploadfileswithqiniu.upload.config;

import com.qiniu.api.auth.AuthException;
import com.qiniu.api.auth.digest.Mac;
import com.qiniu.api.rs.PutPolicy;

import org.json.JSONException;

/**
 * 七牛SDK配置文件
 *
 */
public final class QiNiuConfig {
	public static final String token = getToken();
	public static final String QINIU_AK = "gI0WPFMdhM1L6Yscla6RSiQkcpEq418OClSVdfhr";
	public static final String QINIU_SK = "4wkXZydBHxdgtWDVwq9PAt5uKSNCxz0KJr813Cuu";
	public static final String QINIU_BUCKNAME = "taijiquan";

	public static String getToken() {

		Mac mac = new Mac(QiNiuConfig.QINIU_AK, QiNiuConfig.QINIU_SK);
		PutPolicy putPolicy = new PutPolicy(QiNiuConfig.QINIU_BUCKNAME);
		putPolicy.returnBody = "{\"name\": $(fname),\"size\": \"$(fsize)\",\"w\": \"$(imageInfo.width)\",\"h\": \"$(imageInfo.height)\",\"key\":$(etag)}";
		try {
			String uptoken = putPolicy.token(mac);
			return uptoken;
		} catch (AuthException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}


}
