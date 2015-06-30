package cn.fuhl.uploadfileswithqiniu.upload.utils;

import android.os.Build;
import android.util.Base64;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;

import java.util.zip.CRC32;

import cn.fuhl.uploadfileswithqiniu.upload.config.Config;
import cn.fuhl.uploadfileswithqiniu.upload.rs.CallRet;

public class Util {
	public static byte[] toByte(String s) {
		try {
			return s.getBytes(Config.CHARSET);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String urlsafeBase64(String data) {
		return Base64.encodeToString(toByte(data), Base64.URL_SAFE | Base64.NO_WRAP);
	}

	public static String urlsafeBase64(byte[] binaryData) {
		return Base64.encodeToString(binaryData, Base64.URL_SAFE | Base64.NO_WRAP);
	}

	public static CallRet handleResult(HttpResponse res) {
		String reqId = null;
		try {
			StatusLine status = res.getStatusLine();
			Header header = res.getFirstHeader("X-Reqid");
			if (header != null) {
				reqId = header.getValue();
			}
			int statusCode = status.getStatusCode();
			String responseBody = EntityUtils.toString(res.getEntity(), Config.CHARSET);
			return new CallRet(statusCode, reqId, responseBody);
		} catch (Exception e) {
			return new CallRet(Config.ERROR_CODE, reqId, e);
		}
	}

	public static long crc32(byte[] data) {
		CRC32 crc32 = new CRC32();
		crc32.update(data);
		return crc32.getValue();
	}

	public static HttpPost newPost(String url) {
		HttpPost postMethod = new HttpPost(url);
		postMethod.setHeader("User-Agent", getUserAgent());
		return postMethod;
	}

	private static String userAgent;
	private static String getUserAgent() {
		if (Config.USER_AGENT != null) {
			return Config.USER_AGENT;
		}
		if (userAgent == null) {
			String sdk = "QiniuAndroid/" + Config.VERSION;

			StringBuilder buffer = new StringBuilder("Linux; U; Android ");
			final String version = Build.VERSION.RELEASE;
			buffer.append(version);
			buffer.append(";");

			if ("REL".equals(Build.VERSION.CODENAME)) {
				final String model = Build.MODEL;
				final String brand = Build.BRAND;
				if (brand.length() > 0) {
					buffer.append(" ");
					buffer.append(brand);
				}

				if (model.length() > 0) {
					buffer.append(" ");
					buffer.append(model);
				}
			}
			final String id = Build.ID;
			if (id.length() > 0) {
				buffer.append(" Build/");
				buffer.append(id);
			}

			userAgent = sdk + " (" + buffer + ")";
		}

		return userAgent;
	}
}
