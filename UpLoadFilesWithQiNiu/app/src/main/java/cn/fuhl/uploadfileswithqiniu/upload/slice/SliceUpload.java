package cn.fuhl.uploadfileswithqiniu.upload.slice;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.fuhl.uploadfileswithqiniu.upload.config.Config;
import cn.fuhl.uploadfileswithqiniu.upload.rs.CallRet;
import cn.fuhl.uploadfileswithqiniu.upload.UpParam;
import cn.fuhl.uploadfileswithqiniu.upload.Upload;
import cn.fuhl.uploadfileswithqiniu.upload.UploadHandler;
import cn.fuhl.uploadfileswithqiniu.upload.auth.Authorizer;
import cn.fuhl.uploadfileswithqiniu.upload.rs.ChunkUploadCallRet;
import cn.fuhl.uploadfileswithqiniu.upload.rs.PutExtra;
import cn.fuhl.uploadfileswithqiniu.upload.rs.UploadResultCallRet;
import cn.fuhl.uploadfileswithqiniu.upload.utils.Util;

public abstract class SliceUpload extends Upload {
	private List<Block> lastUploadBlocks;
	private List<Block> totalBlocks;
	// 当前持有的块上传组件，主要用于取消操作
	private UploadBlock uploadBlock;

	public SliceUpload(Authorizer authorizer, String key, UpParam param, PutExtra extra, Object passParam, UploadHandler handler) {
		super(authorizer, key, param, extra, passParam, handler);
	}

	public UploadResultCallRet doExecute() throws Exception {
		// 分片并上传
		ChunkUploadCallRet chret = sliceAndUpload();

		if (chret != null) {
			// 分片上传失败或取消
			return new UploadResultCallRet(chret);
		} else {
			// 生成文件
			CallRet ret = mkfile(totalBlocks);
			return new UploadResultCallRet(ret);
		}
	}

	private ChunkUploadCallRet sliceAndUpload() throws Exception {
		totalBlocks = new ArrayList<Block>();
		while (hasNext()) {
			ChunkUploadCallRet ret = nextSliceAndUpload();
			if (ret != null) {
				return ret;
			}
		}
		// 正常返回 null
		return null;
	}

	private ChunkUploadCallRet nextSliceAndUpload() throws IOException, Exception {
		// block 可以从历史断点记录中获取中获取，isCanceled() 验证不能取消，也不能只放在 while 判断里
		if (isCanceled()) {
			return new ChunkUploadCallRet(Config.CANCEL_CODE, "", Config.PROCESS_MSG);
		}

		uploadBlock = buildNextBlockUpload();
		int idx = uploadBlock.blockIdx;
		long len = uploadBlock.length;
		// 若已上传，从历史断点记录中获取
		Block block = getFromLastUploadedBlocks(idx);
		if (block != null) {
			totalBlocks.add(block); // 上传内容大小已在历史上传中计算
			return null;
		} else {
			// 否则，执行上传
			ChunkUploadCallRet ret = uploadBlock.execute();
			if (!ret.ok()) {
				// 上传出错，立即返回，快速失败。 uploadBlock内已进行了重试
				return ret;
			} else {
				// 成功
				String ctx = ret.getCtx();
				String host = ret.getHost();
				block = new Block(idx, ctx, len, host);
				if (handler != null) {
					handler.sendBlockSuccess(totalBlocks, block, upParam, passParam);
				}
			}
			totalBlocks.add(block);
			addSuccessLength(block.getLength());
			return null;
		}
	}

	protected abstract boolean hasNext();

	protected abstract UploadBlock buildNextBlockUpload() throws IOException;

	/**
	 * 设置已上传的块记录。执行execute方法前设置。 此方法用于分片上传，文件直传 忽略传入的参数
	 * 查找已上传的块记录时，按已顺序查找，找到第一个后就返回。
	 * 
	 * @param blocks
	 */
	// TODO 设置 lastUploadLength lastUploadBlocks started 穿透内存栅栏？？
	public void setLastUploadBlocks(List<Block> blocks) {
		if (lastUploadBlocks == null || started.get()) {
			// 已经运行了，不允许再设置
			return;
		}

		this.lastUploadBlocks = blocks;

		HashMap<Integer, Long> m = new HashMap<Integer, Long>();
		for (Block b : lastUploadBlocks) {
			m.put(b.getIdx(), b.getLength());
		}
		long total = 0;
		for (Integer i : m.keySet()) {
			total += m.get(i);
		}
		lastUploadLength = total;
	}

	protected CallRet mkfile(List<Block> rets) {
		String ctx = mkCtx(rets);
		String lastHost = rets.get(rets.size() - 1).getHost();
		return mkfile(ctx, lastHost, 0);
	}

	protected String mkCtx(List<Block> rets) {
		StringBuffer sb = new StringBuffer();
		for (Block ret : rets) {
			sb.append(",").append(ret.getCtx());
		}
		return sb.substring(1);
	}

	private CallRet mkfile(String ctx, String lastHost, int time) {
		try {
			String url = buildMkfileUrl(lastHost);
			HttpPost post = Util.newPost(url);
			post.setHeader("Authorization", "UpToken " + authorizer.getUploadToken());
			post.setEntity(new StringEntity(ctx));
			HttpResponse response = getHttpClient().execute(post);
			CallRet ret = Util.handleResult(response);
			// 406 上传的数据 CRC32 校验错。； 701 上传数据块校验出错。； 服务端失败
			if (ret.getStatusCode() / 100 == 5 && time < Math.max(Config.CHUNK_TRY_TIMES, Config.BLOCK_TRY_TIMES) + 1) {
				return mkfile(ctx, lastHost, time + 1);
			}
			return ret;
		} catch (Exception e) {
			// 网络异常等，到最后一步，重试是值得的。
			if (time < Config.BLOCK_TRY_TIMES + Config.CHUNK_TRY_TIMES + 1) {
				return mkfile(ctx, lastHost, time + 1);
			}
			throw new RuntimeException(e);
		}
	}

	private String buildMkfileUrl(String lastHost) {
		StringBuilder url = new StringBuilder(lastHost + "/mkfile/" + (currentUploadLength + lastUploadLength));
		if (null != key) {
			url.append("/key/").append(Util.urlsafeBase64(key));
		}
		if (null != mimeType && !(mimeType.trim().length() == 0)) {
			url.append("/mimeType/").append(Util.urlsafeBase64(mimeType));
		}
		if (extra.params != null) {
			for (Map.Entry<String, String> xvar : extra.params.entrySet()) {
				if (xvar.getKey().startsWith("x:")) {
					url.append("/").append(xvar.getKey()).append("/").append(Util.urlsafeBase64(xvar.getValue()));
				}
			}
		}
		return url.toString();
	}

	private Block getFromLastUploadedBlocks(int idx) {
		if (lastUploadBlocks == null) {
			return null;
		}
		for (Block block : lastUploadBlocks) {
			if (idx == block.getIdx()) {
				return block;
			}
		}
		return null;
	}

	protected void clean() throws Exception {
		super.clean();
		uploadBlock = null;
		lastUploadBlocks = null;
		totalBlocks = null;
	}

	public synchronized void cancel() {
		super.cancel();
		if (uploadBlock != null) {
			uploadBlock.cancel();
		}
	}

}
