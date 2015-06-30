package cn.fuhl.uploadfileswithqiniu.upload;

import org.apache.http.client.HttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import cn.fuhl.uploadfileswithqiniu.upload.config.Config;
import cn.fuhl.uploadfileswithqiniu.upload.auth.Authorizer;
import cn.fuhl.uploadfileswithqiniu.upload.rs.PutExtra;
import cn.fuhl.uploadfileswithqiniu.upload.rs.UploadResultCallRet;
import cn.fuhl.uploadfileswithqiniu.upload.slice.Block;
import cn.fuhl.uploadfileswithqiniu.upload.utils.HttpHelper;

/**
 * 一个上传文件
 *
 */
public abstract class Upload {
	private HttpClient httpClient;

	private final ReadWriteLock successLengthLock = new ReentrantReadWriteLock();

	/** 上传结束后的回调 */
	private List<Clean> cleanCallbacks;

	/** 取消标记 */
	private AtomicBoolean canceled = new AtomicBoolean(false);
	/** 已运行标记 */
	protected AtomicBoolean started = new AtomicBoolean(false);

    protected final Authorizer authorizer;
    protected final String key;
    protected final PutExtra extra;
    protected final String mimeType;
    protected final UpParam upParam;
    protected final long contentLength;
    protected final Object passParam;
    protected final UploadHandler handler;

	protected Upload(Authorizer authorizer, String key, UpParam upParam, PutExtra extra, Object passParam, UploadHandler handler) {
		this.authorizer = authorizer;
		this.key = key;
		this.extra = extra != null ? extra : new PutExtra();
		this.mimeType = extra != null ? extra.mimeType : null;
		this.upParam = upParam;
		this.contentLength = upParam.getSize();
		this.passParam = passParam;
		this.handler = handler;
	}

	public UploadResultCallRet execute() {
		boolean started2 = started.getAndSet(true);
		if (started2 && !isCanceled()) {
			return new UploadResultCallRet(Config.ERROR_CODE, new Exception(Config.PROCESS_MSG));
		}
		try {
			if (isCanceled()) {
				throw new Exception(Config.PROCESS_MSG);
			}

			UploadResultCallRet ret = doExecute();

			if (handler != null && ret.getStatusCode() != Config.CANCEL_CODE) {
				if (ret.ok()) {
					handler.sendSuccess(ret, upParam, passParam);
				} else {
					handler.sendFailure(ret, upParam, passParam);
				}
			}
			return ret;
		} catch (Exception e) {
			if (isCanceled()) {
				return new UploadResultCallRet(Config.CANCEL_CODE, e);
			} else {
				UploadResultCallRet ret = new UploadResultCallRet(Config.ERROR_CODE, e);
				if (handler != null) {
					handler.sendFailure(ret, upParam, passParam);
				}
				return ret;
			}
		} finally {
			doClean();
		}
	}

	protected abstract UploadResultCallRet doExecute() throws Exception;

	private void doClean() {
		try {
			clean();
		} catch (Exception e) {
		}
		if (cleanCallbacks != null) {
			for (Clean clean : cleanCallbacks) {
				try {
					clean.clean();
				} catch (Exception e) {
				};
			}
		}
	}

	protected void clean() throws Exception {

	}

	public void addCleanCallback(Clean clean) {
		initCleanCallbacks();
		cleanCallbacks.add(clean);
	}

	private void initCleanCallbacks() {
		if (cleanCallbacks == null) {
			cleanCallbacks = new ArrayList<Clean>();
		}
	}

	public void cancel() {
		canceled.compareAndSet(false, true);
		// cancel 不主动调用clean方法。会尽快结束doExecute方法，以调用clean方法。
	}

	public boolean isCanceled() {
		return canceled.get();
	}

	public void setHttpClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	protected HttpClient getHttpClient() {
		if (httpClient != null) {
			return httpClient;
		} else {
			return HttpHelper.getHttpClient();
		}
	}

    protected long lastUploadLength = 0;
    protected long currentUploadLength = 0;
	/** 已成功上传的数据量 */
	public long getCurrentUploadLength() {
		try {
			successLengthLock.writeLock().lock();
			return currentUploadLength;
		} finally {
			successLengthLock.writeLock().unlock();
		}
	}

	public long getContentLength() {
		return contentLength;
	}

	public long getLastUploadLength() {
		return lastUploadLength;
	}

	protected void addSuccessLength(long size) {
		try {
			successLengthLock.writeLock().lock();
			currentUploadLength += size;
		} finally {
			successLengthLock.writeLock().unlock();
		}
		if (handler != null) {
			handler.sendProcess(contentLength, currentUploadLength, lastUploadLength, upParam, passParam);
		}
	}

	/**
	 * 设置已上传的块记录。 此方法用于分片上传，文件直传 忽略传入的参数 查找已上传的块记录时，按已顺序查找，找到第一个后就返回。
	 * 
	 * @param blocks
	 */
	public void setLastUploadBlocks(List<Block> blocks) {

	}

	public UpParam getUpParam() {
		return upParam;
	}

	public Object getPassParam() {
		return passParam;
	}

}
