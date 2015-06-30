package cn.fuhl.uploadfileswithqiniu.activity;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import cn.fuhl.uploadfileswithqiniu.R;
import cn.fuhl.uploadfileswithqiniu.base.BaseApp;
import cn.fuhl.uploadfileswithqiniu.upload.utils.QiniuUploadUitls;
import roboguice.activity.RoboActivity;

public class MainActivity extends RoboActivity implements OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        initView();
    }

    Button btn_blockUpload;
    ProgressBar pbProgress;
    Button btnChoose;
    ImageView ivShowimage;
    Button btn_commonUpload;
    TextView tvUri;
    private void initView() {
        btn_blockUpload = (Button)findViewById(R.id.btn_blockUpload);
        pbProgress = (ProgressBar)findViewById(R.id.pb_progress);
        btnChoose = (Button)findViewById(R.id.btn_choose_upload);
        ivShowimage = (ImageView)findViewById(R.id.iv_showimage);
        btn_commonUpload = (Button)findViewById(R.id.btn_Upload);
        tvUri = (TextView) findViewById(R.id.tv_url);
        btnChoose.setOnClickListener(this);
        btn_blockUpload.setOnClickListener(this);
        btn_commonUpload.setOnClickListener(this);
    }

    private final static int CHOOSE_IMAGE_CODE = 1;

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_blockUpload :
                startActivity(new Intent(MainActivity.this, BlockUploadActivity.class));
                break;
            case R.id.btn_Upload :
                if(uri != null){
                    SimpleUpLoad();
                }
                break;
            case R.id.btn_choose_upload :
                startActivityForResult(new Intent().setType("image/*").setAction(Intent.ACTION_GET_CONTENT), CHOOSE_IMAGE_CODE);
                break;
            default :
                break;
        }
    }

    Uri uri;
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == CHOOSE_IMAGE_CODE) {
             uri = data.getData();//获取到文件所在路径
            tvUri.setText(uri.toString());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void SimpleUpLoad(){
        ContentResolver cr = this.getContentResolver();
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(cr.openInputStream(uri));//打开文件流
            uploadBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 上传从相册选择的图片
     *
     * @param result
     */
    private void uploadBitmap(Bitmap result) {

        QiniuUploadUitls.getInstance().uploadImage(result, new QiniuUploadUitls.QiniuUploadUitlsListener() {

            public void onSucess(String fileUrl) {
                showToast("上传图片成功");
                showUrlImage(fileUrl);
            }

            public void onProgress(int progress) {

                pbProgress.setProgress(progress);
            }

            public void onError(int errorCode, String msg) {
                showToast("errorCode=" + errorCode + ",msg=" + msg);
            }
        });
    }

    protected void showUrlImage(final String fileUrl) {
        Picasso.with(this).load(fileUrl).error(R.drawable.ic_launcher).into(ivShowimage, new Callback() {

            public void onSuccess() {
                showToast("加载图片成功");
            }

            public void onError() {
                showToast("图片链接不正确不能加载图片，请检查是否为私密空间"+"---"+fileUrl);
            }
        });
    }
    private static void showToast(String content) {
        Toast.makeText(BaseApp.getApplication(), content, Toast.LENGTH_LONG).show();
    }
}