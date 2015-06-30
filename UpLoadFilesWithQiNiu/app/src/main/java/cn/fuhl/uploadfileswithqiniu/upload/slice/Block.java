package cn.fuhl.uploadfileswithqiniu.upload.slice;

public class Block {
	private final int idx;
	private final String ctx;
	private final long length;
	private final String host;

	public Block(int idx, String ctx, long length, String host) {
		this.idx = idx;
		this.ctx = ctx;
		this.length = length;
		this.host = host;
	}

	public int getIdx() {
		return idx;
	}

	public String getCtx() {
		return ctx;
	}

	public String getHost() {
		return host;
	}

	public long getLength() {
		return length;
	}

}
