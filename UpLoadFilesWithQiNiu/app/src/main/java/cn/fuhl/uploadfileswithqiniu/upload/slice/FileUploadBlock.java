package cn.fuhl.uploadfileswithqiniu.upload.slice;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.entity.AbstractHttpEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.Lock;

import cn.fuhl.uploadfileswithqiniu.upload.config.Config;
import cn.fuhl.uploadfileswithqiniu.upload.auth.Authorizer;
import cn.fuhl.uploadfileswithqiniu.upload.utils.Util;

public class FileUploadBlock extends UploadBlock {
	private final RandomAccessFile file;
	private final Lock fileUploadLock;

	public FileUploadBlock(SliceUpload upload, Authorizer authorizer, HttpClient httpClient, String host, int blockIdx,
			long offset, int len, int chunkSize, int firstChunk, RandomAccessFile file, Lock fileUploadLock) {
		super(upload, authorizer, httpClient, host, blockIdx, offset, len, chunkSize, firstChunk);
		this.file = file;
		this.fileUploadLock = fileUploadLock;
	}

	@Override
	protected HttpEntity buildHttpEntity(final int start, final int len) {
		AbstractHttpEntity entity = new AbstractHttpEntity() {
			private boolean consumed = false;
			private long length = len;

			public boolean isRepeatable() {
				return true;
			}

			public long getContentLength() {
				return length;
			}

			public InputStream getContent() throws IOException, IllegalStateException {
				return null;
			}

			public void writeTo(OutputStream os) throws IOException {
				consumed = false;
				try {
					byte[] b = new byte[Config.ONCE_WRITE_SIZE];
					int len = -1;
					long off = offset + start;

					fileUploadLock.lock();
					file.seek(off);
					while (true) {
						len = file.read(b);
						if (len == -1) {
							break;
						}

						os.write(b, 0, len);
						off += len;
					}
					os.flush();
				} finally {
					fileUploadLock.unlock();
					consumed = true;
				}
			}

			public boolean isStreaming() {
				return !consumed;
			}

		};
		entity.setContentType("application/octet-stream");
		return entity;
	}

	@Override
	protected long buildCrc32(int start, int len) {
		return Util.crc32(copy2New(start, len));
	}

	private byte[] copy2New(int start, int len) {
		byte[] data = new byte[len];
		try {
			long off = offset + start;
			fileUploadLock.lock();
			file.seek(off);
			file.read(data);
			return data;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			fileUploadLock.unlock();
		}
	}

	@Override
	protected void clean() {

	}

}
