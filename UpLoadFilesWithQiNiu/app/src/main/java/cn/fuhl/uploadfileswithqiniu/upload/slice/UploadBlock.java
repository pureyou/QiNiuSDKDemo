package cn.fuhl.uploadfileswithqiniu.upload.slice;


import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;

import cn.fuhl.uploadfileswithqiniu.upload.config.Config;
import cn.fuhl.uploadfileswithqiniu.upload.auth.Authorizer;
import cn.fuhl.uploadfileswithqiniu.upload.rs.ChunkUploadCallRet;
import cn.fuhl.uploadfileswithqiniu.upload.utils.Util;

public abstract class UploadBlock {
	protected final HttpClient httpClient;
	protected final String orginHost;

	// / 块编号
	protected final int blockIdx;

	// / 此块开始的位置
	protected final long offset;
	// / 此块的长度
	protected final int length;
	protected final Authorizer authorizer;

	private final int CHUNK_SIZE;
	private final int FIRST_CHUNK;

	protected final SliceUpload upload;

	private HttpPost post;

	public UploadBlock(SliceUpload upload, Authorizer authorizer, HttpClient httpClient, String host, int blockIdx, long offset,
			int len, int chunkSize, int firstChunk) {
		this.upload = upload;
		this.authorizer = authorizer;
		this.httpClient = httpClient;
		this.orginHost = host;
		this.blockIdx = blockIdx;
		this.offset = offset;
		this.length = len;
		CHUNK_SIZE = chunkSize;
		FIRST_CHUNK = firstChunk;
	}

	public ChunkUploadCallRet execute() throws Exception {
		try {
			int time = 0;
			ChunkUploadCallRet ret = null;
			do {
				ret = doExecute();
				time += 1;
			} while (time < Config.BLOCK_TRY_TIMES && (ret.getStatusCode() == 701 || needPutRetry(ret)));
			return ret;
		} finally {
			doClean();
		}
	}

	private ChunkUploadCallRet doExecute() {
		int flen = Math.min(length, FIRST_CHUNK);
		ChunkUploadCallRet ret = uploadMkblk(flen);
		if (!ret.ok()) {
			return ret;
		}
		if (length > FIRST_CHUNK) {
			int count = (length - FIRST_CHUNK + CHUNK_SIZE - 1) / CHUNK_SIZE;
			for (int i = 0; i < count; i++) {
				int start = CHUNK_SIZE * i + FIRST_CHUNK;
				int len = Math.min(length - start, CHUNK_SIZE);
				ret = uploadChunk(ret, start, len);
				if (!ret.ok()) {
					return ret;
				}
			}
		}
		return ret;
	}

	private ChunkUploadCallRet uploadMkblk(int len) {
		String url = getMkblkUrl();
		return upload(url, 0, len, 0);
	}

	private ChunkUploadCallRet uploadChunk(ChunkUploadCallRet ret, int start, int len) {
		String url = getBlkUrl(ret);
		return upload(url, start, len, 0);
	}

	private ChunkUploadCallRet upload(String url, int start, int len, int time) {
		try {
			if (upload.isCanceled()) {
				time += Config.CHUNK_TRY_TIMES;
				return new ChunkUploadCallRet(Config.CANCEL_CODE, "", Config.PROCESS_MSG);
			}
			post = Util.newPost(url);
			post.setHeader("Authorization", "UpToken " + authorizer.getUploadToken());
			post.setEntity(buildHttpEntity(start, len));
			HttpResponse response = httpClient.execute(post);
			ChunkUploadCallRet ret = new ChunkUploadCallRet(Util.handleResult(response));

			return checkAndRetryUpload(url, start, len, time, ret);
		} catch (Exception e) {
			int status = upload.isCanceled() ? Config.CANCEL_CODE : Config.ERROR_CODE;
			return new ChunkUploadCallRet(status, e);
		}
	}

	private ChunkUploadCallRet checkAndRetryUpload(String url, int start, int len, int time, ChunkUploadCallRet ret) {
		if (!ret.ok()) {
			if (time < Config.CHUNK_TRY_TIMES && needPutRetry(ret)) {
				return upload(url, start, len, time + 1);
			} else {
				return ret;
			}
		} else {
			long crc32 = buildCrc32(start, len);
			// 上传的数据 CRC32 校验错。
			if (ret.getCrc32() != crc32) {
				if (time < Config.CHUNK_TRY_TIMES) {
					return upload(url, start, len, time + 1);
				} else {
					// 406 上传的数据 CRC32 校验错。
					return new ChunkUploadCallRet(Config.ERROR_CODE, "", "local's crc32 do not match.");
				}
			} else {
				return ret;
			}
		}
	}

	// 701 上传数据块校验出错需要整个块重试，块重试单独判断。
	// 406 上传的数据 CRC32 校验错；500 服务端失败； 996 ??；
	private boolean needPutRetry(ChunkUploadCallRet ret) {
		return ret.getStatusCode() == 406 || ret.getStatusCode() == 996 || ret.getStatusCode() / 100 == 5;
	}

	private String getMkblkUrl() {
		String url = orginHost + "/mkblk/" + length;
		return url;
	}

	private String getBlkUrl(ChunkUploadCallRet ret) {
		String url = ret.getHost() + "/bput/" + ret.getCtx() + "/" + ret.getOffset();
		return url;
	}

	protected abstract HttpEntity buildHttpEntity(int start, int len);

	protected abstract long buildCrc32(int start, int len);

	private void doClean() {
		try {
			clean();
		} catch (Exception e) {
		}
	}

	protected void clean() {
		post = null;
	}

	public synchronized void cancel() {
		if (post != null) {
			try {
				post.abort();
			} catch (Exception e) {
			}
		}
	}

}
