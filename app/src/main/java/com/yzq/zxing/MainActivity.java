package com.yzq.zxing;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.NotFoundException;
import com.yanzhenjie.permission.Action;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Permission;
import com.yzq.zxinglibrary.android.CaptureActivity;
import com.yzq.zxinglibrary.bean.ZxingConfig;
import com.yzq.zxinglibrary.common.Constant;
import com.yzq.zxinglibrary.encode.CodeCreator;
import com.yzq.zxinglibrary.decode.DecodeImgThread;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Request;
import okio.BufferedSink;
import okio.Utf8;
import pub.devrel.easypermissions.EasyPermissions;

import static com.yanzhenjie.permission.Permission.WRITE_EXTERNAL_STORAGE;


public class MainActivity extends AppCompatActivity implements View.OnClickListener,EasyPermissions.PermissionCallbacks {

    public static final String TAG = "MAINACTIVITY";
    private EditText contentEt;
    private Button encodeBtn;
    private ImageView contentIv;
    private Button fragScanBtn;
    private Button scanBtn;
    private TextView result;
    private Toolbar toolbar;
    private ImageView testBitmat;
    private TextView scanRes;
    private int REQUEST_CODE_SCAN = 111;
    /**
     * 生成带logo的二维码
     */
//    private Button encodeBtnWithLogo;
//    private ImageView contentIvWithLogo;
//    private String contentEtString;
    /**
     * 申请权限
     */
    String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CALL_PHONE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        if (EasyPermissions.hasPermissions(this, perms)) {//检查是否获取该权限
            Log.i(TAG, "已获取权限");
        } else {
            EasyPermissions.requestPermissions(this, "必要的权限", 0, perms);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //把申请权限的回调交由EasyPermissions处理
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    //下面两个方法是实现EasyPermissions的EasyPermissions.PermissionCallbacks接口
    //分别返回授权成功和失败的权限
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        Log.i(TAG, "获取成功的权限" + perms);
    }

    public void onPermissionsDenied(int requestCode, List<String> perms) {
        Log.i(TAG, "获取失败的权限" + perms);
    }

    private void initView() {
        /*扫描按钮*/
        scanBtn = findViewById(R.id.scanBtn);
        scanBtn.setOnClickListener(this);
        /*扫描结果*/
        result = findViewById(R.id.result);
        /*识别结果*/
        scanRes = findViewById(R.id.scanRes);
        /*bitmap预览*/
        testBitmat = findViewById(R.id.text_bitmap);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("钓鱼二维码检测");
        setSupportActionBar(toolbar);
//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        toolbar = (Toolbar) findViewById(R.id.toolbar);
        result = (TextView) findViewById(R.id.result);
        testBitmat = (ImageView) findViewById(R.id.text_bitmap);
        scanBtn = (Button) findViewById(R.id.scanBtn);
        scanRes = (TextView) findViewById(R.id.scanRes);
    }

    @Override
    public void onClick(View v) {
        Bitmap bitmap = null;
        switch (v.getId()) {
            case R.id.scanBtn:
                scanRes.setText("");
                AndPermission.with(this)
                    .permission(Permission.CAMERA, Permission.READ_EXTERNAL_STORAGE)
                    .onGranted(new Action() {
                        @Override
                        public void onAction(List<String> permissions) {
                        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
                        /*ZxingConfig是配置类
                         *可以设置是否显示底部布局，闪光灯，相册，
                         * 是否播放提示音  震动
                         * 设置扫描框颜色等
                         * 也可以不传这个参数
                         * */
                        ZxingConfig config = new ZxingConfig();
                        // config.setPlayBeep(false);//是否播放扫描声音 默认为true
                        //  config.setShake(false);//是否震动  默认为true
                        // config.setDecodeBarCode(false);//是否扫描条形码 默认为true
//                                config.setReactColor(R.color.colorAccent);//设置扫描框四个角的颜色 默认为白色
//                                config.setFrameLineColor(R.color.colorAccent);//设置扫描框边框颜色 默认无色
//                                config.setScanLineColor(R.color.colorAccent);//设置扫描线的颜色 默认白色
                        config.setFullScreenScan(false);//是否全屏扫描  默认为true  设为false则只会在扫描框中扫描
                        intent.putExtra(Constant.INTENT_ZXING_CONFIG, config);
                        startActivityForResult(intent, REQUEST_CODE_SCAN);
                        }
                    })
                    .onDenied(new Action() {
                        @Override
                        public void onAction(List<String> permissions) {
                            Uri packageURI = Uri.parse("package:" + getPackageName());
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageURI);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            startActivity(intent);

                            Toast.makeText(MainActivity.this, "没有权限无法扫描呦", Toast.LENGTH_LONG).show();
                        }
                    }).start();
                break;
        }
    }


    /**
     * 将Bitmap转换为file类型传输
     */
    public File saveFile(Bitmap bm) throws IOException {
        String fileName = "bitmap.png";
        String path = Environment.getExternalStorageDirectory() + "/Ask";
        File dirFile = new File(path);
        if(!dirFile.exists()){
            dirFile.mkdir();
        }
        File myCaptureFile = new File(path + fileName);
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(myCaptureFile));
        bm.compress(Bitmap.CompressFormat.PNG, 100, bos);
        bos.flush();
        bos.close();
        return myCaptureFile;
    }
    /**
     * Bitmap请求体
     * 重写RequestBody
     * 将Bitmap转换为byte[]传输
     */
    class BitmapRequestBody extends RequestBody{
        private Bitmap bitmap;
        public BitmapRequestBody(Bitmap bmp){
            bitmap = bmp;
        }

        @Nullable
        @Override
        public MediaType contentType() {
            return MediaType.parse("image/png");
        }

        @Override
        public void writeTo(@NotNull BufferedSink sink) throws IOException {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, sink.outputStream());
        }
    }

    /**
     *
     * @param bitmap
     * @param orientationDegree 0 - 360 范围
     * @return
     */
    Bitmap adjustPhotoRotation(Bitmap bitmap, int orientationDegree) {

        Matrix matrix = new Matrix();
        matrix.setRotate(orientationDegree, (float) bitmap.getWidth() / 2,
                (float) bitmap.getHeight() / 2);
        float targetX, targetY;
        if (orientationDegree == 90) {
            targetX = bitmap.getHeight();
            targetY = 0;
        } else {
            targetX = bitmap.getHeight();
            targetY = bitmap.getWidth();
        }


        final float[] values = new float[9];
        matrix.getValues(values);


        float x1 = values[Matrix.MTRANS_X];
        float y1 = values[Matrix.MTRANS_Y];


        matrix.postTranslate(targetX - x1, targetY - y1);


        Bitmap canvasBitmap = Bitmap.createBitmap(bitmap.getHeight(), bitmap.getWidth(),
                Bitmap.Config.ARGB_8888);


        Paint paint = new Paint();
        Canvas canvas = new Canvas(canvasBitmap);
        canvas.drawBitmap(bitmap, matrix, paint);

        return canvasBitmap;
    }

    /**
     *传输数据
     */
    private void sendBitmap(final Bitmap bitmap, String content){
        final Bitmap bmp = bitmap;
        final String result = content;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(60, TimeUnit.SECONDS)//设置连接超时时间
                            .readTimeout(60, TimeUnit.SECONDS)//设置读取超时时间
                            .build();
                    File file = saveFile(bitmap);
                    String filename = file.getName();
                    System.out.println(filename+"长度"+file.length());
                    RequestBody requestBody = RequestBody.create(MediaType.parse("application/octet-stream"),file);
                    MultipartBody multipartBody = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("url",result)//bitmap解析结果
                            .addFormDataPart("file",filename, requestBody)//请求体，有bitmap数据
                            .build();
                    Request request = new Request.Builder()
                            .url("http://192.168.43.128:8000")
                            .post(multipartBody)
                            .build();
                    Response response = client.newCall(request).execute();
                    String responseDate = response.body().string();
                    System.out.println("服务端回复内容："+ responseDate);
                    scanRes.setText("钓鱼检测结果为：" + responseDate);
                }catch (Exception e){
                    e.printStackTrace();
                    scanRes.setText("服务器响应超时");
                    Log.i("SendBitmapERROR:",e.getMessage());
                }
            }
        }).start();
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 扫描二维码/条码回传
        if (requestCode == REQUEST_CODE_SCAN && resultCode == RESULT_OK) {
            if (data != null) {
                String content = data.getStringExtra(Constant.CODED_CONTENT);
                Log.i("Mainactivity扫描结果：", content);
                result.setText("扫描结果为：" + content);

                // 获取scan_bitmap
                Bitmap bitmap = null;
                Bundle bundle = data.getExtras();
                if (bundle != null) {
                    Log.i("扫描信息","开始");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        CaptureActivity.ImageBinder imageBinder = (CaptureActivity.ImageBinder) bundle.getBinder("bitmap");
                        if (imageBinder != null){
                            Log.i("扫描信息","成功");
                            bitmap = imageBinder.getBitmap();
                            if(bitmap!=null){
                                bitmap = adjustPhotoRotation(bitmap,90);
                                sendBitmap(bitmap,content);
                                testBitmat.setImageBitmap(bitmap);
                            }else{
                                Log.i("SCAN_BITMAP:","bitmap为空");
                            }
                        }
                        else {
                            Log.i("扫描信息","失败，无扫描数据");
                        }
                    }
                }

                //获取相册bitmap
                String path = data.getStringExtra(Constant.IMG_PATH);
                if(path != null){
                    Log.i("Mainactivity里imgpath：", path);
                    Bitmap imgbitmap = DecodeImgThread.getBitmap(path,400,400);
                    try {
                        imgbitmap = CaptureActivity.get_qrcode(imgbitmap);
                    } catch (NotFoundException e) {
                        e.printStackTrace();
                    }
                    if (imgbitmap != null){
                        testBitmat.setImageBitmap(imgbitmap);
                        sendBitmap(imgbitmap,content);
                    }
                }
            }
        }
    }
}
