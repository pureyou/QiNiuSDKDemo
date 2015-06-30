package cn.fuhl.uploadfileswithqiniu.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import cn.fuhl.uploadfileswithqiniu.R;
import cn.fuhl.uploadfileswithqiniu.upload.UpApi;
import cn.fuhl.uploadfileswithqiniu.upload.UpParam;
import cn.fuhl.uploadfileswithqiniu.upload.Upload;
import cn.fuhl.uploadfileswithqiniu.upload.UploadHandler;
import cn.fuhl.uploadfileswithqiniu.upload.auth.Authorizer;
import cn.fuhl.uploadfileswithqiniu.upload.config.QiNiuConfig;
import cn.fuhl.uploadfileswithqiniu.upload.rs.PutExtra;
import cn.fuhl.uploadfileswithqiniu.upload.rs.UploadResultCallRet;
import cn.fuhl.uploadfileswithqiniu.upload.slice.Block;
import cn.fuhl.uploadfileswithqiniu.upload.utils.Util;

public class BlockUploadActivity extends Activity implements View.OnClickListener{

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.block_upload_activity);
		initBuildToken();
		initView();
        initData();
	}

    private TextView title;
    private TextView textViewCurrent;
    private Button btnUpload ;
    private Button btnCancel  ;
    private Button btnDoupload  ;

    private void initView() {
		title = (TextView) findViewById(R.id.textView);
		textViewCurrent = (TextView) findViewById(R.id.textView1);
		btnUpload = (Button) findViewById(R.id.btn_choose_file);
        btnCancel = (Button) findViewById(R.id.btn_cancel);
		btnDoupload = (Button) findViewById(R.id.btn_doupload);
	}

    private void initData() {
        btnUpload.setOnClickListener(this);
        btnDoupload.setOnClickListener(this);
        btnCancel.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_choose_file:
                ChooseFile();
                break;
            case R.id.btn_cancel:
                cancel();
                break;
            case R.id.btn_doupload:
                doUpload();
                break;
            default:
                break;
        }
    }

    public static final int FILE_SELECT_CODE = 0;
    private void ChooseFile(){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try{
            startActivityForResult(Intent.createChooser(intent,"请选择一个要上传的文件"),FILE_SELECT_CODE);
        }catch (android.content.ActivityNotFoundException ex){
            Toast.makeText(getApplicationContext(), "请安装文件管理器", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK)
            return;
        if (requestCode == FILE_SELECT_CODE) {
            preUpload(data.getData());
            return;
        }
    }

    /**
     * 文件上传前通过获取到的uri构建upload对象
     *
     * @param uri   待上传文件的uri
     */
    private void preUpload(Uri uri) {
        String passObject = "test: " + uri.getEncodedPath() + "passObject";
        String qiniuKey = UUID.randomUUID().toString();
        PutExtra extra = null;
        Upload upLoad = UpApi.build(getAuthorizer(), qiniuKey, uri, this, extra, passObject, uploadHandler);
        addUp(upLoad);
    }

    private List<Upload> ups = new LinkedList<Upload>();

    /**
     * 将选择的文件添加到链表中
     * @param upLoad    待添加的upload对象
     */
    private synchronized void addUp(Upload upLoad) {
        if (!contains(upLoad)) {
            ups.add(upLoad);
            showUps();
        }
    }

    /**
     * 去除重复文件
     *
     * @param up
     * @return
     */
    private synchronized boolean contains(Upload up) {
        for (Upload u : ups) {
            if (up.getPassParam() != null && up.getPassParam().equals(u.getPassParam())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 显示出选择的文件名称
     *
     */
    private void showUps() {
        StringBuilder sb = new StringBuilder();
        for (Upload up : ups) {
            sb.append(up.getUpParam().getName()).append(", ");
        }
        title.setText(sb);
    }

    /**
     * 开始上传工作
     *
     */
    private List<UpApi.Executor> executors = new ArrayList<UpApi.Executor>();
    long start = 0;
    private synchronized void doUpload() {
        for (Upload up : ups) {
            if (UpApi.isSliceUpload(up)) {
                String sourceId = generateSourceId(up.getUpParam(), up.getPassParam());
                List<Block> bls = null;
                try {
                    bls = load(sourceId);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // 设置以前上传的断点记录。 直传会忽略此参数
                up.setLastUploadBlocks(bls);
            }
            UpApi.Executor executor = UpApi.execute(up);//执行上传
            executors.add(executor);
        }
        start = System.currentTimeMillis();
    }

	private static Authorizer authorizer = new Authorizer();
	private static Date buildTokenDate;
	private static ScheduledExecutorService replenishTimer = Executors.newScheduledThreadPool(1, new UpApi.DaemonThreadFactory());
	private static ReadWriteLock rw = new ReentrantReadWriteLock();
	private void initBuildToken() {
		replenishTimer.scheduleAtFixedRate(new Runnable() {
			private long gap = 1000 * 60 * 40; // 40分钟
			public void run() {
				if (getBuildTokenDate() == null || (new Date().getTime() - getBuildTokenDate().getTime() > gap)) {
					buildToken();
				}
			}

		}, 0, 10, TimeUnit.MINUTES);

		authorizer.setUploadToken(QiNiuConfig.token);
		buildTokenDate = new Date();
	}

	private Random r = new Random();
	private void buildToken() {
		try {
			rw.writeLock().lock();
			if (r.nextBoolean()) {// 模拟
				throw new RuntimeException("  获取token失败。  ");
			}
			authorizer.setUploadToken(QiNiuConfig.token);
			buildTokenDate = new Date();
		} catch (Exception e) {

		} finally {
			rw.writeLock().unlock();
		}
	}

	private Date getBuildTokenDate() {
		try {
			rw.readLock().lock();
			return buildTokenDate;
		} finally {
			rw.readLock().unlock();
		}
	}

	public Authorizer getAuthorizer() {
		try {
			rw.readLock().lock();
			return authorizer;
		} finally {
			rw.readLock().unlock();
		}
	}


	private UploadHandler uploadHandler = new UploadHandler() {
		@Override
		protected void onProcess(long contentLength, long currentUploadLength, long lastUploadLength, UpParam p, Object passParam) {
			long now = System.currentTimeMillis();
			long time = (now - start) / 1000;
			long v = currentUploadLength / 1000 / (time + 1);
			String o = textViewCurrent.getText() == null ? "" : textViewCurrent.getText().toString();
			String m = passParam + "  : " + p.getName();
			String txt = o + "\n1" + m + "\n共: " + contentLength / 1024 + "KB, 历史已上传: " + lastUploadLength / 1024 + "KB, 本次已上传: "
					+ currentUploadLength / 1024 + "KB, 耗时: " + time + "秒, 速度: " + v + "KB/s";
			txt = txt.substring(Math.max(0, txt.length() - 1300));
			textViewCurrent.setText(txt);
		}

		@Override
		protected void onSuccess(UploadResultCallRet ret, UpParam p, Object passParam) {

			Toast.makeText(BlockUploadActivity.this, "上传成功!", Toast.LENGTH_LONG).show();
			textViewCurrent.setText("");
			textViewCurrent.setText("\n" + ret.getStatusCode() + " " + ret.getResponse());
			try {
				String sourceId = generateSourceId(p, passParam);
				clean(sourceId);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		protected void onFailure(UploadResultCallRet ret, UpParam p, Object passParam) {
			String o = textViewCurrent.getText() == null ? "" : textViewCurrent.getText().toString();
			textViewCurrent.setText(o + "\n" + ret.getStatusCode() + " " + ret.getResponse() + ", X-Reqid: " + ret.getReqId()
					+ (ret.getException() == null ? "" : " e:" + ret.getException() + " -- " + ret.getException().getMessage()));
			if (ret.getException() != null) {
				ret.getException().printStackTrace();
			}
		}

		@Override
		protected void onBlockSuccess(List<Block> uploadedBlocks, Block block, UpParam p, Object passParam) {
			try {
				String sourceId = generateSourceId(p, passParam);
				addBlock(sourceId, block);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	};



	// 取消上传 **************************
	private void cancel() {
		try {
			for (UpApi.Executor executor : executors) {
				executor.cancel();
			}
			showUps();
		} catch (Exception e) {
			e.printStackTrace();
		}
		cancel0();
	}

	// 上传成功、失败、取消后等，也应将对应的UpLoad、Executor 取消，避免一直被引用，不能回收
	private synchronized void cancel0() {
		try {
			for (int l = ups.size(); l > 0; l--) {
				ups.remove(ups.get(0));
			}
			showUps();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	// 断点记录 记录到文件示例 ******************************
	public static String RESUME_DIR;
	private File getDir() throws IOException {
		String dir = RESUME_DIR;
		String qinuDir = ".qiniu_up";
		if (dir == null) {
			// dir = System.getProperties().getProperty("user.home");
			File exdir = Environment.getExternalStorageDirectory();
			dir = exdir.getCanonicalPath();
			return new File(exdir, qinuDir);
		} else {
			return new File(dir, qinuDir);
		}
	}

	private File initFile(File dir, String sourceId) throws IOException {
		dir.mkdirs();
		File file = new File(dir, sourceId);
		if (!file.exists()) {
			file.createNewFile();
		}
		return file;
	}

	private String generateSourceId(UpParam p, Object passParam) {
		String s = p.getName() + "-" + p.getSize() + "-" + passParam;
		String ss = Util.urlsafeBase64(s);
		return ss;
	}

	private List<Block> load(String sourceId) throws IOException {
		File file = new File(getDir(), sourceId);
		if (!file.exists()) {
			return null;
		}
		List<Block> bls = null;
		FileReader freader = null;
		BufferedReader reader = null;
		try {
			bls = new ArrayList<Block>();
			freader = new FileReader(file);
			reader = new BufferedReader(freader);
			String line = null;
			while ((line = reader.readLine()) != null) {
				Block b = analyLine(line);
				if (b != null) {
					bls.add(b);
				}
			}
			Collections.reverse(bls);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (freader != null) {
				try {
					freader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return bls;
	}

	private void addBlock(String sourceId, Block block) throws IOException {
		File file = initFile(getDir(), sourceId);
		String l = sync(block);
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(file, true));
			writer.newLine();
			writer.write(l);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (Exception e) {
				}
			}
		}
	}

	private void clean(String sourceId) throws IOException {
		File file = new File(getDir(), sourceId);
		file.delete();
	}

	private Block analyLine(String line) {
		String[] s = line.split(",");
		if (s.length >= 4) {
			int idx = Integer.parseInt(s[0]);
			String ctx = s[1];
			long length = Long.parseLong(s[2]);
			String host = s[3];
			Block block = new Block(idx, ctx, length, host);
			return block;
		} else {
			return null;
		}
	}

	private String sync(Block b) {
		return b.getIdx() + "," + b.getCtx() + "," + b.getLength() + "," + b.getHost();
	}
}
