package cn.fuhl.uploadfileswithqiniu.upload.slice;

import java.io.IOException;
import java.io.InputStream;

import cn.fuhl.uploadfileswithqiniu.upload.config.Config;
import cn.fuhl.uploadfileswithqiniu.upload.UpParam;
import cn.fuhl.uploadfileswithqiniu.upload.UploadHandler;
import cn.fuhl.uploadfileswithqiniu.upload.auth.Authorizer;
import cn.fuhl.uploadfileswithqiniu.upload.rs.PutExtra;


public class StreamSliceUpload extends SliceUpload {
	protected final InputStream is;
	private int currentBlockIdx = 0;

	public StreamSliceUpload(Authorizer authorizer, String key, UpParam.StreamUpParam upParam, PutExtra extra, Object passParam,
			UploadHandler handler) {
		super(authorizer, key, upParam, extra, passParam, handler);
		this.is = upParam.getIs();
	}

	@Override
	protected boolean hasNext() {
		try {
			// 部分流 is.available() == 0，此时通过设定的内容长度判断，
			return is != null && (is.available() > 0 || currentBlockIdx * Config.BLOCK_SIZE < contentLength);
		} catch (IOException e) {
			return false;
		}
	}

	@Override
	protected UploadBlock buildNextBlockUpload() throws IOException {
		long left = is.available();
		left = left > 0 ? left : (contentLength - currentBlockIdx * Config.BLOCK_SIZE);
		long start = currentBlockIdx * Config.BLOCK_SIZE;
		int len = (int) Math.min(Config.BLOCK_SIZE, left);

		ByteRef br = new ByteRef();
		byte[] b = new byte[len];
		is.read(b, 0, len);
		br.setBuf(b);
		StreamUploadBlock bu = new StreamUploadBlock(this, authorizer, getHttpClient(), Config.UP_HOST, currentBlockIdx, start,
				len, Config.CHUNK_SIZE, Config.FIRST_CHUNK, br);

		currentBlockIdx++;
		return bu;
	}

	@Override
	protected void clean() throws Exception {
		is.close();
	}

	protected class ByteRef {
		private byte[] buf;

		public byte[] getBuf() {
			return buf;
		}

		protected void setBuf(byte[] buf) {
			this.buf = buf;
		}

		public void clean() {
			this.buf = null;
		}

		public boolean isEmpty() {
			return buf == null;
		}
	}

}
