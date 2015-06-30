package cn.fuhl.uploadfileswithqiniu.upload.simple;

import org.apache.http.entity.mime.content.AbstractContentBody;
import org.apache.http.entity.mime.content.FileBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cn.fuhl.uploadfileswithqiniu.upload.config.Config;
import cn.fuhl.uploadfileswithqiniu.upload.UpParam;
import cn.fuhl.uploadfileswithqiniu.upload.UploadHandler;
import cn.fuhl.uploadfileswithqiniu.upload.auth.Authorizer;
import cn.fuhl.uploadfileswithqiniu.upload.rs.PutExtra;

public class FileSimpleUpload extends SimpleUpload {
	protected final File file;

	public FileSimpleUpload(Authorizer authorizer, String key, UpParam.FileUpParam upParam, PutExtra extra, Object passParam,
			UploadHandler handler) {
		super(authorizer, key, upParam, extra, passParam, handler);
		this.file = upParam.getFile();
	}

	@Override
	protected AbstractContentBody buildFileBody() {
		if (mimeType != null) {
			return new MyFileBody(file, mimeType);
		} else {
			return new MyFileBody(file);
		}
	}

	protected class MyFileBody extends FileBody {

		public MyFileBody(File file) {
			super(file);
		}

		public MyFileBody(File file, String mimeType) {
			super(file, mimeType);
		}

		public void writeTo(final OutputStream out) throws IOException {
			if (out == null) {
				throw new IllegalArgumentException("Output stream may not be null");
			}
			InputStream in = new FileInputStream(this.getFile());
			try {
				byte[] tmp = new byte[Config.ONCE_WRITE_SIZE];
				int l;
				while ((l = in.read(tmp)) != -1) {
					out.write(tmp, 0, l);
					addSuccessLength(l); // 其它代码和父类一样，只加了这行代码
				}
				out.flush();
			} finally {
				in.close();
			}
		}

	}
}
