package cn.fuhl.uploadfileswithqiniu.upload;

import android.os.Handler;
import android.os.Message;

import java.util.List;

import cn.fuhl.uploadfileswithqiniu.upload.rs.UploadResultCallRet;
import cn.fuhl.uploadfileswithqiniu.upload.slice.Block;

public abstract class UploadHandler extends Handler {
	@Override
	public void handleMessage(Message msg) {
		Param p = (Param) msg.obj;
		if (p.type == CallBackType.Process) {
			onProcess(p.contentLength, p.currentUploadLength, p.lastUploadLength, p.upParam, p.passParam);
		} else if (p.type == CallBackType.Success) {
			onSuccess(p.ret, p.upParam, p.passParam);
		} else if (p.type == CallBackType.Failure) {
			onFailure(p.ret, p.upParam, p.passParam);
		} else if (p.type == CallBackType.BlockSuccess) {
			onBlockSuccess(p.uploadedBlocks, p.block, p.upParam, p.passParam);
		}
	}

	private void send(Param p) {
		try {
			Message msg = new Message();
			msg.obj = p;
			sendMessage(msg);
		} catch (Exception e) {

		}
	}

	public void sendProcess(long contentLength, long currentUploadLength, long lastUploadLength, UpParam upParam, Object passParam) {
		Param p = new Param(CallBackType.Process, upParam, passParam);
		p.contentLength = contentLength;
		p.currentUploadLength = currentUploadLength;
		p.lastUploadLength = lastUploadLength;
		send(p);
	}

	public void sendSuccess(UploadResultCallRet ret, UpParam upParam, Object passParam) {
		Param p = new Param(CallBackType.Success, upParam, passParam);
		p.ret = ret;
		send(p);
	}

	public void sendFailure(UploadResultCallRet ret, UpParam upParam, Object passParam) {
		Param p = new Param(CallBackType.Failure, upParam, passParam);
		p.ret = ret;
		send(p);
	}

	public void sendBlockSuccess(List<Block> uploadedBlocks, Block block, UpParam upParam, Object passParam) {
		Param p = new Param(CallBackType.BlockSuccess, upParam, passParam);
		p.block = block;
		p.uploadedBlocks = uploadedBlocks;
		send(p);
	}

	/**
	 * @param contentLength         资源总长度
	 * @param currentUploadLength   本次已上传的资源长度
	 * @param lastUploadLength      上次断点续传已上传的资源长度。非断点上传，此值为0.
	 * @param passParam             用户传递给Upload的参数
	 */
	protected abstract void onProcess(long contentLength, long currentUploadLength, long lastUploadLength, UpParam upParam,
			Object passParam);

	protected abstract void onSuccess(UploadResultCallRet ret, UpParam upParam, Object passParam);

	protected abstract void onFailure(UploadResultCallRet ret, UpParam upParam, Object passParam);

	protected void onBlockSuccess(List<Block> uploadedBlocks, Block block, UpParam upParam, Object passParam) {

	}

	private enum CallBackType {
		Process, Success, Failure, BlockSuccess, ;
	}

	private static class Param {
		final CallBackType type;
		final UpParam upParam;
		final Object passParam;
		long contentLength, currentUploadLength, lastUploadLength;
		UploadResultCallRet ret;
		List<Block> uploadedBlocks;
		Block block;

		Param(CallBackType type, UpParam upParam, Object passParam) {
			this.upParam = upParam;
			this.type = type;
			this.passParam = passParam;
		}
	}
}
