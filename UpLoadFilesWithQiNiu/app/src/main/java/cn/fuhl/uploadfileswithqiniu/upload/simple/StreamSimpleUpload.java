package cn.fuhl.uploadfileswithqiniu.upload.simple;

import org.apache.http.entity.mime.content.AbstractContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cn.fuhl.uploadfileswithqiniu.upload.config.Config;
import cn.fuhl.uploadfileswithqiniu.upload.UpParam;
import cn.fuhl.uploadfileswithqiniu.upload.UploadHandler;
import cn.fuhl.uploadfileswithqiniu.upload.auth.Authorizer;
import cn.fuhl.uploadfileswithqiniu.upload.rs.PutExtra;

public class StreamSimpleUpload extends SimpleUpload {
	protected final InputStream is;

	public StreamSimpleUpload(Authorizer authorizer, String key, UpParam.StreamUpParam upParam, PutExtra extra, Object passParam,
			UploadHandler handler) {
		super(authorizer, key, upParam, extra, passParam, handler);
		this.is = upParam.getIs();
	}

	@Override
	protected AbstractContentBody buildFileBody() {
		String fileName = upParam.getName() != null ? upParam.getName() : "null";
		if (mimeType != null) {
			return new MyInputStreamBody(is, mimeType, fileName);
		} else {
			return new MyInputStreamBody(is, fileName);
		}
	}

	protected class MyInputStreamBody extends InputStreamBody {

		public MyInputStreamBody(InputStream in, String mimeType, String filename) {
			super(in, mimeType, filename);
		}

		public MyInputStreamBody(InputStream in, String filename) {
			super(in, filename);
		}

		// 指定流长度，服务器要求获得文件的长度，不能为 -1
		public long getContentLength() {
			return contentLength;
		}

		public void writeTo(final OutputStream out) throws IOException {
			if (out == null) {
				throw new IllegalArgumentException("Output stream may not be null");
			}
			try {
				byte[] tmp = new byte[Config.ONCE_WRITE_SIZE];
				int l;
				while ((l = this.getInputStream().read(tmp)) != -1) {
					out.write(tmp, 0, l);
					addSuccessLength(l); // 其它代码和父类一样，只加了这行代码
				}
				out.flush();
			} finally {
				this.getInputStream().close();
			}
		}

	}
}
