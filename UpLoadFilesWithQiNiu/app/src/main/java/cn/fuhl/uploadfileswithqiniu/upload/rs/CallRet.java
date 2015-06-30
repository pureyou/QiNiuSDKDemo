package cn.fuhl.uploadfileswithqiniu.upload.rs;

public class CallRet {
	protected int statusCode;
	protected String response;
	protected String reqId;
	protected Exception exception;

	/**
	 * 子类必须实现此构造函数
	 * 
	 * @param ret
	 */
	public CallRet(CallRet ret) {
		if (ret != null) {
			this.statusCode = ret.statusCode;
			this.reqId = ret.reqId;
			this.response = ret.response;
			this.exception = ret.exception;
			doUnmarshal();
		}
	}

	public CallRet(int statusCode, String reqId, String response) {
		this.statusCode = statusCode;
		this.reqId = reqId;
		this.response = response;
		doUnmarshal();
	}

	public CallRet(int statusCode, String reqId, Exception e) {
		this.statusCode = statusCode;
		this.reqId = reqId;
		this.exception = e;
	}

	public boolean ok() {
		return this.statusCode / 100 == 2 && this.exception == null;
	}

	private void doUnmarshal() {
		if (this.exception != null || this.response == null || !this.response.trim().startsWith("{")) {
			return;
		}
		try {
			unmarshal();
		} catch (Exception e) {
			if (this.exception == null) {
				this.exception = e;
			}
		}

	}

	protected void unmarshal() throws Exception {

	}

	public int getStatusCode() {
		return statusCode;
	}

	public String getReqId() {
		return reqId;
	}

	public String getResponse() {
		return response;
	}

	public Exception getException() {
		return exception;
	}

}
