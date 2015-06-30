package cn.fuhl.uploadfileswithqiniu.upload.rs;

import java.util.Map;

public class PutExtra {
	public Map<String, String> params; // 用户自定义参数，key必须以 "x:" 开头
	public String mimeType;
}
